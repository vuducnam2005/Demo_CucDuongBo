# Green-tree production preflight - 2026-10-11

## Scope

Read-only queries were executed against Supabase project `xojcuekhqisjmgyshafm` before any
green-tree schema migration. No secret, password, token, or connection string is recorded here.

## Database state

| Check | Result |
|---|---:|
| Project health | Healthy |
| Supabase managed backups | None (Free Plan) |
| `flyway_schema_history` | Absent |
| `green_tree_record` | Absent |
| `raw_dataset_record` rows | 6,461 |
| Legacy green-tree rows | 60 |
| Active / soft-deleted | 60 / 0 |
| Distinct `record_id` | 60 |
| Distinct non-null `raw_record_id` | 60 |
| Rows without `raw_record_id` | 0 |
| Duplicate `record_id` groups | 0 |
| Duplicate `raw_record_id` groups | 0 |
| ID range | 1-60 |
| Version distribution | version 1: 60 |
| Branch distribution | null: 60 |
| Invalid legacy measurements | 0 |
| Invalid attribute coordinate pairs | 0 |
| Trees with any valid coordinates | 60 |
| Trees without valid coordinates | 0 |
| Trees with source images | 60 |
| Trees with documents | 0 |
| Related audit rows | 0 |

All 60 trees have valid coordinate pairs in both legacy attributes and related raw payloads.
There are currently no point geometries in `asset_geometry`, so V24 will select attribute
coordinates according to the documented precedence rule.

## Preservation hashes

These hashes are release evidence for post-backfill comparison:

| Dataset | MD5 |
|---|---|
| Full legacy preservation payload from `01_preflight.sql` | `348a3685a54dfa0079b5a0e17380b29c` |
| Aggregate legacy attributes by numeric ID | `d3a6c3d82e8f1f664d8ab8bbd435d6c9` |
| Related raw rows (`id`, dataset key, record key, payload) | `92ad8b952ebf27dffddf880ed3b81006` |
| Ordered legacy `record_id` values | `b0ccd5b6e0e399604359331073069c42` |

Captured database timestamps ranged from `2026-10-07T12:40:14.345533Z` to the same value for
both `created_at` and `updated_at`.

## Deployment gate

V23/V24 remain unapplied because the project has no recoverable backup. Storage mode remains
`LEGACY`, Render Flyway remains disabled, and no CONTRACT migration exists. Before Release 1A:

1. Create and verify a restorable logical or managed Supabase backup.
2. Initialize Flyway with baseline version 22; do not rerun V22.
3. Apply only V23, deploy SHADOW, verify dual-write, then apply V24 and reconcile.
