\set ON_ERROR_STOP on

-- Run after V24. Any non-zero mismatch count blocks NEW/FINAL.
BEGIN TRANSACTION READ ONLY;

WITH legacy AS (
    SELECT *
    FROM asset_record
    WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
)
SELECT (SELECT COUNT(*) FROM legacy) AS legacy_total,
       (SELECT COUNT(*) FROM green_tree_record) AS new_total,
       (SELECT COUNT(*) FROM legacy WHERE is_deleted = FALSE) AS legacy_active,
       (SELECT COUNT(*) FROM green_tree_record WHERE is_deleted = FALSE) AS new_active,
       (SELECT COUNT(*) FROM legacy WHERE is_deleted = TRUE) AS legacy_deleted,
       (SELECT COUNT(*) FROM green_tree_record WHERE is_deleted = TRUE) AS new_deleted;

SELECT 'missing_in_new' AS mismatch_type, legacy.id, legacy.record_id
FROM asset_record legacy
LEFT JOIN green_tree_record tree ON tree.id = legacy.id
WHERE legacy.dataset_code = 'green_trees' AND legacy.asset_type = 'TREE' AND tree.id IS NULL
UNION ALL
SELECT 'missing_in_legacy', tree.id, tree.record_id
FROM green_tree_record tree
LEFT JOIN asset_record legacy
  ON legacy.id = tree.id
 AND legacy.dataset_code = 'green_trees'
 AND legacy.asset_type = 'TREE'
WHERE legacy.id IS NULL
ORDER BY mismatch_type, id;

SELECT legacy.id, legacy.record_id,
       legacy.raw_record_id AS legacy_raw_record_id, tree.raw_record_id AS new_raw_record_id,
       md5(legacy.attributes::text) AS legacy_attributes_hash,
       md5(tree.attributes::text) AS new_attributes_hash,
       legacy.version AS legacy_version, tree.version AS new_version,
       legacy.created_at AS legacy_created_at, tree.created_at AS new_created_at,
       legacy.updated_at AS legacy_updated_at, tree.updated_at AS new_updated_at,
       legacy.deleted_at AS legacy_deleted_at, tree.deleted_at AS new_deleted_at,
       legacy.deleted_by AS legacy_deleted_by, tree.deleted_by AS new_deleted_by
FROM asset_record legacy
JOIN green_tree_record tree ON tree.id = legacy.id
WHERE legacy.dataset_code = 'green_trees'
  AND legacy.asset_type = 'TREE'
  AND (legacy.record_id IS DISTINCT FROM tree.record_id
    OR legacy.raw_record_id IS DISTINCT FROM tree.raw_record_id
    OR legacy.attributes IS DISTINCT FROM tree.attributes
    OR legacy.version IS DISTINCT FROM tree.version
    OR legacy.created_at IS DISTINCT FROM tree.created_at
    OR legacy.updated_at IS DISTINCT FROM tree.updated_at
    OR legacy.is_deleted IS DISTINCT FROM tree.is_deleted
    OR legacy.deleted_at IS DISTINCT FROM tree.deleted_at
    OR legacy.deleted_by IS DISTINCT FROM tree.deleted_by)
ORDER BY legacy.id;

