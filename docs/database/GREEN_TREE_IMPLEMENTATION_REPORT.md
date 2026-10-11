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

The local verification snapshot below was captured before production rollout. Values that remain unknown must be filled by the trusted deployment runner after executing the runbook:

| Evidence | Status |
|---|---|
| Trees before/after | Unknown / not run |
| Active/deleted trees | Unknown / not run |
| Trees with/without selected coordinates | Unknown / not run |
| Trees with images/documents | Unknown / not run |
| Duplicate `record_id` / `raw_record_id` | Unknown / not run |
| Tree, attribute, and raw checksums | Unknown / not run |
| Supabase backup identifier | Not created by this implementation |
| Supabase Flyway history | Untouched |
| Render storage mode | Untouched; repository default is `LEGACY` |
| Render deployed commit | Unknown / untouched |
| Vercel deployed commit | Unknown / untouched |
| GitHub commit | Use the Git commit containing this report |

## Release blockers

Before promotion to SHADOW/NEW, run the preflight, apply Flyway with explicit targets, and require zero reconciliation mismatches as documented in `GREEN_TREE_SEPARATION_RUNBOOK.md`.

Before FINAL, pause tree writes, run final reconciliation, synchronize the identity sequence with `scripts/green-tree/05_sync_sequence_before_final.sql`, and verify a controlled create uses an ID above the prior maximum.

CONTRACT requires a separate explicit approval, fresh backup, maintenance window, and new migration. It must never modify `raw_dataset_record`.
