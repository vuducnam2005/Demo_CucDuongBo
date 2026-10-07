-- V8__gis_spatial_indices.sql
-- Optimizations, Safe Geometry Extraction, and Spatial Views for WebGIS Module

-- 1. Immutable helper function to safely extract Point geometry in WGS 84 (EPSG:4326)
CREATE OR REPLACE FUNCTION safe_point(x_val text, y_val text) 
RETURNS geometry AS $func$
DECLARE
    x double precision;
    y double precision;
BEGIN
    IF x_val IS NULL OR y_val IS NULL OR x_val = '' OR y_val = '' OR x_val = 'null' OR y_val = 'null' THEN
        RETURN NULL;
    END IF;
    x := x_val::double precision;
    y := y_val::double precision;
    -- Sanity check coordinates for Vietnam territory (Lat 8.0 to 24.0, Lon 100.0 to 115.0)
    IF x BETWEEN 100.0 AND 115.0 AND y BETWEEN 8.0 AND 24.0 THEN
        RETURN ST_SetSRID(ST_MakePoint(x, y), 4326);
    END IF;
    RETURN NULL;
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$func$ LANGUAGE plpgsql IMMUTABLE;

-- 2. GiST Spatial Index on raw_dataset_record for sub-millisecond BBOX filtering
CREATE INDEX IF NOT EXISTS idx_raw_dataset_spatial_point 
ON raw_dataset_record 
USING gist (safe_point(raw_payload->>'x_min', raw_payload->>'y_min')) 
WHERE raw_payload->>'x_min' IS NOT NULL;

-- 3. Composite Index on parent_id to accelerate sign-to-pole spatial joins
CREATE INDEX IF NOT EXISTS idx_raw_dataset_parent_id 
ON raw_dataset_record (dataset_key, (raw_payload->>'parent_id')) 
WHERE dataset_key = 'tbl_road_sign';

-- 4. Spatial View for Road Signs linking to parent Pole coordinates
CREATE OR REPLACE VIEW view_road_signs_spatial AS
SELECT 
    s.id,
    s.record_key,
    s.dataset_key,
    COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
    s.raw_payload->>'branch_id' AS branch_id,
    COALESCE(s.raw_payload->>'state_name', s.record_status, 'Đang khai thác') AS state_name,
    p.record_key AS pole_key,
    COALESCE((p.raw_payload->>'x_min')::double precision, (s.raw_payload->>'x_min')::double precision) AS lon,
    COALESCE((p.raw_payload->>'y_min')::double precision, (s.raw_payload->>'y_min')::double precision) AS lat,
    safe_point(
        COALESCE(p.raw_payload->>'x_min', s.raw_payload->>'x_min'),
        COALESCE(p.raw_payload->>'y_min', s.raw_payload->>'y_min')
    ) AS geom,
    s.raw_payload
FROM raw_dataset_record s
LEFT JOIN raw_dataset_record p 
  ON p.dataset_key = 'road_sphere_mirror' 
 AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
WHERE s.dataset_key = 'tbl_road_sign'
  AND (p.raw_payload->>'x_min' IS NOT NULL OR s.raw_payload->>'x_min' IS NOT NULL);
