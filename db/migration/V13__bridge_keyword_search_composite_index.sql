-- Composite trigram expression matching the bridge keyword predicate in DatasetQueryService.
CREATE INDEX IF NOT EXISTS idx_raw_bridge_search_text_trgm
    ON raw_dataset_record USING gin (
        (coalesce(record_key, '') || ' ' || coalesce(raw_payload->>'name', '') || ' ' || coalesce(raw_payload->>'fielddisplay', '') || ' ' || coalesce(raw_payload->>'text', '') || ' ' || coalesce(raw_payload->>'route_name', '')) gin_trgm_ops
    )
    WHERE dataset_key = 'tbl_bridge';

ANALYZE raw_dataset_record;
