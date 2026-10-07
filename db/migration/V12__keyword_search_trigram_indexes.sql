-- Tăng tốc tìm kiếm mờ đúng theo các trường mà DatasetQueryService expose qua q/keyword.
-- Các index có điều kiện chỉ áp dụng cho dataset cầu đường bộ lớn.
CREATE INDEX IF NOT EXISTS idx_raw_bridge_record_key_trgm
    ON raw_dataset_record USING gin (record_key gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_fielddisplay_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'fielddisplay') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_text_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'text') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

CREATE INDEX IF NOT EXISTS idx_raw_bridge_route_name_trgm
    ON raw_dataset_record USING gin ((raw_payload->>'route_name') gin_trgm_ops)
    WHERE dataset_key = 'tbl_bridge';

ANALYZE raw_dataset_record;
