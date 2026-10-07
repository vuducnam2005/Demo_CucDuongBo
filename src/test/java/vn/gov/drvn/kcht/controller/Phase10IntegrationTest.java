package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.gov.drvn.kcht.dto.ReferenceCatalogItemDto;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://localhost:5436/kcht_db",
        "spring.datasource.username=kcht_user",
        "spring.datasource.password=${POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Kiểm thử tích hợp Giai đoạn 10 - Báo cáo, Danh mục & Hồ sơ tài liệu")
class Phase10IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM raw_dataset_record WHERE raw_payload->>'vidagis_name' = 'bien_ban_nghiem_thu_km12.pdf'");
    }

    @Test
    @DisplayName("1. GET /api/reports/road-lengths: Trả về tổng hợp chiều dài mạng lưới quốc lộ")
    void testRoadLengthReport() throws Exception {
        mockMvc.perform(get("/api/reports/road-lengths")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.summary", notNullValue()))
                .andExpect(jsonPath("$.summary.totalRoutes", greaterThan(0)))
                .andExpect(jsonPath("$.summary.totalLengthKm", greaterThan(0.0)))
                .andExpect(jsonPath("$.summary.averageLengthKm", greaterThan(0.0)))
                .andExpect(jsonPath("$.routes", notNullValue()))
                .andExpect(jsonPath("$.branchDistribution", notNullValue()))
                .andExpect(jsonPath("$.surfaceDistribution", notNullValue()))
                .andExpect(jsonPath("$.branchDistribution[0].percent", notNullValue()))
                .andExpect(jsonPath("$.surfaceDistribution[0].percent", notNullValue()));

        Integer sqlRouteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM raw_dataset_record r
                CROSS JOIN LATERAL jsonb_array_elements((r.raw_payload->>'data_')::jsonb) elem
                WHERE r.dataset_key = 'mst_national_road'
                  AND elem->>'column_identify' = 'actual_length'
                  AND NULLIF(elem->>'column_value', '') IS NOT NULL
                """, Integer.class);
        BigDecimal sqlTotalLength = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(NULLIF(elem->>'column_value', '')::numeric), 0)
                FROM raw_dataset_record r
                CROSS JOIN LATERAL jsonb_array_elements((r.raw_payload->>'data_')::jsonb) elem
                WHERE r.dataset_key = 'mst_national_road'
                  AND elem->>'column_identify' = 'actual_length'
                  AND NULLIF(elem->>'column_value', '') IS NOT NULL
                """, BigDecimal.class);
        org.hamcrest.MatcherAssert.assertThat(sqlRouteCount, org.hamcrest.Matchers.equalTo(
                objectMapper.readTree(mockMvc.perform(get("/api/reports/road-lengths")
                                .header("X-User-Role", "ROLE_VIEWER"))
                        .andReturn().getResponse().getContentAsString()).path("summary").path("totalRoutes").asInt()));
        org.hamcrest.MatcherAssert.assertThat(sqlTotalLength.doubleValue(), org.hamcrest.Matchers.closeTo(
                objectMapper.readTree(mockMvc.perform(get("/api/reports/road-lengths")
                                .header("X-User-Role", "ROLE_VIEWER"))
                        .andReturn().getResponse().getContentAsString()).path("summary").path("totalLengthKm").asDouble(), 0.1));

        var filteredResult = mockMvc.perform(get("/api/reports/road-lengths")
                        .param("branch", "cdb_vn")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andReturn();
        var filteredSummary = objectMapper.readTree(filteredResult.getResponse().getContentAsString()).path("summary");
        Integer sqlBranchRouteCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM raw_dataset_record r
                CROSS JOIN LATERAL jsonb_array_elements((r.raw_payload->>'data_')::jsonb) elem
                WHERE r.dataset_key = 'mst_national_road'
                  AND r.raw_payload->>'branch_id' = 'cdb_vn'
                  AND elem->>'column_identify' = 'actual_length'
                  AND NULLIF(elem->>'column_value', '') IS NOT NULL
                """, Integer.class);
        org.hamcrest.MatcherAssert.assertThat(filteredSummary.path("totalRoutes").asInt(),
                org.hamcrest.Matchers.equalTo(sqlBranchRouteCount));
    }

    @Test
    @DisplayName("2. GET /api/reports/road-lengths/export: Xuất tệp CSV UTF-8 BOM chuẩn tiếng Việt")
    void testExportRoadLengthCsv() throws Exception {
        mockMvc.perform(get("/api/reports/road-lengths/export")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("\uFEFFSTT,Mã tuyến,Tên tuyến đường,Chiều dài (km)")));
    }

    @Test
    @DisplayName("3a. Export báo cáo bảo trì và biển báo giữ BOM, header tiếng Việt và dữ liệu theo bộ lọc")
    void testReportCsvContracts() throws Exception {
        mockMvc.perform(get("/api/reports/maintenance/export")
                        .param("year", "2026")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("\uFEFFSTT,Mã dự án,Tên công trình/hạng mục bảo trì")))
                .andExpect(content().string(containsString("BT-2026-001")));

        mockMvc.perform(get("/api/reports/road-signs-blackspots/export")
                        .param("route", "QL.1")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("\uFEFFSTT,Mã điểm đen,Tuyến đường,Lý trình / Vị trí")))
                .andExpect(content().string(containsString("DDB-QL1-01")));
    }

    @Test
    @DisplayName("3b. Bộ lọc không có dữ liệu trả về aggregate zero thay vì dữ liệu giả")
    void testEmptyRoadLengthFilter() throws Exception {
        mockMvc.perform(get("/api/reports/road-lengths")
                        .param("route", "ROUTE_DOES_NOT_EXIST")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRoutes", is(0)))
                .andExpect(jsonPath("$.summary.totalLengthKm", is(0.0)))
                .andExpect(jsonPath("$.summary.averageLengthKm", is(0.0)))
                .andExpect(jsonPath("$.summary.longestRouteCode", is("—")))
                .andExpect(jsonPath("$.branchDistribution", hasSize(0)))
                .andExpect(jsonPath("$.surfaceDistribution", hasSize(0)));
    }

    @Test
    @DisplayName("3. GET /api/reports/maintenance: Trả về báo cáo kế hoạch và kinh phí bảo trì")
    void testMaintenanceReport() throws Exception {
        mockMvc.perform(get("/api/reports/maintenance")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalProjects", greaterThan(0)))
                .andExpect(jsonPath("$.summary.totalBudgetVnd", notNullValue()))
                .andExpect(jsonPath("$.summary.completedCount", notNullValue()))
                .andExpect(jsonPath("$.summary.inProgressCount", notNullValue()))
                .andExpect(jsonPath("$.summary.plannedCount", notNullValue()))
                .andExpect(jsonPath("$.summary.completionRatePercent", notNullValue()))
                .andExpect(jsonPath("$.projects", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.projects[0].id", notNullValue()))
                .andExpect(jsonPath("$.branchSummary", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.branchSummary[0].percent", notNullValue()))
                .andExpect(jsonPath("$.yearlySummary", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("4. GET /api/reports/road-signs-blackspots: Thống kê biển báo QCVN 41 và điểm đen")
    void testRoadSignBlackspotReport() throws Exception {
        mockMvc.perform(get("/api/reports/road-signs-blackspots")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRoadSigns", is(222112)))
                .andExpect(jsonPath("$.summary.totalSignCategories", is(5)))
                .andExpect(jsonPath("$.summary.totalBlackspots", greaterThan(0)))
                .andExpect(jsonPath("$.signCategories", hasSize(5)))
                .andExpect(jsonPath("$.signCategories[0].signPrefix", notNullValue()))
                .andExpect(jsonPath("$.signCategories[0].percent", notNullValue()))
                .andExpect(jsonPath("$.signCategories[0].sampleSignCode", notNullValue()))
                .andExpect(jsonPath("$.blackspots", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.blackspots[0].kmMarker", notNullValue()))
                .andExpect(jsonPath("$.blackspots[0].description", notNullValue()))
                .andExpect(jsonPath("$.blackspots[0].incidentCount", notNullValue()))
                .andExpect(jsonPath("$.blackspots[0].fatalityCount", notNullValue()))
                .andExpect(jsonPath("$.branchStatistics", hasSize(4)))
                .andExpect(jsonPath("$.branchStatistics[0].signCount", notNullValue()));
    }

    @Test
    @DisplayName("5. GET /api/catalogs: Trả về danh sách tất cả các danh mục tham chiếu")
    void testGetAllCatalogs() throws Exception {
        mockMvc.perform(get("/api/catalogs")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].catalogCode", notNullValue()))
                .andExpect(jsonPath("$[0].catalogName", notNullValue()));
    }

    @Test
    @DisplayName("6. POST /api/catalogs/{catalog}: ROLE_VIEWER bị chặn 403 Forbidden khi cố sửa danh mục")
    void testCatalogWrite_ForbiddenForViewer() throws Exception {
        ReferenceCatalogItemDto item = new ReferenceCatalogItemDto(
                "c_test_catalog", "99", "Mục Thử Nghiệm", null, 99, true, Map.of()
        );

        mockMvc.perform(post("/api/catalogs/c_test_catalog")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7. POST /api/catalogs/{catalog}: ROLE_ADMIN được phép tạo và cập nhật danh mục thành công")
    void testCatalogWrite_AllowedForAdmin() throws Exception {
        ReferenceCatalogItemDto item = new ReferenceCatalogItemDto(
                "c_test_catalog", "TEST_99", "Phần Tử Danh Mục Mới", null, 99, true, Map.of()
        );

        mockMvc.perform(post("/api/catalogs/c_test_catalog")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCode", is("TEST_99")))
                .andExpect(jsonPath("$.itemName", is("Phần Tử Danh Mục Mới")));
    }

    @Test
    @DisplayName("8. GET /api/documents/folders: Trả về cây thư mục hồ sơ tài liệu")
    void testGetDocumentFolders() throws Exception {
        mockMvc.perform(get("/api/documents/folders")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].folderName", notNullValue()));
    }

    @Test
    @DisplayName("9. POST /api/documents/upload: Tải lên tệp tin và lưu trữ metadata an toàn")
    void testUploadDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bien_ban_nghiem_thu_km12.pdf",
                "application/pdf",
                "%PDF-1.4 sample test document content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("folderId", "cdb_vn")
                        .param("assetRecordId", "bridge_01")
                        .param("branchId", "kqldb_1")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName", is("bien_ban_nghiem_thu_km12.pdf")))
                .andExpect(jsonPath("$.fileExtension", is("pdf")))
                .andExpect(jsonPath("$.fileEntryId", notNullValue()));
    }
}
