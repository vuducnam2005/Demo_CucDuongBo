import re
import json
import subprocess
import os

log_file_path = r"C:\Users\vuduc\.gemini\antigravity\brain\06fbc4d1-4890-42cf-b79c-95cf98d88437\.system_generated\tasks\task-798.log"
manifest_path = r"C:\Data\kcht_json_2026-10-05\manifest.json"

print("1. Loading manifest...")
with open(manifest_path, "r", encoding="utf-8") as f:
    manifest_data = json.load(f)

manifest_map = {}
for item in manifest_data.get("files", []):
    path = item.get("path")
    manifest_map[path] = item

print(f"Manifest loaded: {len(manifest_map)} files")

print("2. Parsing task-798.log...")
# Pattern: File: assets/... | Read: 169 | Ins: 0 | Upd: 0 | Skip: 169 | Fail: 0 | UniqueKeys: 169 | Geoms: 169 | Time: 42ms (4023.8 rec/s) | RAM: 184MB
file_audit_pattern = re.compile(
    r"File:\s+([^\s|]+)\s+\|\s+Read:\s+(\d+)\s+\|\s+Ins:\s+(\d+)\s+\|\s+Upd:\s+(\d+)\s+\|\s+Skip:\s+(\d+)\s+\|\s+Fail:\s+(\d+)\s+\|\s+UniqueKeys:\s+(\d+)\s+\|\s+Geoms:\s+(\d+)\s+\|\s+Time:\s+(\d+)ms\s+\(([\d.]+)\s+rec/s\)\s+\|\s+RAM:\s+(\d+)MB"
)

audits = {}
with open(log_file_path, "r", encoding="utf-8", errors="ignore") as f:
    for line in f:
        m = file_audit_pattern.search(line)
        if m:
            rel_path = m.group(1)
            audits[rel_path] = {
                "relativePath": rel_path,
                "read": int(m.group(2)),
                "ins": int(m.group(3)),
                "upd": int(m.group(4)),
                "skip": int(m.group(5)),
                "fail": int(m.group(6)),
                "uniqueKeys": int(m.group(7)),
                "geoms": int(m.group(8)),
                "timeMs": int(m.group(9)),
                "speed": float(m.group(10)),
                "ramMb": int(m.group(11))
            }

print(f"Audits parsed: {len(audits)} files")

# Query PostgreSQL for actual record counts per dataset_key
print("3. Querying PostgreSQL for raw_dataset_record counts...")
psql_cmd = [
    "docker", "exec", "-i", "kcht_postgres",
    "psql", "-U", "kcht_user", "-d", "kcht_db", "-A", "-F", "\t", "-t", "-c",
    "SELECT dataset_key, count(*) FROM raw_dataset_record GROUP BY dataset_key;"
]
res = subprocess.run(psql_cmd, capture_output=True, text=True, check=True)
db_counts = {}
for line in res.stdout.strip().split("\n"):
    if line.strip():
        parts = line.strip().split("\t")
        if len(parts) == 2:
            db_counts[parts[0]] = int(parts[1])

print(f"PostgreSQL dataset counts retrieved: {len(db_counts)} datasets with records")

# Summary aggregates
total_read = sum(a["read"] for a in audits.values())
total_ins = sum(a["ins"] for a in audits.values())
total_upd = sum(a["upd"] for a in audits.values())
total_skip = sum(a["skip"] for a in audits.values())
total_fail = sum(a["fail"] for a in audits.values())
total_geoms = sum(a["geoms"] for a in audits.values())
total_time_ms = sum(a["timeMs"] for a in audits.values())

print(f"TOTAL AUDIT AGGREGATES:")
print(f"Read: {total_read}")
print(f"Inserted: {total_ins}")
print(f"Updated: {total_upd}")
print(f"Skipped: {total_skip}")
print(f"Failed: {total_fail}")
print(f"Valid Geometries: {total_geoms}")
print(f"Sum File Time: {total_time_ms} ms ({total_time_ms / 1000.0:.1f} s)")

# Save reconciled data to JSON for easy reference
reconciled_list = []
empty_datasets = []
duplicate_key_datasets = []

for rel_path, m_info in manifest_map.items():
    dataset_key = os.path.splitext(os.path.basename(rel_path))[0]
    m_records = m_info.get("records", 0)
    kind = m_info.get("kind", "asset")
    a = audits.get(rel_path, {
        "read": 0, "ins": 0, "upd": 0, "skip": 0, "fail": 0,
        "uniqueKeys": 0, "geoms": 0, "timeMs": 0, "speed": 0.0, "ramMb": 0
    })
    db_rec = db_counts.get(dataset_key, 0)
    
    diff = m_records - db_rec
    rec_obj = {
        "path": rel_path,
        "dataset_key": dataset_key,
        "kind": kind,
        "manifest_records": m_records,
        "read": a["read"],
        "inserted": a["ins"],
        "updated": a["upd"],
        "skipped": a["skip"],
        "failed": a["fail"],
        "db_records": db_rec,
        "geoms": a["geoms"],
        "time_ms": a["timeMs"],
        "speed": a["speed"],
        "diff": diff
    }
    reconciled_list.append(rec_obj)
    if m_records == 0:
        empty_datasets.append(rec_obj)
    if diff != 0:
        duplicate_key_datasets.append(rec_obj)

print(f"Total datasets evaluated: {len(reconciled_list)}")
print(f"Empty datasets (0 records): {len(empty_datasets)}")
print(f"Datasets with source duplicate keys (diff != 0): {len(duplicate_key_datasets)}")

with open("scratch/reconciled_summary.json", "w", encoding="utf-8") as f:
    json.dump({
        "total_files": len(reconciled_list),
        "total_read": total_read,
        "total_inserted": total_ins,
        "total_updated": total_upd,
        "total_skipped": total_skip,
        "total_failed": total_fail,
        "total_geoms": total_geoms,
        "total_db_records": sum(db_counts.values()),
        "empty_datasets_count": len(empty_datasets),
        "diff_datasets_count": len(duplicate_key_datasets),
        "datasets": reconciled_list
    }, f, ensure_ascii=False, indent=2)

print("Saved scratch/reconciled_summary.json successfully.")
