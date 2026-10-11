-- Separate green-tree management from the general asset catalogue while
-- preserving the original VroadAI raw records for lineage and map features.

INSERT INTO dataset_registry (
    dataset_key, dataset_name, kind, endpoint, source_file, total_records, is_active
)
VALUES
    ('green_trees', 'Cây xanh', 'asset', '/api/datasets/green_trees', 'managed/green_trees', 0, TRUE),
    ('other_assets', 'Tài sản khác', 'asset', '/api/datasets/other_assets', 'managed/other_assets', 0, TRUE)
ON CONFLICT (dataset_key) DO UPDATE
SET dataset_name = EXCLUDED.dataset_name,
    kind = EXCLUDED.kind,
    endpoint = EXCLUDED.endpoint,
    source_file = EXCLUDED.source_file,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

WITH field_def(dataset_key, field_name, field_alias, data_type, searchable, filterable) AS (
    VALUES
        ('green_trees', 'name', 'Tên cây / Nhãn quản lý', 'varchar', TRUE, FALSE),
        ('green_trees', 'tree_code', 'Mã cây', 'varchar', TRUE, TRUE),
        ('green_trees', 'species_name', 'Tên loài cây', 'varchar', TRUE, TRUE),
        ('green_trees', 'scientific_name', 'Tên khoa học', 'varchar', TRUE, FALSE),
        ('green_trees', 'route_code', 'Mã tuyến', 'varchar', TRUE, TRUE),
        ('green_trees', 'route_name', 'Tên tuyến', 'varchar', TRUE, TRUE),
        ('green_trees', 'lytrinh', 'Lý trình', 'varchar', TRUE, TRUE),
        ('green_trees', 'branch_id', 'Đơn vị quản lý', 'varchar', FALSE, TRUE),
        ('green_trees', 'province_name', 'Tỉnh / Thành phố', 'varchar', TRUE, TRUE),
        ('green_trees', 'district_name', 'Quận / Huyện', 'varchar', TRUE, TRUE),
        ('green_trees', 'town_name', 'Xã / Phường', 'varchar', TRUE, TRUE),
        ('green_trees', 'diameter_cm', 'Đường kính thân (cm)', 'numeric', FALSE, FALSE),
        ('green_trees', 'height_m', 'Chiều cao (m)', 'numeric', FALSE, FALSE),
        ('green_trees', 'canopy_diameter_m', 'Đường kính tán (m)', 'numeric', FALSE, FALSE),
        ('green_trees', 'planting_year', 'Năm trồng', 'integer', FALSE, TRUE),
        ('green_trees', 'health_status', 'Tình trạng sinh trưởng', 'varchar', TRUE, TRUE),
        ('green_trees', 'risk_level', 'Mức độ rủi ro', 'varchar', TRUE, TRUE),
        ('green_trees', 'longitude', 'Kinh độ', 'numeric', FALSE, FALSE),
        ('green_trees', 'latitude', 'Vĩ độ', 'numeric', FALSE, FALSE),
        ('green_trees', 'notes', 'Ghi chú', 'text', TRUE, FALSE),
        ('other_assets', 'name', 'Tên tài sản', 'varchar', TRUE, FALSE),
        ('other_assets', 'asset_category', 'Nhóm tài sản', 'varchar', TRUE, TRUE),
        ('other_assets', 'asset_type_name', 'Loại tài sản', 'varchar', TRUE, TRUE),
        ('other_assets', 'description', 'Mô tả', 'text', TRUE, FALSE),
        ('other_assets', 'route_code', 'Mã tuyến', 'varchar', TRUE, TRUE),
        ('other_assets', 'route_name', 'Tên tuyến', 'varchar', TRUE, TRUE),
        ('other_assets', 'lytrinh', 'Lý trình', 'varchar', TRUE, TRUE),
        ('other_assets', 'branch_id', 'Đơn vị quản lý', 'varchar', FALSE, TRUE),
        ('other_assets', 'province_name', 'Tỉnh / Thành phố', 'varchar', TRUE, TRUE),
        ('other_assets', 'district_name', 'Quận / Huyện', 'varchar', TRUE, TRUE),
        ('other_assets', 'town_name', 'Xã / Phường', 'varchar', TRUE, TRUE),
        ('other_assets', 'quantity', 'Số lượng', 'numeric', FALSE, FALSE),
        ('other_assets', 'unit', 'Đơn vị tính', 'varchar', FALSE, TRUE),
        ('other_assets', 'active_status', 'Tình trạng khai thác', 'varchar', TRUE, TRUE),
        ('other_assets', 'construction_year', 'Năm đưa vào sử dụng', 'integer', FALSE, TRUE),
        ('other_assets', 'maintain_value', 'Kinh phí bảo trì', 'numeric', FALSE, FALSE),
        ('other_assets', 'notes', 'Ghi chú', 'text', TRUE, FALSE)
)
INSERT INTO dataset_field (
    dataset_id, field_name, field_alias, data_type, is_searchable, is_filter
)
SELECT registry.id, field_def.field_name, field_def.field_alias, field_def.data_type,
       field_def.searchable, field_def.filterable