WITH expected AS (
    SELECT asset.id,
           asset.gid,
           asset.name,
           asset.route_code,
           asset.route_name,
           asset.km_from,
           asset.km_to,
           asset.lytrinh,
           asset.province_id,
           asset.province_name,
           asset.district_name,
           asset.town_name,
           asset.road_class,
           asset.road_type,
           asset.organization_id,
           asset.branch_id,
           asset.branch_name,
           asset.management_agency,
           asset.state,
           asset.state_name,
           asset.level_state,
           asset.active_status,
           asset.maintain_value,
           asset.construction_year,
           asset.parent_id,
           COALESCE(NULLIF(asset.attributes->>'tree_code', ''), NULLIF(raw.raw_payload->>'tree_code', '')) AS tree_code,
           COALESCE(NULLIF(asset.attributes->>'species_name', ''), NULLIF(raw.raw_payload->>'species_name', '')) AS species_name,
           COALESCE(NULLIF(asset.attributes->>'scientific_name', ''), NULLIF(raw.raw_payload->>'scientific_name', '')) AS scientific_name,
           CASE
               WHEN COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm')
                    ~ '^[+]?[0-9]+([.][0-9]+)?$'
                   THEN CASE
                       WHEN COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm')::numeric >= 0
                           THEN COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm')::numeric
                   END
           END AS diameter_cm,
           CASE
               WHEN COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m')
                    ~ '^[+]?[0-9]+([.][0-9]+)?$'
                   THEN CASE
                       WHEN COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m')::numeric >= 0
                           THEN COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m')::numeric
                   END
           END AS height_m,
           CASE
               WHEN COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m')
                    ~ '^[+]?[0-9]+([.][0-9]+)?$'
                   THEN CASE
                       WHEN COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m')::numeric >= 0
                           THEN COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m')::numeric
                   END
           END AS canopy_diameter_m,
           CASE
               WHEN COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year') ~ '^-?[0-9]+$'
                   THEN CASE
                       WHEN COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year')::numeric
                               BETWEEN -2147483648 AND 2147483647
                           THEN COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year')::integer
                   END
           END AS planting_year,
           COALESCE(NULLIF(asset.attributes->>'health_status', ''),
                    NULLIF(asset.attributes->>'condition', ''),
                    NULLIF(raw.raw_payload->>'health_status', ''),
                    NULLIF(raw.raw_payload->>'condition', '')) AS health_status,
           COALESCE(NULLIF(asset.attributes->>'risk_level', ''), NULLIF(raw.raw_payload->>'risk_level', '')) AS risk_level,
           COALESCE(NULLIF(asset.attributes->>'notes', ''), NULLIF(raw.raw_payload->>'notes', '')) AS notes
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    WHERE asset.dataset_code = 'green_trees' AND asset.asset_type = 'TREE'
)
SELECT expected.id
FROM expected
JOIN green_tree_record tree ON tree.id = expected.id
WHERE expected.gid IS DISTINCT FROM tree.gid
   OR expected.name IS DISTINCT FROM tree.name
   OR expected.route_code IS DISTINCT FROM tree.route_code
   OR expected.route_name IS DISTINCT FROM tree.route_name
   OR expected.km_from IS DISTINCT FROM tree.km_from
   OR expected.km_to IS DISTINCT FROM tree.km_to
   OR expected.lytrinh IS DISTINCT FROM tree.lytrinh
   OR expected.province_id IS DISTINCT FROM tree.province_id
   OR expected.province_name IS DISTINCT FROM tree.province_name
   OR expected.district_name IS DISTINCT FROM tree.district_name
   OR expected.town_name IS DISTINCT FROM tree.town_name
   OR expected.road_class IS DISTINCT FROM tree.road_class
   OR expected.road_type IS DISTINCT FROM tree.road_type
   OR expected.organization_id IS DISTINCT FROM tree.organization_id
   OR expected.branch_id IS DISTINCT FROM tree.branch_id
   OR expected.branch_name IS DISTINCT FROM tree.branch_name
   OR expected.management_agency IS DISTINCT FROM tree.management_agency
   OR expected.state IS DISTINCT FROM tree.state
   OR expected.state_name IS DISTINCT FROM tree.state_name
   OR expected.level_state IS DISTINCT FROM tree.level_state
   OR expected.active_status IS DISTINCT FROM tree.active_status
   OR expected.maintain_value IS DISTINCT FROM tree.maintain_value
   OR expected.construction_year IS DISTINCT FROM tree.construction_year
   OR expected.parent_id IS DISTINCT FROM tree.parent_id
   OR expected.tree_code IS DISTINCT FROM tree.tree_code
   OR expected.species_name IS DISTINCT FROM tree.species_name
   OR expected.scientific_name IS DISTINCT FROM tree.scientific_name
   OR expected.diameter_cm IS DISTINCT FROM tree.diameter_cm
   OR expected.height_m IS DISTINCT FROM tree.height_m
   OR expected.canopy_diameter_m IS DISTINCT FROM tree.canopy_diameter_m
   OR expected.planting_year IS DISTINCT FROM tree.planting_year
   OR expected.health_status IS DISTINCT FROM tree.health_status
   OR expected.risk_level IS DISTINCT FROM tree.risk_level
   OR expected.notes IS DISTINCT FROM tree.notes
