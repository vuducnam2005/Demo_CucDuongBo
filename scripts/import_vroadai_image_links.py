"""Import hyperlink targets from the canonical XLSX reports without fetching private images."""

import csv
import hashlib
import io
import pathlib
import re
import subprocess
import sys
import zipfile
from urllib.parse import urlsplit
from xml.etree import ElementTree


REPORTS = (
    ("Asset-Lib-All-Report-20261006-150704.xlsx", "vroad_assets", "L"),
    ("Defect-Lib-All-Report-20261006-150716.xlsx", "vroad_defects", "K"),
)


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def image_links(path, column):
    with zipfile.ZipFile(path) as book:
        if sum(item.file_size for item in book.infolist()) > 180 * 1024 * 1024:
            raise ValueError("Workbook decompressed size exceeds 180 MiB")
        strings = []
        if "xl/sharedStrings.xml" in book.namelist():
            strings = ["".join(item.itertext()) for item in ElementTree.fromstring(
                book.read("xl/sharedStrings.xml")).findall("{*}si")]
        relationships = ElementTree.fromstring(book.read("xl/worksheets/_rels/sheet1.xml.rels"))
        targets = {}
        for item in relationships:
            if not item.attrib.get("Type", "").endswith("/hyperlink"):
                continue
            url = item.attrib.get("Target", "")
            parts = urlsplit(url)
            if (parts.scheme, parts.hostname, parts.port, parts.username) != (
                    "https", "platform.vroad.vn", None, None):
                raise ValueError("Unexpected image host, scheme, port, or userinfo")
            targets[item.attrib["Id"]] = url
        worksheet = ElementTree.fromstring(book.read("xl/worksheets/sheet1.xml"))
        by_row = {}
        for item in worksheet.findall(".//{*}hyperlink"):
            coordinate = re.fullmatch(rf"{column}([0-9]+)", item.attrib.get("ref", ""))
            relationship = next((value for key, value in item.attrib.items() if key.endswith("}id")), None)
            if not coordinate or relationship not in targets:
                raise ValueError("Unexpected image column or hyperlink relationship")
            row_number = int(coordinate.group(1))
            if row_number < 5 or row_number in by_row:
                raise ValueError("Duplicate or invalid image row")
            by_row[row_number] = targets[relationship]
        records = []
        for row in worksheet.findall(".//{*}sheetData/{*}row"):
            row_number = int(row.attrib["r"])
            if row_number < 5:
                continue
            identity = row.find(f"{{*}}c[@r='A{row_number}']")
            if identity is None:
                raise ValueError("Record missing source identifier")
            value = identity.find("{*}v")
            inline = identity.find("{*}is")
            if value is not None and identity.attrib.get("t") == "s":
                record_key = strings[int(value.text)]
            else:
                record_key = value.text if value is not None else "".join(inline.itertext()) if inline is not None else ""
            record_key = (record_key or "").strip()
            if not record_key or row_number not in by_row:
                raise ValueError("Record missing identifier or image hyperlink")
            records.append((record_key, by_row[row_number]))
        if len(records) != len(by_row) or len(records) != len({key for key, _ in records}):
            raise ValueError("Image count or source identifiers do not reconcile")
        return records


def import_report(directory, report, dataset_key, image_column):
    path = directory / report
    if not path.is_file():
        raise FileNotFoundError(report)
    records = image_links(path, image_column)
    source_hash = hashlib.sha256(path.read_bytes()).hexdigest()
    stream = io.StringIO(newline="")
    writer = csv.writer(stream, lineterminator="\n")
    writer.writerows(records)
    sql = f"""\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE image_stage (record_key VARCHAR(150) PRIMARY KEY, source_url TEXT NOT NULL) ON COMMIT DROP;
COPY image_stage(record_key, source_url) FROM STDIN WITH (FORMAT csv);
{stream.getvalue()}\\.
DO $validation$
BEGIN
 IF (SELECT COUNT(*) FROM image_stage) <> (SELECT COUNT(*) FROM raw_dataset_record WHERE dataset_key = {quote(dataset_key)})
    OR EXISTS (SELECT 1 FROM image_stage s LEFT JOIN raw_dataset_record r
        ON r.dataset_key = {quote(dataset_key)} AND r.record_key = s.record_key WHERE r.id IS NULL)
    OR EXISTS (SELECT 1 FROM vroad_image_reference existing JOIN image_stage s
        ON existing.dataset_key = {quote(dataset_key)} AND existing.record_key = s.record_key
        WHERE existing.source_url <> s.source_url)
 THEN RAISE EXCEPTION 'Image identifiers or existing links differ from the canonical dataset';
 END IF;
END $validation$;
INSERT INTO vroad_image_reference(dataset_key, record_key, source_url, source_report, workbook_sha256)
SELECT {quote(dataset_key)}, record_key, source_url, {quote(report)}, {quote(source_hash)} FROM image_stage
ON CONFLICT (dataset_key, record_key) DO NOTHING;
COMMIT;
SELECT COUNT(*) FROM vroad_image_reference WHERE dataset_key = {quote(dataset_key)};
"""
    result = subprocess.run(
        ["docker", "exec", "-i", "kcht_postgres", "psql", "-X", "-qAt", "-v", "ON_ERROR_STOP=1",
         "-U", "kcht_user", "-d", "kcht_db"],
        input=sql, text=True, encoding="utf-8", capture_output=True, check=False,
    )
    if result.returncode:
        raise RuntimeError("Image import failed; check migration and dataset keys. " + result.stderr[-300:])
    count = int(result.stdout.strip().splitlines()[-1])
    if count != len(records):
        raise RuntimeError("Image count does not match workbook")
    print(f"{dataset_key}: {count} image references (source links only; no image files downloaded)")


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python -X utf8 scripts/import_vroadai_image_links.py WORKBOOK_DIRECTORY")
    directory = pathlib.Path(sys.argv[1]).resolve()
    for report, dataset_key, image_column in REPORTS:
        import_report(directory, report, dataset_key, image_column)


if __name__ == "__main__":
    main()
