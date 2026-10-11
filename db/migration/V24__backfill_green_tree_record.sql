-- Backfill phase: copy every active and soft-deleted green tree without mutating legacy or raw data.
WITH source_rows AS (
    SELECT
        asset.id,
        asset.raw_record_id,
        asset.record_id,
        asset.gid,
        asset.name,
        COALESCE(NULLIF(asset.attributes->>'tree_code', ''), NULLIF(raw.raw_payload->>'tree_code', '')) AS tree_code,
        COALESCE(NULLIF(asset.attributes->>'species_name', ''), NULLIF(raw.raw_payload->>'species_name', '')) AS species_name,
        COALESCE(NULLIF(asset.attributes->>'scientific_name', ''), NULLIF(raw.raw_payload->>'scientific_name', '')) AS scientific_name,
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
        CASE
            WHEN COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm') ~ '^[+]?[0-9]+([.][0-9]+)?$'
                THEN CASE
                    WHEN (COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm'))::numeric >= 0
                        THEN (COALESCE(asset.attributes->>'diameter_cm', raw.raw_payload->>'diameter_cm'))::numeric
                END
        END AS diameter_cm,
        CASE
            WHEN COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m') ~ '^[+]?[0-9]+([.][0-9]+)?$'
                THEN CASE
                    WHEN (COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m'))::numeric >= 0
                        THEN (COALESCE(asset.attributes->>'height_m', raw.raw_payload->>'height_m'))::numeric
                END
        END AS height_m,
        CASE
            WHEN COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m') ~ '^[+]?[0-9]+([.][0-9]+)?$'
                THEN CASE
                    WHEN (COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m'))::numeric >= 0
                        THEN (COALESCE(asset.attributes->>'canopy_diameter_m', raw.raw_payload->>'canopy_diameter_m'))::numeric
                END
        END AS canopy_diameter_m,
        CASE
            WHEN COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year') ~ '^-?[0-9]+$'
                THEN CASE
                    WHEN (COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year'))::numeric
                            BETWEEN -2147483648 AND 2147483647
                        THEN (COALESCE(asset.attributes->>'planting_year', raw.raw_payload->>'planting_year'))::integer
                END
        END AS planting_year,
        COALESCE(
            NULLIF(asset.attributes->>'health_status', ''),
            NULLIF(asset.attributes->>'condition', ''),
            NULLIF(raw.raw_payload->>'health_status', ''),
            NULLIF(raw.raw_payload->>'condition', '')
        ) AS health_status,
        COALESCE(NULLIF(asset.attributes->>'risk_level', ''), NULLIF(raw.raw_payload->>'risk_level', '')) AS risk_level,
        COALESCE(NULLIF(asset.attributes->>'notes', ''), NULLIF(raw.raw_payload->>'notes', '')) AS notes,
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
                    (asset.attributes->>'latitude')::double precision
                ), 4326)
            WHEN asset.attributes ? 'longitude' OR asset.attributes ? 'latitude'
                THEN NULL
            WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                       THEN (raw.raw_payload->>'longitude')::double precision END) BETWEEN -180 AND 180
             AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                       THEN (raw.raw_payload->>'latitude')::double precision END) BETWEEN -90 AND 90
                THEN ST_SetSRID(ST_MakePoint(
                    (raw.raw_payload->>'longitude')::double precision,
                    (raw.raw_payload->>'latitude')::double precision
                ), 4326)
        END AS geom,
        asset.attributes,
        asset.is_deleted,
        asset.deleted_at,
        asset.deleted_by,
        asset.version,
        asset.created_at,
        asset.updated_at
    FROM asset_record asset
    LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
    LEFT JOIN asset_geometry geometry ON geometry.asset_id = asset.id
    WHERE asset.dataset_code = 'green_trees'
      AND asset.asset_type = 'TREE'
)
INSERT INTO green_tree_record (
    id, raw_record_id, record_id, gid, name, tree_code, species_name, scientific_name,
    route_code, route_name, km_from, km_to, lytrinh,
    province_id, province_name, district_name, town_name, road_class, road_type,
    organization_id, branch_id, branch_name, management_agency,
    state, state_name, level_state, active_status, maintain_value, construction_year, parent_id,
    diameter_cm, height_m, canopy_diameter_m, planting_year,
    health_status, risk_level, notes, geom, attributes,
    is_deleted, deleted_at, deleted_by, version, created_at, updated_at
)
SELECT
    id, raw_record_id, record_id, gid, name, tree_code, species_name, scientific_name,
    route_code, route_name, km_from, km_to, lytrinh,
    province_id, province_name, district_name, town_name, road_class, road_type,
    organization_id, branch_id, branch_name, management_agency,
    state, state_name, level_state, active_status, maintain_value, construction_year, parent_id,
    diameter_cm, height_m, canopy_diameter_m, planting_year,
    health_status, risk_level, notes, geom, attributes,
    is_deleted, deleted_at, deleted_by, version, created_at, updated_at
FROM source_rows
ON CONFLICT (id) DO UPDATE SET
    raw_record_id = EXCLUDED.raw_record_id,
    record_id = EXCLUDED.record_id,
    gid = EXCLUDED.gid,
    name = EXCLUDED.name,
    tree_code = EXCLUDED.tree_code,
    species_name = EXCLUDED.species_name,
    scientific_name = EXCLUDED.scientific_name,
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
    diameter_cm = EXCLUDED.diameter_cm,
    height_m = EXCLUDED.height_m,
    canopy_diameter_m = EXCLUDED.canopy_diameter_m,
    planting_year = EXCLUDED.planting_year,
    health_status = EXCLUDED.health_status,
    risk_level = EXCLUDED.risk_level,
    notes = EXCLUDED.notes,
    geom = EXCLUDED.geom,
    attributes = EXCLUDED.attributes,
    is_deleted = EXCLUDED.is_deleted,
    deleted_at = EXCLUDED.deleted_at,
    deleted_by = EXCLUDED.deleted_by,
    version = EXCLUDED.version,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

SELECT setval(
    pg_get_serial_sequence('green_tree_record', 'id'),
    COALESCE((SELECT MAX(id) FROM green_tree_record), 1),
    EXISTS (SELECT 1 FROM green_tree_record)
);

UPDATE dataset_registry
SET total_records = (
        SELECT COUNT(*)
        FROM green_tree_record
        WHERE is_deleted = FALSE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE dataset_key = 'green_trees';

