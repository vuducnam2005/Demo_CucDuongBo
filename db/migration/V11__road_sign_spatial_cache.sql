-- Keep a narrow, transactionally maintained projection for road-sign map queries.
-- The source JSONB rows are intentionally wide; joining two copies for every map
-- request caused hash spills and 15-30 second cluster response times.

CREATE TABLE IF NOT EXISTS gis_road_sign_point_cache (
    record_key VARCHAR(150) PRIMARY KEY,
    sign_name TEXT NOT NULL,
    branch_id TEXT,
    state_name TEXT,
    route_code TEXT,
    route_name TEXT,
    pole_key VARCHAR(150),
    lon DOUBLE PRECISION NOT NULL,
    lat DOUBLE PRECISION NOT NULL,
    geom geometry(Point, 4326) NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_geom
    ON gis_road_sign_point_cache USING gist (geom);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_branch
    ON gis_road_sign_point_cache (branch_id);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_state
    ON gis_road_sign_point_cache (state_name);
CREATE INDEX IF NOT EXISTS idx_gis_road_sign_cache_route
    ON gis_road_sign_point_cache (route_code);

CREATE INDEX IF NOT EXISTS idx_raw_road_sign_normalized_parent
    ON raw_dataset_record (
        dataset_key,
        regexp_replace(raw_payload->>'parent_id', '_tbl_road_sign$', '')
    )
    WHERE dataset_key = 'tbl_road_sign';

CREATE OR REPLACE FUNCTION kcht_upsert_road_sign_point(p_record_key VARCHAR)
RETURNS VOID AS $$
BEGIN
    DELETE FROM gis_road_sign_point_cache WHERE record_key = p_record_key;

    INSERT INTO gis_road_sign_point_cache (
        record_key, sign_name, branch_id, state_name, route_code, route_name, pole_key,
        lon, lat, geom, refreshed_at
    )
    SELECT
        resolved.record_key,
        resolved.sign_name,
        resolved.branch_id,
        resolved.state_name,
        resolved.route_code,
        resolved.route_name,
        resolved.pole_key,
        ST_X(resolved.geom),
        ST_Y(resolved.geom),
        resolved.geom,
        CURRENT_TIMESTAMP
    FROM (
        SELECT
            s.record_key,
            COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
            s.raw_payload->>'branch_id' AS branch_id,
            COALESCE(s.raw_payload->>'state_name', s.record_status) AS state_name,
            COALESCE(s.raw_payload->>'route_code', s.raw_payload->>'route') AS route_code,
            s.raw_payload->>'route_name' AS route_name,
            regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') AS pole_key,
            COALESCE(
                safe_point(p.raw_payload->>'x_min', p.raw_payload->>'y_min'),
                safe_point(s.raw_payload->>'x_min', s.raw_payload->>'y_min')
            ) AS geom
        FROM raw_dataset_record s
        LEFT JOIN raw_dataset_record p
          ON p.dataset_key = 'road_sphere_mirror'
         AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
        WHERE s.dataset_key = 'tbl_road_sign'
          AND s.record_key = p_record_key
    ) resolved
    WHERE resolved.geom IS NOT NULL;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION kcht_sync_road_sign_point_cache()
RETURNS TRIGGER AS $$
DECLARE
    affected_key VARCHAR(150);
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.dataset_key = 'tbl_road_sign' THEN
        PERFORM kcht_upsert_road_sign_point(OLD.record_key);
    END IF;

    IF TG_OP <> 'DELETE' AND NEW.dataset_key = 'tbl_road_sign' THEN
        PERFORM kcht_upsert_road_sign_point(NEW.record_key);
    END IF;

    IF TG_OP <> 'INSERT' AND OLD.dataset_key = 'road_sphere_mirror' THEN
        FOR affected_key IN
            SELECT s.record_key
            FROM raw_dataset_record s
            WHERE s.dataset_key = 'tbl_road_sign'
              AND regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') = OLD.record_key
        LOOP
            PERFORM kcht_upsert_road_sign_point(affected_key);
        END LOOP;
    END IF;

    IF TG_OP <> 'DELETE' AND NEW.dataset_key = 'road_sphere_mirror' THEN
        FOR affected_key IN
            SELECT s.record_key
            FROM raw_dataset_record s
            WHERE s.dataset_key = 'tbl_road_sign'
              AND regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') = NEW.record_key
        LOOP
            PERFORM kcht_upsert_road_sign_point(affected_key);
        END LOOP;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_road_sign_point_cache_write ON raw_dataset_record;
CREATE TRIGGER trg_sync_road_sign_point_cache_write
AFTER INSERT OR UPDATE OF raw_payload, record_status, record_key
ON raw_dataset_record
FOR EACH ROW
WHEN (NEW.dataset_key IN ('tbl_road_sign', 'road_sphere_mirror'))
EXECUTE FUNCTION kcht_sync_road_sign_point_cache();

DROP TRIGGER IF EXISTS trg_sync_road_sign_point_cache_delete ON raw_dataset_record;
CREATE TRIGGER trg_sync_road_sign_point_cache_delete
AFTER DELETE ON raw_dataset_record
FOR EACH ROW
WHEN (OLD.dataset_key IN ('tbl_road_sign', 'road_sphere_mirror'))
EXECUTE FUNCTION kcht_sync_road_sign_point_cache();

INSERT INTO gis_road_sign_point_cache (
    record_key, sign_name, branch_id, state_name, route_code, route_name, pole_key,
    lon, lat, geom, refreshed_at
)
SELECT
    resolved.record_key,
    resolved.sign_name,
    resolved.branch_id,
    resolved.state_name,
    resolved.route_code,
    resolved.route_name,
    resolved.pole_key,
    ST_X(resolved.geom),
    ST_Y(resolved.geom),
    resolved.geom,
    CURRENT_TIMESTAMP
FROM (
    SELECT
        s.record_key,
        COALESCE(s.raw_payload->>'fielddisplay', s.raw_payload->>'text', s.record_key) AS sign_name,
        s.raw_payload->>'branch_id' AS branch_id,
        COALESCE(s.raw_payload->>'state_name', s.record_status) AS state_name,
        COALESCE(s.raw_payload->>'route_code', s.raw_payload->>'route') AS route_code,
        s.raw_payload->>'route_name' AS route_name,
        regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '') AS pole_key,
        COALESCE(
            safe_point(p.raw_payload->>'x_min', p.raw_payload->>'y_min'),
            safe_point(s.raw_payload->>'x_min', s.raw_payload->>'y_min')
        ) AS geom
    FROM raw_dataset_record s
    LEFT JOIN raw_dataset_record p
      ON p.dataset_key = 'road_sphere_mirror'
     AND p.record_key = regexp_replace(s.raw_payload->>'parent_id', '_tbl_road_sign$', '')
    WHERE s.dataset_key = 'tbl_road_sign'
) resolved
WHERE resolved.geom IS NOT NULL
ON CONFLICT (record_key) DO UPDATE SET
    sign_name = EXCLUDED.sign_name,
    branch_id = EXCLUDED.branch_id,
    state_name = EXCLUDED.state_name,
    route_code = EXCLUDED.route_code,
    route_name = EXCLUDED.route_name,
    pole_key = EXCLUDED.pole_key,
    lon = EXCLUDED.lon,
    lat = EXCLUDED.lat,
    geom = EXCLUDED.geom,
    refreshed_at = EXCLUDED.refreshed_at;

ANALYZE gis_road_sign_point_cache;
