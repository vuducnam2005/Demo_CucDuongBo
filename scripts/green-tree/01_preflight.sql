\set ON_ERROR_STOP on

-- Run with psql against the target Supabase/staging database before V23/V24.
-- Save the complete output as deployment evidence. This script is read-only.
BEGIN TRANSACTION READ ONLY;

SELECT current_database() AS database_name,
       current_user AS database_user,
       current_setting('server_version') AS postgres_version,
       now() AS captured_at;

WITH trees AS (
    SELECT *
    FROM asset_record
    WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
)
SELECT COUNT(*) AS total_trees,
       COUNT(*) FILTER (WHERE is_deleted = FALSE) AS active_trees,
       COUNT(*) FILTER (WHERE is_deleted = TRUE) AS deleted_trees,
       COUNT(DISTINCT record_id) AS distinct_record_ids,
       COUNT(DISTINCT raw_record_id) AS distinct_non_null_raw_record_ids,
       COUNT(*) FILTER (WHERE raw_record_id IS NULL) AS trees_without_raw_record,
       MIN(id) AS min_id,
       MAX(id) AS max_id,
       MIN(created_at) AS min_created_at,
       MAX(created_at) AS max_created_at,
       MIN(updated_at) AS min_updated_at,
       MAX(updated_at) AS max_updated_at
FROM trees;

SELECT record_id, COUNT(*) AS duplicate_count, array_agg(id ORDER BY id) AS ids
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
GROUP BY record_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, record_id;

SELECT raw_record_id, COUNT(*) AS duplicate_count, array_agg(id ORDER BY id) AS ids
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE' AND raw_record_id IS NOT NULL
GROUP BY raw_record_id
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, raw_record_id;

SELECT version, COUNT(*) AS tree_count
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
GROUP BY version
ORDER BY version;

SELECT COALESCE(branch_id, '<NULL>') AS branch_id, COUNT(*) AS tree_count
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
GROUP BY branch_id
ORDER BY tree_count DESC, branch_id;

WITH trees AS (
    SELECT asset.*, raw.raw_payload, geometry.point_geom, geometry.geom
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    LEFT JOIN asset_geometry geometry ON geometry.asset_id = asset.id
    WHERE asset.dataset_code = 'green_trees' AND asset.asset_type = 'TREE'
), coordinate_flags AS (
    SELECT *,
           point_geom IS NOT NULL
               AND ST_IsValid(point_geom)
               AND ST_X(point_geom) BETWEEN -180 AND 180
               AND ST_Y(point_geom) BETWEEN -90 AND 90 AS valid_point_geom,
           (CASE WHEN geom IS NOT NULL AND GeometryType(geom) = 'POINT' AND ST_IsValid(geom)
                 THEN ST_X(geom) END) BETWEEN -180 AND 180
               AND (CASE WHEN geom IS NOT NULL AND GeometryType(geom) = 'POINT' AND ST_IsValid(geom)
                         THEN ST_Y(geom) END) BETWEEN -90 AND 90 AS valid_generic_geom,
           (CASE WHEN attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (attributes->>'longitude')::double precision END) BETWEEN -180 AND 180
               AND (CASE WHEN attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                         THEN (attributes->>'latitude')::double precision END) BETWEEN -90 AND 90
               AS valid_attribute_coordinates,
           (CASE WHEN raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                 THEN (raw_payload->>'longitude')::double precision END) BETWEEN -180 AND 180
               AND (CASE WHEN raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                         THEN (raw_payload->>'latitude')::double precision END) BETWEEN -90 AND 90
               AS valid_raw_coordinates
    FROM trees
)
SELECT COUNT(*) FILTER (WHERE valid_point_geom) AS with_asset_point_geom,
       COUNT(*) FILTER (WHERE valid_generic_geom) AS with_asset_generic_point,
       COUNT(*) FILTER (WHERE valid_attribute_coordinates) AS with_attribute_coordinates,
       COUNT(*) FILTER (WHERE valid_raw_coordinates) AS with_raw_coordinates,
       COUNT(*) FILTER (WHERE attributes ? 'longitude' OR attributes ? 'latitude') AS with_attribute_coordinate_keys,
       COUNT(*) FILTER (WHERE (attributes ? 'longitude' OR attributes ? 'latitude')
                          AND NOT COALESCE(valid_attribute_coordinates, FALSE)) AS attribute_tombstones_or_invalid_pairs,
       COUNT(*) FILTER (WHERE NOT COALESCE(valid_point_geom, FALSE)
                          AND NOT COALESCE(valid_generic_geom, FALSE)
                          AND NOT COALESCE(valid_attribute_coordinates, FALSE)
                          AND NOT COALESCE(valid_raw_coordinates, FALSE)) AS without_any_valid_coordinates
