# Green tree physical-table separation

## Scope and current status

This release implements the safe part of the separation:

- EXPAND migration: V23__expand_green_tree_record.sql.
- BACKFILL migration: V24__backfill_green_tree_record.sql.
- Runtime modes: LEGACY, SHADOW, NEW, FINAL.
- Dedicated CRUD, query, mapper, GIS, permission, audit, import, and export paths.
- No CONTRACT migration has been created or executed.

Repository baseline when work started:

- HEAD: 49721866aff14fc37633670437dd5d3ef233e2c4.
- Latest existing Flyway version: V22.
- Worktree: clean before implementation.
- Default runtime mode: LEGACY.
- Render Flyway default: FLYWAY_ENABLED=false.

Production facts are currently unknown because no reachable PostgreSQL/Supabase connection or Render/Vercel credentials were available. Counts, checksums, EXPLAIN plans, backups, deployed commits, and production smoke tests must not be inferred from local tests.

## Existing architecture and dependencies

Before this change, green trees were:

- Curated into asset_record by V22, with dataset_code='green_trees' and asset_type='TREE'.
- Queried through AssetQueryAdapterImpl.
- Written through the generic AssetCrudService.
- Located by parsing geometry/JSON in VroadSurveyController.
- Linked to immutable source data through raw_record_id.
- Linked to images through vroad_image_reference, using either attributes.source_record_key or raw_dataset_record.record_key.
- Excluded from parts of the generic asset UI by frontend guards and request filters.

Repository-wide dependencies found during analysis include:

- Flyway migrations and dynamic views involving asset_record/asset_geometry.
- Dataset list/detail/export routing.
- Generic asset CRUD/import and dataset counts.
- VROAD dashboard, asset markers, tree markers, and deep links.
- Branch visibility through vroad_record_visible.
- Image and document lineage.
- Frontend list, modal, GIS layer, deep-link, and import copy.
- Controller, security-filter, list-page, and map-page tests.

The backend now dispatches green_trees before generic curated/raw fallback. Generic vroad_assets list/detail/export/GIS/dashboard paths exclude tree classifications server-side.

## New schema

green_tree_record preserves numeric IDs and source lineage. It contains:

- Identity primary key with explicit-ID backfill support.
- Unique record_id and nullable raw_record_id with ON DELETE SET NULL.
- Existing generic asset fields needed to preserve the public payload.
- Typed green-tree fields.
- Nullable PostGIS Point(4326) geometry.
- Unmodified legacy attributes JSONB.
- Soft-delete, timestamps, and optimistic-lock version.
- B-tree, GIN, GiST, and conditional trigram indexes.

No unique partial index is added to raw_record_id until production preflight proves one-to-one lineage.
Legacy timestamps remain nullable because the original V3 schema did not enforce NOT NULL; backfill therefore preserves null timestamps instead of inventing values.

## Field mapping

| New field | Priority/source |
|---|---|
| id | asset_record.id |
| raw_record_id | asset_record.raw_record_id |
| record_id | asset_record.record_id |
| Generic normalized fields | Corresponding asset_record column |
| tree_code, species fields | asset_record.attributes, then raw payload |
| Measurements and planting year | Valid value in attributes, then raw payload |
| health_status | attributes health, attributes condition, raw health, raw condition |
| risk_level, notes | attributes, then raw payload |
| geom | valid point_geom, valid Point geom, attributes pair, raw pair; explicit/invalid attribute coordinate keys block raw fallback |
| attributes | Exact asset_record.attributes, without enrichment or deletion |
| Delete/version/timestamps | Exact asset_record values |

Invalid negative legacy measurements remain in attributes; their typed columns are null. Coordinates are created only from a complete, valid longitude/latitude pair from the same priority source.
Clearing a coordinate stores JSON null tombstones for both keys so LEGACY/SHADOW reads cannot resurrect immutable raw coordinates. Updates that omit both coordinate keys preserve the existing geometry.

API-only constants remain:

- datasetKey = green_trees
- payload.dataset_code = green_trees
- payload.asset_type = TREE

Typed columns win over conflicting keys in attributes.

## Runtime mode matrix

| Mode | Read | Write |
|---|---|---|
| LEGACY | asset_record | asset_record |
| SHADOW | asset_record | Legacy first, then same ID to both tables in one transaction |
| NEW | green_tree_record | Legacy first, then same ID to both tables in one transaction |
| FINAL | green_tree_record | green_tree_record only |

An invalid GREEN_TREE_STORAGE_MODE stops application startup with an explicit error.

## Deployment gates

### Gate 0 - preflight

1. Create a Supabase backup and record its recovery identifier.
2. Confirm the trusted runner can connect without printing secrets.
3. Run scripts/green-tree/01_preflight.sql with psql.
4. Save all output in the release evidence location.
5. Stop if duplicate IDs/lineage, suspicious coordinates, or unknown constraints need a decision.
6. Record the raw-data and tree checksums.

