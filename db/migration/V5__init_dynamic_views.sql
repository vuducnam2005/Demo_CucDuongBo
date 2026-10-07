-- ==============================================================================
-- V5: KHỞI TẠO CÁC SQL VIEW BÁO CÁO BẢO TRÌ ĐỘNG
-- ==============================================================================

-- 1. View Báo cáo Chi tiết Bảo trì Tài sản (thay thế 148 bảng maintenance_detail_*_chitiet)
CREATE OR REPLACE VIEW view_maintenance_detail_report AS
SELECT 
    ar.id AS asset_id,
    ar.record_id AS vidagis_id,
    ar.dataset_code,
    ar.asset_type,
    ar.name,
    ar.route_code,
    ar.lytrinh,
    ar.km_from,
    ar.km_to,
    ar.province_id,
    ar.province_name,
    ar.branch_id,
    ar.branch_name,
    ar.active_status,
    ar.maintain_value,
    ar.state,
    ar.state_name,
    ar.attributes
FROM asset_record ar
WHERE ar.is_deleted = FALSE;

-- 2. View Báo cáo Tổng hợp Bảo trì Tài sản (thay thế 148 bảng maintenance_detail_*_tonghop)
CREATE OR REPLACE VIEW view_maintenance_summary_report AS
SELECT 
    ar.dataset_code,
    ar.asset_type,
    ar.branch_id,
    ar.branch_name,
    ar.route_code,
    ar.province_name,
    COUNT(ar.id) AS total_count,
    SUM(COALESCE(ar.km_to - ar.km_from, 0)) AS total_length_km,
    SUM(COALESCE(ar.maintain_value, 0)) AS total_maintain_cost,
    COUNT(CASE WHEN ar.state = 'Approved' THEN 1 END) AS approved_count,
    COUNT(CASE WHEN ar.state = 'Pending' THEN 1 END) AS pending_count
FROM asset_record ar
WHERE ar.is_deleted = FALSE
GROUP BY ar.dataset_code, ar.asset_type, ar.branch_id, ar.branch_name, ar.route_code, ar.province_name;