ORDER BY expected.id;

WITH expected_geom AS (
    SELECT asset.id,
           CASE
               WHEN geometry.point_geom IS NOT NULL
                AND ST_IsValid(geometry.point_geom)
                AND ST_X(geometry.point_geom) BETWEEN -180 AND 180
                AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90
                   THEN geometry.point_geom
               WHEN (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                               AND ST_IsValid(geometry.geom) THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180
                AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                               AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90
                   THEN geometry.geom::geometry(Point, 4326)
               WHEN (CASE WHEN asset.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                          THEN (asset.attributes->>'longitude')::double precision END) BETWEEN -180 AND 180
                AND (CASE WHEN asset.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                          THEN (asset.attributes->>'latitude')::double precision END) BETWEEN -90 AND 90
                   THEN ST_SetSRID(ST_MakePoint(
                       (asset.attributes->>'longitude')::double precision,
                       (asset.attributes->>'latitude')::double precision), 4326)
               WHEN asset.attributes ? 'longitude' OR asset.attributes ? 'latitude'
                   THEN NULL
               WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                          THEN (raw.raw_payload->>'longitude')::double precision END) BETWEEN -180 AND 180
                AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                          THEN (raw.raw_payload->>'latitude')::double precision END) BETWEEN -90 AND 90
                   THEN ST_SetSRID(ST_MakePoint(
                       (raw.raw_payload->>'longitude')::double precision,
                       (raw.raw_payload->>'latitude')::double precision), 4326)
           END AS geom
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    LEFT JOIN asset_geometry geometry ON geometry.asset_id = asset.id
    WHERE asset.dataset_code = 'green_trees' AND asset.asset_type = 'TREE'
)
SELECT expected.id,
       ST_AsText(expected.geom) AS expected_geom,
       ST_AsText(tree.geom) AS actual_geom
FROM expected_geom expected
JOIN green_tree_record tree ON tree.id = expected.id
WHERE NOT (
    (expected.geom IS NULL AND tree.geom IS NULL)
    OR (expected.geom IS NOT NULL AND tree.geom IS NOT NULL AND ST_Equals(expected.geom, tree.geom))
)
ORDER BY expected.id;

SELECT id, record_id, ST_X(geom) AS longitude, ST_Y(geom) AS latitude
FROM green_tree_record
WHERE geom IS NOT NULL
  AND (ST_X(geom) NOT BETWEEN -180 AND 180 OR ST_Y(geom) NOT BETWEEN -90 AND 90)
ORDER BY id;

WITH legacy_images AS (
    SELECT DISTINCT asset.id
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    JOIN vroad_image_reference image
      ON image.dataset_key = 'vroad_assets'
     AND image.record_key = COALESCE(NULLIF(asset.attributes->>'source_record_key', ''), raw.record_key)
    WHERE asset.dataset_code = 'green_trees' AND asset.asset_type = 'TREE'
), new_images AS (
    SELECT DISTINCT tree.id
    FROM green_tree_record tree
    LEFT JOIN raw_dataset_record raw ON raw.id = tree.raw_record_id
    JOIN vroad_image_reference image
      ON image.dataset_key = 'vroad_assets'
     AND image.record_key = COALESCE(NULLIF(tree.attributes->>'source_record_key', ''), raw.record_key)
)
SELECT (SELECT COUNT(*) FROM legacy_images) AS legacy_trees_with_images,
       (SELECT COUNT(*) FROM new_images) AS new_trees_with_images,
       (SELECT COUNT(*) FROM legacy_images legacy
        FULL JOIN new_images newer USING (id)
        WHERE legacy.id IS NULL OR newer.id IS NULL) AS image_link_mismatches;

