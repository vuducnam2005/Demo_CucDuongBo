package vn.gov.drvn.kcht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.dto.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final JdbcClient jdbcClient;

    public DashboardService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @org.springframework.cache.annotation.CacheEvict(value = "dashboardSummary", allEntries = true)
    public void evictCache() {
        log.info("Xóa cache dashboard summary");
    }

    @Cacheable(value = "dashboardSummary", key = "'summary'")
    public DashboardSummaryDto getSummary() {
        log.debug("Lấy thống kê tổng quan dashboard...");

        // 1. Thống kê từ dataset_registry
        String regSql = """
            SELECT 
                COUNT(*) AS total_datasets,
                COUNT(*) FILTER (WHERE kind = 'asset') AS physical_datasets,
                COUNT(*) FILTER (WHERE kind = 'module') AS module_datasets,
                COALESCE(SUM(total_records) FILTER (WHERE kind = 'asset'), 0) AS total_assets,
                COALESCE(MAX(total_records) FILTER (WHERE dataset_key = 'tbl_bridge'), 0) AS total_bridges,
                COALESCE(MAX(total_records) FILTER (WHERE dataset_key = 'tbl_road_sign'), 0) AS total_road_signs
            FROM dataset_registry;
        """;

        Map<String, Object> regMap = jdbcClient.sql(regSql).query().singleRow();

        int totalDatasets = ((Number) regMap.get("total_datasets")).intValue();
        int physicalDatasets = ((Number) regMap.get("physical_datasets")).intValue();
        int moduleDatasets = ((Number) regMap.get("module_datasets")).intValue();
        long totalAssets = ((Number) regMap.get("total_assets")).longValue();
        long totalBridges = ((Number) regMap.get("total_bridges")).longValue();
        long totalRoadSigns = ((Number) regMap.get("total_road_signs")).longValue();

        // 2. Thống kê chiều dài quốc lộ từ view_dashboard_national_road_stats
        String roadSql = """
            SELECT 
                COUNT(*) AS total_routes,
                COALESCE(ROUND(SUM(length_km), 2), 0) AS total_length
            FROM view_dashboard_national_road_stats;
        """;

        Map<String, Object> roadMap = jdbcClient.sql(roadSql).query().singleRow();
        int totalRoutes = ((Number) roadMap.get("total_routes")).intValue();
        double totalLength = ((Number) roadMap.get("total_length")).doubleValue();

        // 3. Thống kê tài liệu hồ sơ
        String docSql = "SELECT COUNT(*) FROM document_metadata;";
        long totalDocs = jdbcClient.sql(docSql).query(Long.class).single();

        return new DashboardSummaryDto(
            totalAssets,
            totalDatasets,
            physicalDatasets,
            moduleDatasets,
            totalBridges,
            totalRoadSigns,
            totalRoutes,
            totalLength,
            totalDocs,
            OffsetDateTime.now()
        );
    }

    @Cacheable(value = "dashboardBranchStats", key = "'branchStats'")
    public List<DashboardBranchStatDto> getBranchStats() {
        log.debug("Lấy thống kê theo chi nhánh / đơn vị...");
        String sql = """
            SELECT 
                branch_id,
                total_assets,
                bridge_count,
                road_sign_count,
                national_road_count
            FROM mv_dashboard_branch_stats
            WHERE branch_id != 'unassigned'
            ORDER BY total_assets DESC
            LIMIT 15;
        """;

        return jdbcClient.sql(sql)
            .query((rs, rowNum) -> {
                String branchId = rs.getString("branch_id");
                return new DashboardBranchStatDto(
                    branchId,
                    resolveBranchName(branchId),
                    rs.getLong("total_assets"),
                    rs.getLong("bridge_count"),
                    rs.getLong("road_sign_count"),
                    rs.getLong("national_road_count"),
                    OffsetDateTime.now()
                );
            })
            .list();
    }

    @Cacheable(value = "dashboardTopDatasets", key = "'topDatasets'")
    public List<DashboardDatasetStatDto> getTopDatasets() {
        log.debug("Lấy danh sách các tập dữ liệu tài sản lớn nhất...");
        String sql = """
            SELECT 
                dataset_key,
                dataset_name,
                kind,
                total_records,
                source_file
            FROM dataset_registry
            WHERE kind = 'asset'
            ORDER BY total_records DESC
            LIMIT 10;
        """;

        return jdbcClient.sql(sql)
            .query((rs, rowNum) -> new DashboardDatasetStatDto(
                rs.getString("dataset_key"),
                rs.getString("dataset_name"),
                rs.getString("kind"),
                rs.getLong("total_records"),
                rs.getString("source_file"),
                OffsetDateTime.now()
            ))
            .list();
    }

    @Cacheable(value = "dashboardRoadSigns", key = "'roadSignStats'")
    public DashboardRoadSignStatDto getRoadSignStats() {
        log.debug("Lấy thống kê chuyên đề biển báo đường bộ...");
        // 1. Phân bổ theo chi nhánh
        String branchSql = """
            SELECT 
                branch_id,
                road_sign_count
            FROM mv_dashboard_branch_stats
            WHERE road_sign_count > 0 AND branch_id != 'unassigned'
            ORDER BY road_sign_count DESC
            LIMIT 8;
        """;

        long totalRoadSigns = jdbcClient.sql("SELECT COUNT(*) FROM raw_dataset_record WHERE dataset_key = 'tbl_road_sign'")
            .query(Long.class).single();
        List<DashboardRoadSignStatDto.BranchSignCountDto> byBranch = jdbcClient.sql(branchSql)
            .query((rs, rowNum) -> {
                String branchId = rs.getString("branch_id");
                long count = rs.getLong("road_sign_count");
                double pct = totalRoadSigns == 0 ? 0 : Math.round((double) count * 1000.0 / totalRoadSigns) / 10.0;
                return new DashboardRoadSignStatDto.BranchSignCountDto(
                    branchId,
                    resolveBranchName(branchId),
                    count,
                    pct
                );
            })
            .list();

        // 2. Phân loại nhóm biển báo theo QCVN 41:2019/BGTVT
        List<DashboardRoadSignStatDto.SignCategoryDto> byShape = List.of();

        return new DashboardRoadSignStatDto(
            totalRoadSigns,
            byBranch,
            byShape,
            OffsetDateTime.now()
        );
    }

    @Cacheable(value = "dashboardRoadLengths", key = "'roadLengthStats'")
    public DashboardRoadLengthStatDto getRoadLengthStats() {
        log.debug("Lấy thống kê chiều dài quốc lộ...");
        // 1. Top 10 tuyến đường dài nhất
        String topRoutesSql = """
            SELECT 
                record_key,
                route_name,
                ROUND(length_km, 2) AS length_km
            FROM view_dashboard_national_road_stats
            ORDER BY length_km DESC
            LIMIT 10;
        """;

        List<DashboardRoadLengthStatDto.RouteLengthDto> longestRoutes = jdbcClient.sql(topRoutesSql)
            .query((rs, rowNum) -> new DashboardRoadLengthStatDto.RouteLengthDto(
                rs.getString("record_key"),
                rs.getString("route_name"),
                rs.getDouble("length_km")
            ))
            .list();

        // 2. Phân bố theo dải chiều dài (<50km, 50-100km, 100-300km, >300km)
        String distSql = """
            SELECT 
                CASE 
                    WHEN length_km < 50 THEN 'Dưới 50 km (Đoạn ngắn/Nhánh)'
                    WHEN length_km >= 50 AND length_km < 100 THEN 'Từ 50 - 100 km (Tuyến trung bình)'
                    WHEN length_km >= 100 AND length_km < 300 THEN 'Từ 100 - 300 km (Tuyến liên tỉnh)'
                    ELSE 'Trên 300 km (Tuyến trục quốc gia)'
                END AS range_label,
                COUNT(*) AS route_count,
                ROUND(SUM(length_km), 2) AS total_km
            FROM view_dashboard_national_road_stats
            GROUP BY 1
            ORDER BY total_km DESC;
        """;

        List<DashboardRoadLengthStatDto.LengthDistributionDto> distribution = jdbcClient.sql(distSql)
            .query((rs, rowNum) -> new DashboardRoadLengthStatDto.LengthDistributionDto(
                rs.getString("range_label"),
                rs.getInt("route_count"),
                rs.getDouble("total_km")
            ))
            .list();

        String sumSql = """
            SELECT 
                COUNT(*) AS total_routes,
                COALESCE(ROUND(SUM(length_km), 2), 0) AS total_length_km
            FROM view_dashboard_national_road_stats;
        """;
        Map<String, Object> totals = jdbcClient.sql(sumSql).query().singleRow();
        int totalRoutes = ((Number) totals.getOrDefault("total_routes", 0)).intValue();
        double totalLengthKm = ((Number) totals.getOrDefault("total_length_km", 0.0)).doubleValue();
        double avgLength = totalRoutes > 0 ? Math.round((totalLengthKm / totalRoutes) * 100.0) / 100.0 : 0.0;

        return new DashboardRoadLengthStatDto(
            totalRoutes,
            totalLengthKm,
            avgLength,
            longestRoutes,
            distribution,
            OffsetDateTime.now()
        );
    }

    public List<DashboardRecentAssetDto> getRecentAssets(int limit) {
        int safeLimit = Math.min(Math.max(limit, 5), 20);
        log.debug("Lấy {} tài sản cập nhật gần đây...", safeLimit);

        String sql = """
            SELECT 
                r.dataset_key,
                d.dataset_name,
                r.record_key,
                COALESCE(r.raw_payload->>'fielddisplay', r.raw_payload->>'text', r.record_key) AS asset_name,
                r.raw_payload->>'branch_id' AS branch_id,
                r.imported_at
            FROM raw_dataset_record r
            JOIN dataset_registry d ON r.dataset_key = d.dataset_key
            WHERE d.kind = 'asset'
            ORDER BY r.imported_at DESC
            LIMIT :limit;
        """;

        return jdbcClient.sql(sql)
            .param("limit", safeLimit)
            .query((rs, rowNum) -> {
                String dKey = rs.getString("dataset_key");
                String branchId = rs.getString("branch_id");
                return new DashboardRecentAssetDto(
                    dKey,
                    rs.getString("dataset_name"),
                    rs.getString("record_key"),
                    rs.getString("asset_name"),
                    branchId,
                    resolveBranchName(branchId),
                    rs.getObject("imported_at", OffsetDateTime.class),
                    dKey,
                    "/assets"
                );
            })
            .list();
    }

    public static String resolveBranchName(String branchId) {
        if (branchId == null || branchId.isBlank() || "unassigned".equalsIgnoreCase(branchId)) {
            return "Cơ quan Trung ương / Chưa phân loại";
        }
        return switch (branchId.toLowerCase()) {
            case "kqldb_1" -> "Khu QLĐB I (Miền Bắc)";
            case "kqldb_2" -> "Khu QLĐB II (Bắc Trung Bộ)";
            case "kqldb_3" -> "Khu QLĐB III (Duyên hải & Tây Nguyên)";
            case "kqldb_4" -> "Khu QLĐB IV (Miền Nam)";
            case "cdb_vn" -> "Cục Đường bộ Việt Nam";
            case "sxd_na" -> "Sở GTVT Nghệ An";
            case "sxd_pt" -> "Sở GTVT Phú Thọ";
            case "sxd_th" -> "Sở GTVT Thanh Hóa";
            case "sxd_qni" -> "Sở GTVT Quảng Ninh";
            case "sxd_tpdn" -> "Sở GTVT TP. Đà Nẵng";
            case "sxd_tq" -> "Sở GTVT Tuyên Quang";
            case "sxd_ld" -> "Sở GTVT Lâm Đồng";
            case "sxd_nb" -> "Sở GTVT Ninh Bình";
            case "sxd_gl" -> "Sở GTVT Gia Lai";
            case "sxd_lch" -> "Sở GTVT Lai Châu";
            case "sxd_dl" -> "Sở GTVT Đắk Lắk";
            case "sxd_cb" -> "Sở GTVT Cao Bằng";
            case "sxd_lc" -> "Sở GTVT Lào Cai";
            case "sxd_ag" -> "Sở GTVT An Giang";
            case "sxd_sl" -> "Sở GTVT Sơn La";
            case "sxd_bn" -> "Sở GTVT Bắc Ninh";
            case "sxd_qt" -> "Sở GTVT Quảng Trị";
            case "sxd_vl" -> "Sở GTVT Vĩnh Long";
            case "sxd_thn" -> "Sở GTVT Thái Nguyên";
            case "sxd_db" -> "Sở GTVT Điện Biên";
            case "sxd_ht" -> "Sở GTVT Hà Tĩnh";
            case "sxd_ls" -> "Sở GTVT Lạng Sơn";
            case "sxd_tphp" -> "Sở GTVT TP. Hải Phòng";
            case "sxd_hy" -> "Sở GTVT Hưng Yên";
            case "sxd_qn" -> "Sở GTVT Quảng Nam";
            case "sxd_tphcm" -> "Sở GTVT TP. Hồ Chí Minh";
            case "sxd_tn" -> "Sở GTVT Tây Ninh";
            case "sxd_tph" -> "Sở GTVT TP. Hà Nội";
            case "sxd_dn" -> "Sở GTVT Đắk Nông";
            case "sxd_kh" -> "Sở GTVT Khánh Hòa";
            case "sxd_tpct" -> "Sở GTVT TP. Cần Thơ";
            case "sxd_dt" -> "Sở GTVT Đồng Tháp";
            default -> branchId.startsWith("sxd_") ? "Sở GTVT " + branchId.substring(4).toUpperCase() : branchId;
        };
    }
}