FROM coordinate_flags;

WITH trees AS (
    SELECT id, record_id, attributes
    FROM asset_record
    WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
)
SELECT id, record_id,
       attributes->'longitude' AS longitude,
       attributes->'latitude' AS latitude
FROM trees
WHERE (attributes ? 'longitude' OR attributes ? 'latitude')
  AND (
      (CASE WHEN attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
            THEN (attributes->>'longitude')::double precision END) BETWEEN -180 AND 180
      AND (CASE WHEN attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                THEN (attributes->>'latitude')::double precision END) BETWEEN -90 AND 90
  ) IS NOT TRUE
ORDER BY id;

SELECT asset.id, asset.record_id, measurement.field_name, measurement.raw_value
FROM asset_record asset
LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
CROSS JOIN LATERAL (VALUES
    ('diameter_cm', COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm')),
    ('height_m', COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m')),
    ('canopy_diameter_m', COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m'))
) measurement(field_name, raw_value)
WHERE asset.dataset_code = 'green_trees'
  AND asset.asset_type = 'TREE'
  AND measurement.raw_value IS NOT NULL
  AND NOT (
      measurement.raw_value ~ '^[+]?[0-9]+([.][0-9]+)?$'
      AND CASE WHEN measurement.raw_value ~ '^[+]?[0-9]+([.][0-9]+)?$'
               THEN measurement.raw_value::numeric >= 0 ELSE FALSE END
  )
ORDER BY asset.id, measurement.field_name;

WITH source_keys AS (
    SELECT asset.id,
           COALESCE(NULLIF(asset.attributes->>'source_record_key', ''), raw.record_key) AS source_record_key
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    WHERE asset.dataset_code = 'green_trees' AND asset.asset_type = 'TREE'
)
SELECT COUNT(DISTINCT source_keys.id) AS trees_with_images
FROM source_keys
JOIN vroad_image_reference image
  ON image.dataset_key = 'vroad_assets'
 AND image.record_key = source_keys.source_record_key;

SELECT COUNT(DISTINCT asset.id) AS trees_with_documents
FROM asset_record asset
JOIN document_metadata document ON document.asset_record_id = asset.record_id
WHERE asset.dataset_code = 'green_trees'
  AND asset.asset_type = 'TREE'
  AND document.is_deleted = FALSE;

SELECT COUNT(*) AS related_audit_rows
FROM audit_log audit
WHERE audit.entity_id IN (
          SELECT record_id
          FROM asset_record
          WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
      )
   OR audit.entity_id IN (
          SELECT id::text
          FROM asset_record
          WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
      );

-- Overall canonical checksum. jsonb preserves nulls and emits keys in canonical order.
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
       )::text, E'\n' ORDER BY id), '')) AS legacy_preservation_checksum
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE';

SELECT id, record_id, md5(attributes::text) AS attributes_checksum
FROM asset_record
WHERE dataset_code = 'green_trees' AND asset_type = 'TREE'
ORDER BY id;

SELECT raw.id, raw.dataset_key, raw.record_key, md5(raw.raw_payload::text) AS raw_payload_checksum
FROM raw_dataset_record raw
WHERE EXISTS (
    SELECT 1
    FROM asset_record asset
    WHERE asset.raw_record_id = raw.id
      AND asset.dataset_code = 'green_trees'
      AND asset.asset_type = 'TREE'
)
ORDER BY raw.id;

SELECT COUNT(*) AS raw_dataset_record_total,
       MIN(id) AS raw_min_id,
       MAX(id) AS raw_max_id
FROM raw_dataset_record;

COMMIT;
