"""Import previously reconciled VroadAI JSONL into the existing raw staging tables.

The two defect route reports are cross-checks, not additional observations.
This script never uploads to the reference website or assigns a region by name.
"""

import argparse
import csv
import hashlib
import io
import json
import pathlib
import subprocess
import sys


DATASETS = (
    ("assets.jsonl", "vroad_assets", "Tài sản VroadAI (nguồn)", "asset", "Asset-Lib-All-Report-20261006-150704.xlsx"),
    ("defects.jsonl", "vroad_defects", "Hư hỏng VroadAI (nguồn)", "module", "Defect-Lib-All-Report-20261006-150716.xlsx"),
    ("iri.jsonl", "vroad_iri", "Quan trắc IRI VroadAI (nguồn)", "module", "Road_Roughness_IRI_AllRoads.xlsx"),
)


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def prepare(file_path, workbook):
    content = file_path.read_bytes()
    source_hash = hashlib.sha256(content).hexdigest()
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, lineterminator="\n")
    seen = set()
    for line_number, line in enumerate(content.decode("utf-8-sig").splitlines(), 1):
        if not line.strip():
            continue
        record = json.loads(line)
        record_key = str(record.get("source_key", "")).strip()
        if not record_key or record_key in seen:
            raise ValueError(f"Missing/duplicate source_key in {file_path.name} at line {line_number}")
        seen.add(record_key)
        record["source_report"] = workbook
        record["is_demo"] = False
        payload = json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        writer.writerow((record_key, payload, hashlib.sha256(payload.encode("utf-8")).hexdigest()))
    return source_hash, len(seen), stream.getvalue()


def import_dataset(container, file_path, dataset_key, title, kind, workbook):
    source_hash, expected, rows = prepare(file_path, workbook)
    script = f"""\\set ON_ERROR_STOP on
BEGIN;
INSERT INTO dataset_registry(dataset_key, dataset_name, kind, source_file, total_records)
VALUES ({quote(dataset_key)}, {quote(title)}, {quote(kind)}, {quote(file_path.name)}, 0)
ON CONFLICT (dataset_key) DO NOTHING;
CREATE TEMP TABLE import_stage (record_key varchar(150) PRIMARY KEY, payload jsonb NOT NULL,
                                payload_sha256 char(64) NOT NULL) ON COMMIT DROP;
COPY import_stage(record_key, payload, payload_sha256) FROM STDIN WITH (FORMAT csv);
{rows}\\.
DO $check$
BEGIN
    IF EXISTS (SELECT 1 FROM import_stage s JOIN raw_dataset_record r
               ON r.dataset_key = {quote(dataset_key)} AND r.record_key = s.record_key
               WHERE r.payload_sha256 <> s.payload_sha256) THEN
        RAISE EXCEPTION 'Existing source key has different payload; review conflict before import';
    END IF;
END $check$;
INSERT INTO raw_dataset_record (dataset_key, record_key, raw_payload, source_file,
                                source_sha256, payload_sha256, record_status)
SELECT {quote(dataset_key)}, record_key, payload, {quote(file_path.name)},
       {quote(source_hash)}, payload_sha256, 'RAW_STORED' FROM import_stage
ON CONFLICT (dataset_key, record_key) DO NOTHING;
UPDATE dataset_registry SET total_records = (SELECT COUNT(*) FROM raw_dataset_record
      WHERE dataset_key = {quote(dataset_key)}), updated_at = CURRENT_TIMESTAMP
WHERE dataset_key = {quote(dataset_key)};
COMMIT;
SELECT dataset_key, total_records FROM dataset_registry WHERE dataset_key = {quote(dataset_key)};
"""
    result = subprocess.run(
        ["docker", "exec", "-i", container, "psql", "-X", "-v", "ON_ERROR_STOP=1",
         "-U", "kcht_user", "-d", "kcht_db", "-qAt"],
        input=script, text=True, encoding="utf-8", capture_output=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(f"Import failed for {dataset_key}: {result.stderr[-1000:]}")
    print(f"{dataset_key}: {expected} source keys; database count {result.stdout.strip().splitlines()[-1]}")


def main():
    parser = argparse.ArgumentParser(description="Import reconciled files into local demo PostgreSQL")
    parser.add_argument("data_dir", type=pathlib.Path, help="Directory containing assets.jsonl, defects.jsonl and iri.jsonl")
    parser.add_argument("--container", default="kcht_postgres")
    options = parser.parse_args()
    for filename, dataset_key, title, kind, workbook in DATASETS:
        file_path = options.data_dir / filename
        if not file_path.is_file():
            raise FileNotFoundError(file_path)
        import_dataset(options.container, file_path, dataset_key, title, kind, workbook)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, FileNotFoundError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