WITH scoped_users AS (
    SELECT user_account.id, user_account.branch_id
    FROM app_user user_account
    JOIN app_role role ON role.id = user_account.role_id
    WHERE user_account.is_active = TRUE
      AND role.role_code <> 'ROLE_ADMIN'
      AND user_account.branch_id IS NOT NULL
), legacy_visible AS (
    SELECT scoped_users.id AS user_id, tree.id AS tree_id
    FROM scoped_users
    JOIN asset_record tree
      ON tree.dataset_code = 'green_trees'
     AND tree.asset_type = 'TREE'
     AND tree.is_deleted = FALSE
     AND ((tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, scoped_users.id))
       OR (tree.raw_record_id IS NULL AND tree.branch_id = scoped_users.branch_id))
), new_visible AS (
    SELECT scoped_users.id AS user_id, tree.id AS tree_id
    FROM scoped_users
    JOIN green_tree_record tree
      ON tree.is_deleted = FALSE
     AND ((tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, scoped_users.id))
       OR (tree.raw_record_id IS NULL AND tree.branch_id = scoped_users.branch_id))
)
SELECT COALESCE(legacy.user_id, newer.user_id) AS user_id,
       COALESCE(legacy.tree_id, newer.tree_id) AS tree_id,
       CASE WHEN legacy.tree_id IS NULL THEN 'missing_in_legacy_scope'
            ELSE 'missing_in_new_scope' END AS mismatch_type
FROM legacy_visible legacy
FULL JOIN new_visible newer
  ON newer.user_id = legacy.user_id AND newer.tree_id = legacy.tree_id
WHERE legacy.tree_id IS NULL OR newer.tree_id IS NULL
ORDER BY user_id, tree_id;

SELECT total_records AS registry_total,
       (SELECT COUNT(*) FROM green_tree_record WHERE is_deleted = FALSE) AS actual_active_total
FROM dataset_registry
WHERE dataset_key = 'green_trees';

SELECT md5(COALESCE(string_agg(jsonb_build_object(
           'id', id, 'raw_record_id', raw_record_id, 'record_id', record_id, 'name', name,
           'gid', gid, 'route_code', route_code, 'route_name', route_name,
           'km_from', km_from, 'km_to', km_to, 'lytrinh', lytrinh,
           'province_id', province_id, 'province_name', province_name,
           'district_name', district_name, 'town_name', town_name,
           'road_class', road_class, 'road_type', road_type, 'organization_id', organization_id,
           'branch_id', branch_id, 'branch_name', branch_name, 'management_agency', management_agency,
           'state', state, 'state_name', state_name, 'level_state', level_state,
           'active_status', active_status, 'maintain_value', maintain_value,
           'construction_year', construction_year, 'parent_id', parent_id,
           'attributes', attributes, 'is_deleted', is_deleted, 'deleted_at', deleted_at,
           'deleted_by', deleted_by, 'version', version, 'created_at', created_at, 'updated_at', updated_at
       )::text, E'\n' ORDER BY id), '')) AS new_tree_preservation_checksum
FROM green_tree_record;

-- Compare these immutable raw values to the saved output from 01_preflight.sql.
SELECT COUNT(*) AS raw_dataset_record_total,
       MIN(id) AS raw_min_id,
       MAX(id) AS raw_max_id
FROM raw_dataset_record;

SELECT raw.id, raw.dataset_key, raw.record_key, md5(raw.raw_payload::text) AS raw_payload_checksum
FROM raw_dataset_record raw
WHERE EXISTS (
    SELECT 1 FROM green_tree_record tree WHERE tree.raw_record_id = raw.id
)
ORDER BY raw.id;

COMMIT;
