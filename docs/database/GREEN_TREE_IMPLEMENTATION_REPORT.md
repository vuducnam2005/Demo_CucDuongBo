# Green tree separation implementation report

## Local implementation status

- Baseline commit: `49721866aff14fc37633670437dd5d3ef233e2c4`.
- Latest pre-existing Flyway migration: V22.
- Added EXPAND V23 and idempotent BACKFILL V24.
- No CONTRACT migration exists.
- Default configured storage mode: `LEGACY`.
- Default Render Flyway setting remains `FLYWAY_ENABLED=false`.
- Public green-tree URLs and numeric IDs are preserved by the dedicated backend dispatcher and same-ID dual write.
- Generic `vroad_assets` list/detail/export/GIS/dashboard paths exclude tree classifications server-side.

## Local verification

The final command results for this workspace are recorded here before handoff:

| Check | Result |
|---|---|
| Backend clean tests | Passed: 85 tests (`.\\mvnw.bat clean test`) |
| Frontend tests | Passed sequentially: 23 files, 110 tests (`npm test -- --run --no-file-parallelism --maxWorkers=1`) |
| Frontend lint | Passed (`npm run lint`) |
| Frontend production build | Passed (`npm run build`); existing Ant Design chunk-size warning only |
| `git diff --check` | Passed; line-ending conversion warnings only |
| PostgreSQL/PostGIS migration execution | Not run - database unavailable |
| Reconciliation | Not run - database unavailable |
| EXPLAIN ANALYZE | Not run - database unavailable |

## Production evidence

Production preflight and deployment verification were captured on 2026-10-11. Detailed database
evidence is stored in `GREEN_TREE_PRODUCTION_PREFLIGHT_2026-10-11.md`.

| Evidence | Status |
|---|---|
| Trees before/after | 60 / not backfilled |
| Active/deleted trees | 60 / 0 |
| Trees with/without selected coordinates | 60 / 0 |
| Trees with images/documents | 60 / 0 |
| Duplicate `record_id` / `raw_record_id` groups | 0 / 0 |
| Tree, attribute, and raw checksums | Captured in production preflight evidence |
| Supabase backup identifier | Blocked: Free Plan reports no backups |
| Supabase Flyway history | Absent; V23/V24 unapplied |
| Render storage mode | `LEGACY`; `FLYWAY_ENABLED=false` |
| Render deployed commit | `c7b7f4a9c182900e861ec4a58e4f9d1dc188caed` |
| Vercel deployed commit | `c7b7f4a9c182900e861ec4a58e4f9d1dc188caed` |
| GitHub commit | `c7b7f4a9c182900e861ec4a58e4f9d1dc188caed` before the deployment-safety follow-up |

## Release blockers

Before promotion to SHADOW/NEW, create a restorable backup, initialize Flyway at baseline V22,
apply Flyway with explicit targets, and require zero reconciliation mismatches as documented in
`GREEN_TREE_SEPARATION_RUNBOOK.md`.

Before FINAL, pause tree writes, run final reconciliation, synchronize the identity sequence with `scripts/green-tree/05_sync_sequence_before_final.sql`, and verify a controlled create uses an ID above the prior maximum.

CONTRACT requires a separate explicit approval, fresh backup, maintenance window, and new migration. It must never modify `raw_dataset_record`.
