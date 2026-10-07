-- V7__dashboard_aggregations.sql
-- Optimizations and Materialized Views for Server-Side Dashboard Aggregations

-- 1. Index on dataset_key and branch_id for instant filtering
CREATE INDEX IF NOT EXISTS idx_raw_dataset_branch 
ON raw_dataset_record (dataset_key, (raw_payload->>'branch_id'));

-- 2. Materialized view for high-performance branch/province aggregations
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_dashboard_branch_stats AS
SELECT 
    COALESCE(raw_payload->>'branch_id', 'unassigned') AS branch_id,
    COUNT(*) AS total_assets,
    COUNT(*) FILTER (WHERE dataset_key = 'tbl_bridge') AS bridge_count,
    COUNT(*) FILTER (WHERE dataset_key = 'tbl_road_sign') AS road_sign_count,
    COUNT(*) FILTER (WHERE dataset_key = 'mst_national_road') AS national_road_count
FROM raw_dataset_record
GROUP BY 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_dashboard_branch 
ON mv_dashboard_branch_stats (branch_id);

-- 3. View for national road statistics and length calculations
CREATE OR REPLACE VIEW view_dashboard_national_road_stats AS
SELECT 
    r.record_key,
    COALESCE(r.raw_payload->>'fielddisplay', r.raw_payload->>'text', r.record_key) AS route_name,
    NULLIF(elem->>'column_value', '')::numeric AS length_km,
    r.raw_payload->>'branch_id' AS branch_id,
    r.imported_at,
    r.updated_at
FROM raw_dataset_record r,
     jsonb_array_elements((r.raw_payload->>'data_')::jsonb) elem
WHERE r.dataset_key = 'mst_national_road'
  AND elem->>'column_identify' = 'actual_length';

-- 4. Helper function to refresh materialized view concurrently
CREATE OR REPLACE FUNCTION refresh_dashboard_aggregates()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_dashboard_branch_stats;
END;
$$ LANGUAGE plpgsql;
