"""Convert only the proposed local Excel template to a JSON staging request."""

import argparse
import json
import pathlib
import re
import xml.etree.ElementTree as element_tree
from zipfile import ZipFile

from build_vroad_inbound_template import HEADERS


XML_NAMESPACE = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def read_rows(path):
    with ZipFile(path) as workbook:
        name = "xl/worksheets/sheet1.xml"
        sheet = workbook.getinfo(name)
        if sheet.file_size > 2_000_000 or sum(item.file_size for item in workbook.infolist()) > 5_000_000:
            raise ValueError("Excel template is too large")
        root = element_tree.fromstring(workbook.read(name))
    rows = []
    for row in root.findall(f".//{XML_NAMESPACE}row"):
        if len(rows) > 100:
            raise ValueError("No more than 100 proposed records per batch")
        values = [""] * len(HEADERS)
        for cell in row.findall(f"{XML_NAMESPACE}c"):
            position = re.match(r"([A-Z]+)", cell.attrib.get("r", ""))
            if position is None or len(position.group(1)) != 1:
                raise ValueError("Only the proposed template's first ten columns are supported")
            column = ord(position.group(1)) - 65
            if column < 0 or column >= len(HEADERS):
                raise ValueError("Unexpected column in proposed template")
            if cell.attrib.get("t") != "inlineStr":
                raise ValueError("Use the provided template to keep source codes as text")
            values[column] = "".join(node.text or "" for node in cell.findall(f".//{XML_NAMESPACE}t"))
        rows.append(values)
    if not rows or tuple(rows[0]) != HEADERS:
        raise ValueError("Not the VroadAI proposed template")
    if len(rows) > 101:
        raise ValueError("No more than 100 proposed records per batch")
    return rows[1:]


def convert(path, request_key):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{5,119}", request_key):
        raise ValueError("Invalid staging request key")
    records = []
    for values in read_rows(path):
        record = {header: value.strip() for header, value in zip(HEADERS, values) if value.strip()}
        if not record:
            continue
        for field in ("longitude", "latitude", "confidence"):
            if field in record:
                try:
                    record[field] = float(record[field])
                except ValueError:
                    pass
        records.append(record)
    if not records:
        raise ValueError("No records in the template")
    return {"requestKey": request_key, "schemaVersion": "demo-proposal-v1", "records": records}


def main():
    parser = argparse.ArgumentParser(description="Convert only the proposal XLSX to staging JSON; no API calls")
    parser.add_argument("workbook", type=pathlib.Path)
    parser.add_argument("--request-key", required=True)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    request = convert(args.workbook, args.request_key)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(request, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Converted {len(request['records'])} proposed records; no data sent to any server")


if __name__ == "__main__":
    main()
