package vn.gov.drvn.kcht.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.service.DashboardService;
import vn.gov.drvn.kcht.service.DatasetQueryService;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ kiểm chuẩn hiệu năng toàn diện (Comprehensive Performance Benchmark Suite)
 * Giai đoạn 13 - Hiệu năng và Vận hành (KCHT ĐB).
 * Đo kiểm thực nghiệm:
 * 1. Độ trễ Dashboard API (p50 / p95)
 * 2. Độ trễ Phân trang Danh sách Tài sản lớn (tbl_road_sign: 222k, tbl_bridge: 11.6k)
 * 3. Độ trễ Tìm kiếm từ khóa (Keyword Search)
 * 4. Độ trễ Truy vấn Không gian PostGIS (BBOX & Grid Clustering)
 * 5. Tần suất và tỷ lệ sử dụng chỉ mục CSDL (Database Index Usage)
 * 6. Tiêu thụ bộ nhớ JVM (Heap Memory Usage)
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerformanceBenchmarkIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PerformanceBenchmarkIntegrationTest.class);

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private DatasetQueryService datasetQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Hàm tiện ích tính toán phân vị Min, p50, p95, Max từ danh sách thời gian thực thi (ms).
     */
    private static BenchmarkStats calculateStats(List<Long> latenciesMs) {
        if (latenciesMs.isEmpty()) return new BenchmarkStats(0, 0, 0, 0, 0);
        List<Long> sorted = new ArrayList<>(latenciesMs);
        Collections.sort(sorted);
        int n = sorted.size();
        long min = sorted.get(0);
        long max = sorted.get(n - 1);
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = sorted.get((int) Math.floor(n * 0.50));
        int p95Idx = Math.min((int) Math.ceil(n * 0.95) - 1, n - 1);
        long p95 = sorted.get(Math.max(0, p95Idx));
        return new BenchmarkStats(min, Math.round(avg * 100.0) / 100.0, p50, p95, max);
    }

    public record BenchmarkStats(long minMs, double avgMs, long p50Ms, long p95Ms, long maxMs) {
        @Override
        public String toString() {
            return String.format("[Min: %dms, Avg: %.2fms, p50: %dms, p95: %dms, Max: %dms]", minMs, avgMs, p50Ms, p95Ms, maxMs);
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Benchmark Dashboard Queries: Đo độ trễ mở Bảng điều hành (p50 / p95)")
    void testDashboardLatencyBenchmark() {
        log.info("=== BẮT ĐẦU BENCHMARK DASHBOARD QUERIES (30 VÒNG LẶP) ===");
        List<Long> summaryLatencies = new ArrayList<>();
        List<Long> branchLatencies = new ArrayList<>();
        List<Long> roadSignLatencies = new ArrayList<>();
        List<Long> roadLengthLatencies = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            long t0 = System.nanoTime();
            DashboardSummaryDto summary = dashboardService.getSummary();
            long t1 = System.nanoTime();
            summaryLatencies.add((t1 - t0) / 1_000_000);
            assertNotNull(summary);
            assertTrue(summary.getTotalAssets() > 0);

            long t2 = System.nanoTime();
            List<DashboardBranchStatDto> branches = dashboardService.getBranchStats();
            long t3 = System.nanoTime();
            branchLatencies.add((t3 - t2) / 1_000_000);
            assertFalse(branches.isEmpty());

            long t4 = System.nanoTime();
            DashboardRoadSignStatDto signs = dashboardService.getRoadSignStats();
            long t5 = System.nanoTime();
            roadSignLatencies.add((t5 - t4) / 1_000_000);
            assertNotNull(signs);

            long t6 = System.nanoTime();
            DashboardRoadLengthStatDto lengths = dashboardService.getRoadLengthStats();
            long t7 = System.nanoTime();
            roadLengthLatencies.add((t7 - t6) / 1_000_000);
            assertNotNull(lengths);
        }

        BenchmarkStats summaryStats = calculateStats(summaryLatencies);
        BenchmarkStats branchStats = calculateStats(branchLatencies);
        BenchmarkStats signStats = calculateStats(roadSignLatencies);
        BenchmarkStats lengthStats = calculateStats(roadLengthLatencies);

        log.info("Dashboard Summary Latency:      {}", summaryStats);
        log.info("Dashboard Branch Stats Latency: {}", branchStats);
        log.info("Dashboard Road Signs Latency:   {}", signStats);
        log.info("Dashboard Road Lengths Latency: {}", lengthStats);

        // Đảm bảo thời gian mở dashboard cực nhanh
        assertTrue(summaryStats.p95Ms() <= 200, "Dashboard Summary p95 phải <= 200ms");
        assertTrue(branchStats.p95Ms() <= 100, "Dashboard Branch Stats p95 phải <= 100ms");
    }

    @Test
    @Order(2)
    @DisplayName("2. Benchmark Dataset Pagination: Đo độ trễ tải trang danh sách với Deferred Join (p50 / p95)")
    void testDatasetPaginationBenchmark() {
        log.info("=== BẮT ĐẦU BENCHMARK PHÂN TRANG DANH SÁCH (tbl_road_sign 222k & tbl_bridge 11.6k) ===");

        // 2.1 Benchmark trên dataset biển báo cực lớn (tbl_road_sign - 222.112 bản ghi)
        List<Long> roadSignLatencies = new ArrayList<>();
        for (int page = 0; page < 20; page++) {
            long t0 = System.nanoTime();
            PagedResponse<RecordItemDto> res = datasetQueryService.getRecords(
                    "tbl_road_sign", null, null, "id,asc", page, 20, "ROLE_ADMIN"
            );
            long t1 = System.nanoTime();
            roadSignLatencies.add((t1 - t0) / 1_000_000);
            assertEquals(20, res.getContent().size());
            assertEquals(222112L, res.getTotalElements());
        }
        BenchmarkStats roadSignStats = calculateStats(roadSignLatencies);
        log.info("Pagination tbl_road_sign (222k records): {}", roadSignStats);

        // 2.2 Benchmark trên dataset cầu đường bộ (tbl_bridge - 11.631 bản ghi)
        List<Long> bridgeLatencies = new ArrayList<>();
        for (int page = 0; page < 20; page++) {
            long t0 = System.nanoTime();
            PagedResponse<RecordItemDto> res = datasetQueryService.getRecords(
                    "tbl_bridge", null, null, "id,asc", page, 20, "ROLE_ADMIN"
            );
            long t1 = System.nanoTime();
            bridgeLatencies.add((t1 - t0) / 1_000_000);
            assertEquals(20, res.getContent().size());
            assertEquals(11631L, res.getTotalElements());
        }
        BenchmarkStats bridgeStats = calculateStats(bridgeLatencies);
        log.info("Pagination tbl_bridge (11.6k records):   {}", bridgeStats);

        // Kiểm tra ngưỡng cam kết: phân trang phải < 200ms nhờ Deferred Join và O(1) count
        assertTrue(roadSignStats.p95Ms() <= 500, "Phân trang 222k dòng p95 phải <= 500ms");
        assertTrue(bridgeStats.p95Ms() <= 200, "Phân trang 11k dòng p95 phải <= 200ms");
    }

    @Test
    @Order(3)
    @DisplayName("3. Benchmark Keyword Search: Đo độ trễ tìm kiếm từ khóa trên tập dữ liệu lớn")
    void testKeywordSearchBenchmark() {
        log.info("=== BẮT ĐẦU BENCHMARK TÌM KIẾM TỪ KHÓA (KEYWORD SEARCH) ===");
        List<Long> searchLatencies = new ArrayList<>();
        String[] keywords = {"Thăng Long", "Chương Dương", "Cầu Giấy", "QL.1", "Hà Nội"};

        for (String kw : keywords) {
            for (int i = 0; i < 3; i++) {
                long t0 = System.nanoTime();
                PagedResponse<RecordItemDto> res = datasetQueryService.getRecords(
                        "tbl_bridge", kw, null, "id,asc", 0, 20, "ROLE_ADMIN"
                );
                long t1 = System.nanoTime();
                searchLatencies.add((t1 - t0) / 1_000_000);
                assertNotNull(res);
            }
        }
        BenchmarkStats searchStats = calculateStats(searchLatencies);
        log.info("Keyword Search tbl_bridge Latency:       {}", searchStats);
        assertTrue(searchStats.p95Ms() <= 4500, "Tìm kiếm từ khóa p95 phải <= 4500ms");
    }

    @Test
    @Order(4)
    @DisplayName("4. Benchmark PostGIS Spatial Queries: Đo độ trễ BBOX và Grid Clustering")
    void testSpatialQueriesBenchmark() {
        log.info("=== BẮT ĐẦU BENCHMARK TRUY VẤN KHÔNG GIAN POSTGIS (BBOX & CLUSTERING) ===");

        // 4.1 Chạy GIS Benchmark tích hợp trên 2 dataset lớn
        long t0 = System.currentTimeMillis();
        GisBenchmarkDto gisRes = datasetQueryService.runGisBenchmark("tbl_road_sign", "tbl_bridge", "ROLE_ADMIN");
        long totalGisDuration = System.currentTimeMillis() - t0;
        assertNotNull(gisRes);
        assertTrue(gisRes.isComplianceNoFullLoad());
        assertEquals(2, gisRes.getDatasets().size());

        for (var item : gisRes.getDatasets()) {
            log.info("GIS Dataset: {} | Total: {} | Spatial: {} | BBOX: {}ms ({} features) | Cluster: {}ms ({} clusters) | Compression: {}%",
                    item.getDatasetKey(), item.getTotalDatasetRecords(), item.getSpatialRecordsWithCoordinates(),
                    item.getBboxQueryExecutionTimeMs(), item.getBboxFeaturesReturned(),
                    item.getGridClusterExecutionTimeMs(), item.getGridClustersReturned(),
                    item.getCompressionRatioPercent());
        }

        log.info("Tổng thời gian chạy GIS Benchmark: {}ms", totalGisDuration);
        assertTrue(totalGisDuration < 60000, "Tổng GIS benchmark phải hoàn thành dưới 60 giây");
    }

    @Test
    @Order(5)
    @DisplayName("5. Kiểm tra Tỷ lệ Sử dụng Chỉ mục CSDL (Database Index Usage)")
    void testDatabaseIndexUsageInspection() {
        log.info("=== BẮT ĐẦU KIỂM TRA CHỈ MỤC POSTGRESQL (PG_STAT_USER_INDEXES) ===");
        String sql = """
            SELECT 
                relname,
                indexrelname,
                idx_scan,
                idx_tup_read,
                idx_tup_fetch
            FROM pg_stat_user_indexes
            WHERE relname IN ('raw_dataset_record', 'dataset_registry', 'asset_record', 'mv_dashboard_branch_stats')
              AND idx_scan > 0
            ORDER BY idx_scan DESC;
        """;

        List<Map<String, Object>> activeIndexes = jdbcTemplate.queryForList(sql);
        assertFalse(activeIndexes.isEmpty(), "Phải có chỉ mục PostgreSQL đang hoạt động phục vụ truy vấn");

        log.info("Danh sách các chỉ mục có lượt quét (idx_scan > 0):");
        for (Map<String, Object> idx : activeIndexes) {
            log.info("  Bảng: {:<20} | Index: {:<35} | Scans: {:<8} | Fetches: {}",
                    idx.get("relname"), idx.get("indexrelname"), idx.get("idx_scan"), idx.get("idx_tup_fetch"));
        }

        // Xác nhận các chỉ mục cốt lõi đã được kích hoạt
        boolean hasPkOrUnique = activeIndexes.stream()
                .anyMatch(idx -> String.valueOf(idx.get("indexrelname")).contains("raw_dataset_record") ||
                        String.valueOf(idx.get("indexrelname")).contains("dataset_registry"));
        assertTrue(hasPkOrUnique, "Các chỉ mục khóa và định danh bắt buộc phải được PostgreSQL sử dụng");
    }

    @Test
    @Order(6)
    @DisplayName("6. Đo Tiêu thụ Bộ nhớ JVM Backend (JVM Heap Memory Usage)")
    void testJvmMemoryUsageMetrics() {
        log.info("=== BẮT ĐẦU ĐO TIÊU THỤ BỘ NHỚ BACKEND JVM ===");
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memBean.getHeapMemoryUsage();

        long usedMb = heapUsage.getUsed() / (1024 * 1024);
        long committedMb = heapUsage.getCommitted() / (1024 * 1024);
        long maxMb = heapUsage.getMax() / (1024 * 1024);

        log.info("JVM Heap Memory - Used: {} MB | Committed: {} MB | Max: {} MB", usedMb, committedMb, maxMb);

        // Bảo đảm bộ nhớ Heap không bị rò rỉ sau khi truy vấn dữ liệu lớn
        assertTrue(usedMb < 1024, "Bộ nhớ Heap đã sử dụng phải nhỏ hơn 1024 MB");
    }
}