FROM field_def
JOIN dataset_registry registry ON registry.dataset_key = field_def.dataset_key
ON CONFLICT (dataset_id, field_name) DO UPDATE
SET field_alias = EXCLUDED.field_alias,
    data_type = EXCLUDED.data_type,
    is_searchable = EXCLUDED.is_searchable,
    is_filter = EXCLUDED.is_filter;

-- Reclassify already-curated tree records first so their optimistic-lock history is retained.
UPDATE asset_record asset
SET dataset_code = 'green_trees',
    asset_type = 'TREE',
    updated_at = CURRENT_TIMESTAMP
FROM raw_dataset_record raw
WHERE asset.raw_record_id = raw.id
  AND raw.dataset_key = 'vroad_assets'
  AND (
      lower(trim(COALESCE(raw.raw_payload->>'asset_type', ''))) IN
          ('cây', 'cây xanh', 'cay', 'cay xanh', 'tree', 'green tree')
      OR lower(trim(COALESCE(raw.raw_payload->>'category', ''))) IN
          ('cây', 'cây xanh', 'cay', 'cay xanh', 'tree', 'green tree')
  );

-- Create operational green-tree records from the immutable VroadAI source rows.
INSERT INTO asset_record (
    raw_record_id, record_id, dataset_code, asset_type, name,
    route_code, route_name, lytrinh, branch_id, branch_name,
    province_name, district_name, town_name, active_status,
    attributes, is_deleted, version, created_at, updated_at
)
SELECT raw.id,
       CASE
           WHEN EXISTS (SELECT 1 FROM asset_record existing WHERE existing.record_id = raw.record_key)
               THEN 'green_tree_' || raw.id
           ELSE raw.record_key
       END,
       'green_trees',
       'TREE',
       COALESCE(
           NULLIF(raw.raw_payload->>'name', ''),
           NULLIF(raw.raw_payload->>'fielddisplay', ''),
           NULLIF(raw.raw_payload->>'species_name', ''),
           NULLIF(raw.raw_payload->>'asset_type', ''),
           'Cây xanh ' || raw.record_key
       ),
       NULLIF(raw.raw_payload->>'route_code', ''),
       NULLIF(raw.raw_payload->>'route_name', ''),
       COALESCE(NULLIF(raw.raw_payload->>'lytrinh', ''), NULLIF(raw.raw_payload->>'chainage', '')),
       NULLIF(raw.raw_payload->>'branch_id', ''),
       NULLIF(raw.raw_payload->>'branch_name', ''),
       NULLIF(raw.raw_payload->>'province_name', ''),
       NULLIF(raw.raw_payload->>'district_name', ''),
       NULLIF(raw.raw_payload->>'town_name', ''),
       COALESCE(NULLIF(raw.raw_payload->>'active_status', ''), NULLIF(raw.raw_payload->>'condition', ''), 'Đang quản lý'),
       raw.raw_payload || jsonb_build_object(
           'source_dataset', raw.dataset_key,
           'source_record_key', raw.record_key
       ),
       FALSE,
       1,
       raw.imported_at,
       raw.updated_at
FROM raw_dataset_record raw
WHERE raw.dataset_key = 'vroad_assets'
  AND (
      lower(trim(COALESCE(raw.raw_payload->>'asset_type', ''))) IN
          ('cây', 'cây xanh', 'cay', 'cay xanh', 'tree', 'green tree')
      OR lower(trim(COALESCE(raw.raw_payload->>'category', ''))) IN
          ('cây', 'cây xanh', 'cay', 'cay xanh', 'tree', 'green tree')
  )
  AND NOT EXISTS (
      SELECT 1
      FROM asset_record existing
      WHERE existing.raw_record_id = raw.id
         OR (existing.dataset_code = 'green_trees' AND existing.record_id = raw.record_key)
  );

UPDATE dataset_registry registry
SET total_records = (
        SELECT COUNT(*)
        FROM asset_record asset
        WHERE asset.dataset_code = registry.dataset_key
          AND asset.is_deleted = FALSE
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE registry.dataset_key IN ('green_trees', 'other_assets');
