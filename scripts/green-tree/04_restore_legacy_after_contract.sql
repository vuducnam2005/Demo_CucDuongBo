\set ON_ERROR_STOP on

-- Emergency rollback only after an approved CONTRACT release.
-- Preconditions: maintenance window is active, a fresh backup exists, and FINAL writes are paused.
-- This script never changes raw_dataset_record, images, documents, or audit history.
BEGIN;

SELECT pg_advisory_xact_lock(hashtext('green_tree_restore_legacy_after_contract'));

INSERT INTO asset_record (
    id, raw_record_id, record_id, gid, dataset_code, asset_type, name,
    route_code, route_name, km_from, km_to, lytrinh,
    province_id, province_name, district_name, town_name, road_class, road_type,
    organization_id, branch_id, branch_name, management_agency,
    state, state_name, level_state, active_status, maintain_value, construction_year, parent_id,
    attributes, is_deleted, deleted_at, deleted_by, version, created_at, updated_at
)
SELECT
    id, raw_record_id, record_id, gid, 'green_trees', 'TREE', name,
    route_code, route_name, km_from, km_to, lytrinh,
    province_id, province_name, district_name, town_name, road_class, road_type,
    organization_id, branch_id, branch_name, management_agency,
    state, state_name, level_state, active_status, maintain_value, construction_year, parent_id,
    attributes, is_deleted, deleted_at, deleted_by, version, created_at, updated_at
FROM green_tree_record
ON CONFLICT (id) DO UPDATE SET
    raw_record_id = EXCLUDED.raw_record_id,
    record_id = EXCLUDED.record_id,
    gid = EXCLUDED.gid,
    dataset_code = 'green_trees',
    asset_type = 'TREE',
    name = EXCLUDED.name,
    route_code = EXCLUDED.route_code,
    route_name = EXCLUDED.route_name,
    km_from = EXCLUDED.km_from,
    km_to = EXCLUDED.km_to,
    lytrinh = EXCLUDED.lytrinh,
    province_id = EXCLUDED.province_id,
    province_name = EXCLUDED.province_name,
    district_name = EXCLUDED.district_name,
    town_name = EXCLUDED.town_name,
    road_class = EXCLUDED.road_class,
    road_type = EXCLUDED.road_type,
    organization_id = EXCLUDED.organization_id,
    branch_id = EXCLUDED.branch_id,
    branch_name = EXCLUDED.branch_name,
    management_agency = EXCLUDED.management_agency,
    state = EXCLUDED.state,
    state_name = EXCLUDED.state_name,
    level_state = EXCLUDED.level_state,
    active_status = EXCLUDED.active_status,
    maintain_value = EXCLUDED.maintain_value,
    construction_year = EXCLUDED.construction_year,
    parent_id = EXCLUDED.parent_id,
    attributes = EXCLUDED.attributes,
    is_deleted = EXCLUDED.is_deleted,
    deleted_at = EXCLUDED.deleted_at,
    deleted_by = EXCLUDED.deleted_by,
    version = EXCLUDED.version,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

INSERT INTO asset_geometry (
    asset_id, geom_type, geom, point_geom,
    bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax,
    srid, is_valid, updated_at
)
SELECT id, 'POINT', geom, geom,
       ST_X(geom), ST_Y(geom), ST_X(geom), ST_Y(geom),
       4326, TRUE, updated_at
FROM green_tree_record
WHERE geom IS NOT NULL
ON CONFLICT (asset_id) DO UPDATE SET
    geom_type = 'POINT',
    geom = EXCLUDED.geom,
    point_geom = EXCLUDED.point_geom,
    line_geom = NULL,
    bbox_xmin = EXCLUDED.bbox_xmin,
    bbox_ymin = EXCLUDED.bbox_ymin,
    bbox_xmax = EXCLUDED.bbox_xmax,
    bbox_ymax = EXCLUDED.bbox_ymax,
    srid = 4326,
    is_valid = TRUE,
    updated_at = EXCLUDED.updated_at;

DELETE FROM asset_geometry ag
USING green_tree_record gt
WHERE ag.asset_id = gt.id
  AND gt.geom IS NULL;

SELECT setval(
    pg_get_serial_sequence('asset_record', 'id'),
    COALESCE((SELECT MAX(id) FROM asset_record), 1),
    EXISTS (SELECT 1 FROM asset_record)
);

UPDATE dataset_registry
SET total_records = (
        SELECT COUNT(*) FROM asset_record
        WHERE dataset_code = 'green_trees' AND asset_type = 'TREE' AND is_deleted = FALSE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE dataset_key = 'green_trees';

COMMIT;