Production currently has the V22 schema/data but no `flyway_schema_history` table. The first
managed Flyway run must therefore use `FLYWAY_BASELINE_VERSION=22`. Never baseline at V21 (or
Flyway's default V1): rerunning V22 updates legacy tree timestamps before backfill.

For a Supabase runner, configure the environment without committing secrets:

~~~dotenv
POSTGRES_HOST=aws-0-ap-southeast-1.pooler.supabase.com
POSTGRES_PORT=5432
POSTGRES_DB=postgres
POSTGRES_USER=postgres.<project-ref>
POSTGRES_SSLMODE=require
FLYWAY_BASELINE_VERSION=22
FLYWAY_TARGET=22
~~~

The production compose Flyway service now builds its JDBC URL from these variables. Use
`--no-deps` so a trusted runner does not start the compose-local PostgreSQL service while targeting
Supabase. The default target is also pinned at V22, so enabling Flyway without an explicit release
target cannot apply V23 and V24 together.

Do not use C:\dataexcel as a migration source. It may only be used for a separately approved comparison report.

### Release 1A - EXPAND only

Do not run the existing all-the-way migration helper for this gate. Target Flyway version 23 explicitly:

~~~powershell
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway info

# Run this once only when flyway_schema_history is absent.
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway baseline

docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway info
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway validate
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway -target=23 migrate
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway info
~~~

After the explicit baseline, stop unless `flyway info` shows V22 as `Baseline` rather than
`Success` or `Pending`. The intended state after Release 1A is a V22 baseline followed only by a
successful V23 migration, with V24 still pending.

Then:

1. Deploy the backend with GREEN_TREE_STORAGE_MODE=LEGACY.
2. Keep FLYWAY_ENABLED=false on Render.
3. Smoke-test existing tree list/detail/CRUD/import/export/GIS URLs.
4. Change Render to SHADOW and restart.
5. Create, update, then soft-delete a controlled test tree.
6. Verify both tables have the same ID, version, attributes, and deletion state.

Rollback at this point: set the mode back to LEGACY; retain the new table for investigation.

### Release 1B - BACKFILL

Target Flyway version 24 explicitly:

~~~powershell
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway validate
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway -target=24 migrate
docker compose -f docker-compose.prod.yml --env-file .env.production run --rm --no-deps flyway info
~~~

Then:

1. Run scripts/green-tree/02_reconciliation.sql.
2. Require zero rows from every mismatch query.
3. Compare raw checksums to the saved preflight output.
4. Compare API list totals and exported record_id sets in LEGACY and SHADOW.
5. Run ANALYZE green_tree_record.
6. Run scripts/green-tree/03_performance_explain.sql on staging, then production during a safe window.

Any mismatch blocks NEW.

### Release 2 - NEW

1. Set GREEN_TREE_STORAGE_MODE=NEW.
2. Deploy/restart Render and confirm its commit.
3. Smoke-test all public green-tree URLs.
4. Verify branch/admin visibility, optimistic locking, images, coordinate clearing, and batch rollback.
5. Confirm the generic asset list/map/dashboard does not include trees.
6. Observe dual-write for at least seven days.
7. Re-run reconciliation after representative create/update/delete/import activity.

Rollback: set the mode to LEGACY. Because NEW still dual-writes, no restore is needed if reconciliation remains clean.

### Release 3 - FINAL

FINAL requires explicit approval, a fresh backup, and final zero-mismatch reconciliation.

1. Enter a short write maintenance window.
2. Reconcile one final time.
3. Run scripts/green-tree/05_sync_sequence_before_final.sql while writes remain paused. SHADOW/NEW use explicit legacy IDs, so the identity sequence may otherwise lag behind MAX(id).
4. Set GREEN_TREE_STORAGE_MODE=FINAL.
5. Smoke-test new-table reads and writes, including creation of one controlled record with an ID greater than the previous maximum.
6. End the maintenance window only after successful checks.

This repository intentionally contains no CONTRACT migration.

### Future CONTRACT

CONTRACT needs separate approval and a separate release. Before deletion:

- Verify there is no valid non-green dataset using asset_type='TREE'.
- Reconfirm all images/documents/lineage and restore procedures.
- Take another backup.
- Pause tree writes.
- Reconcile at 100%.

Only then may a future migration remove legacy tree rows/geometry and add a guard preventing their return.

## Rollback

Before CONTRACT:

- NEW failure: switch to LEGACY.
- SHADOW failure: switch to LEGACY.
- Do not drop or clean green_tree_record; preserve it for mismatch analysis.
- Do not modify raw_dataset_record.

After a future CONTRACT:

- Prefer restoring the approved database backup.
- If backup restoration is not selected, use scripts/green-tree/04_restore_legacy_after_contract.sql only in a maintenance window after reviewing conflicts.
- Re-run preflight/reconciliation and switch to LEGACY only after the restored legacy rows are verified.

## Required production evidence

The release report must capture:

- Backup identifiers.
- Preflight and reconciliation output.
- Total/active/deleted counts.
- Coordinate and image counts.
- Raw and tree checksums.
- EXPLAIN plans and timings.
- Storage-mode changes with timestamps.
- GitHub commit and Render/Vercel deployed commits.
- Supabase Flyway history through the intended target.
- Smoke-test results for list, detail, CRUD, import, export, GIS, image, permissions, and generic-asset exclusion.

## Known risks and stop conditions

- Production lineage uniqueness is unknown; do not add the partial unique index yet.
- No local PostgreSQL/PostGIS daemon was reachable, so migrations were not executed locally.
- Production counts/checksums/plans are unknown until the SQL scripts run.
- Existing invalid measurements remain only in attributes; report them before NEW.
- A mismatch in IDs, raw lineage, JSON, versions, timestamps, geometry, image resolution, permissions, API totals, exports, or raw checksums blocks mode promotion.
- Do not enable Render Flyway automatically merely to apply these migrations.
