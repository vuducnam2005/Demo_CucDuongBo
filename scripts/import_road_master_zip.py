"""Import only the two road master datasets from a partial, user-provided ZIP.

No archive entries are extracted. Other reference tables are not road assets.
"""

import argparse
import json
import pathlib
import re
import sys
import tempfile
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from import_vroadai_normalized import import_dataset


DATASETS = (
    ("mst_national_road", "Danh mục quốc lộ (nguồn chưa đầy đủ)"),
    ("mst_national_expressway", "Danh mục cao tốc (nguồn chưa đầy đủ)"),
)
ARCHIVE_PREFIX = "kcht_2026-10-05/"


def load_json(archive, name):
    return json.loads(archive.read(ARCHIVE_PREFIX + name).decode("utf-8-sig"))


def prepare_dataset(archive, dataset):
    metadata = load_json(archive, dataset + "_manifest.json")
    details = metadata.get("main", {})
    records = load_json(archive, dataset + ".json")
    if metadata.get("dataset") != dataset or not isinstance(records, list) or not records:
        raise ValueError("Invalid road master dataset or metadata: " + dataset)
    if len(records) != details.get("records") or metadata.get("complete_website_crawl") is not False:
        raise ValueError("Archive coverage/count mismatch: " + dataset)
    fields = details.get("fields", [])
    if not isinstance(fields, list) or "vidagis_id" not in fields:
        raise ValueError("Road identifier missing from source metadata: " + dataset)
    workbook = details.get("source_workbook")
    if not isinstance(workbook, str) or not workbook:
        raise ValueError("Source workbook missing: " + dataset)
    seen = set()
    prepared = []
    for record in records:
        if not isinstance(record, dict):
            raise ValueError("Non-object road record: " + dataset)
        source_key = record.get("vidagis_id")
        if not isinstance(source_key, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,150}", source_key):
            raise ValueError("Invalid road source identifier: " + dataset)
        if source_key in seen:
            raise ValueError("Duplicate road source identifier: " + dataset)
        seen.add(source_key)
        selected = {field: record[field] for field in fields if field in record}
        selected.update(source_key=source_key, source_report=workbook, is_demo=False,
                        source_status="partial_awaiting_authenticated_api_request")
        prepared.append(json.dumps(selected, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return workbook, prepared


def main():
    parser = argparse.ArgumentParser(description="Import road master records from the partial local ZIP")
    parser.add_argument("archive", type=pathlib.Path)
    parser.add_argument("--container", default="kcht_postgres")
    arguments = parser.parse_args()
    if not arguments.archive.is_file():
        raise FileNotFoundError(arguments.archive)
    with zipfile.ZipFile(arguments.archive) as archive:
        if sum(item.file_size for item in archive.infolist()) > 30 * 1024 * 1024:
            raise ValueError("Archive expands beyond 30 MiB")
        manifest = load_json(archive, "manifest.json")
        if manifest.get("complete_website_crawl") is not False or not str(manifest.get("source", "")).startswith(
                "https://kcht.drvn.gov.vn/"):
            raise ValueError("Unexpected source or unsupported archive status")
        prepared = [(dataset, title, *prepare_dataset(archive, dataset)) for dataset, title in DATASETS]
    with tempfile.TemporaryDirectory(prefix="kcht-roads-") as directory:
        for dataset, title, workbook, records in prepared:
            file_path = pathlib.Path(directory) / (dataset + ".jsonl")
            file_path.write_text("\n".join(records) + "\n", encoding="utf-8")
            import_dataset(arguments.container, file_path, dataset, title, "module", workbook)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, FileNotFoundError, KeyError, RuntimeError, zipfile.BadZipFile) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
