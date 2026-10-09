"""Optional, clearly labelled synthetic records for local region workflow QA."""

import hashlib
import json
import subprocess
import sys


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    source_hash = hashlib.sha256(b"LOCAL-DEMO-REGION-ASSETS-V1").hexdigest()
    records = (
        ("DEMO-REGION-001", "Biển minh họa — chưa gắn tuyến thực"),
        ("DEMO-REGION-002", "Hạng mục minh họa — không có tọa độ"),
    )
    statements = ["\\set ON_ERROR_STOP on", "BEGIN;",
        "INSERT INTO dataset_registry(dataset_key, dataset_name, kind, source_file, total_records) "
        "VALUES ('demo_region_assets', 'Tài sản MÔ PHỎNG đơn vị', 'auxiliary', 'demo_fixture', 0) "
        "ON CONFLICT (dataset_key) DO NOTHING;"]
    for record_key, display_name in records:
        payload = json.dumps({"source_key": record_key, "fielddisplay": display_name,
                              "route_name": "Tuyến giả lập — không định vị", "branch_id": "kqldb_1",
                              "is_demo": True}, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        payload_hash = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        statements.append("INSERT INTO raw_dataset_record(dataset_key, record_key, raw_payload, source_file, "
            "source_sha256, payload_sha256, record_status) VALUES ("
            f"'demo_region_assets', {quote(record_key)}, {quote(payload)}::jsonb, 'demo_fixture', "
            f"{quote(source_hash)}, {quote(payload_hash)}, 'RAW_STORED') "
            "ON CONFLICT (dataset_key, record_key) DO NOTHING;")
    statements.extend(["UPDATE dataset_registry SET total_records = (SELECT COUNT(*) FROM raw_dataset_record "
        "WHERE dataset_key = 'demo_region_assets') WHERE dataset_key = 'demo_region_assets';",
        "COMMIT;", "SELECT total_records FROM dataset_registry WHERE dataset_key = 'demo_region_assets';"])
    result = subprocess.run(["docker", "exec", "-i", "kcht_postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1",
                             "-U", "kcht_user", "-d", "kcht_db", "-qAt"],
                            input="\n".join(statements) + "\n", text=True, encoding="utf-8", capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr[-1000:])
    print("Bản ghi MÔ PHỎNG cho kqldb_1:", result.stdout.strip().splitlines()[-1])


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
