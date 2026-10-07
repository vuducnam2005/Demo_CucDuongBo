package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
public class DatasetApiPostgisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. GET /api/datasets - Tra cứu danh sách dataset có phân trang và lọc theo kind")
    void testGetDatasets() throws Exception {
        mockMvc.perform(get("/api/datasets")
                        .param("page", "0")
                        .param("size", "10")
                        .param("kind", "asset")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].kind").value("asset"));
    }

    @Test
    @DisplayName("2. GET /api/datasets/{dataset}/metadata - Lấy siêu dữ liệu và từ điển trường")
    void testGetDatasetMetadata() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("tbl_bridge"))
                .andExpect(jsonPath("$.datasetName").value("Cầu quốc lộ"))
                .andExpect(jsonPath("$.kind").value("asset"))
                .andExpect(jsonPath("$.geometryType").value("POINT"))
                .andExpect(jsonPath("$.fields", not(empty())));
    }

    @Test
    @DisplayName("3. GET /api/datasets/{dataset}/records - Phân trang server-side và tìm kiếm bản ghi")
    void testGetDatasetRecords() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "id,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(169))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$.content[0].payload").exists());
    }

    @Test
    @DisplayName("4. GET /api/datasets/{dataset}/records/{id} - Lấy chi tiết bản ghi theo record_key")
    void testGetRecordById() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/records/17615867416863577")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$.recordKey").value("17615867416863577"))
                .andExpect(jsonPath("$.payload").exists());
    }

    @Test
    @DisplayName("5. GET /api/datasets/{dataset}/geo - Lấy GeoJSON FeatureCollection theo Bounding Box")
    void testGetGeoDataWithBbox() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/geo")
                        .param("bbox", "102.0,8.0,110.0,24.0")
                        .param("limit", "25")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.totalFeatures", greaterThan(0)))
                .andExpect(jsonPath("$.features", not(empty())))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates", hasSize(2)));
    }

    @Test
    @DisplayName("6. GET /api/reference-catalogs/{catalog} - Tra cứu danh mục dùng chung (Tỉnh thành)")
    void testGetReferenceCatalog() throws Exception {
        mockMvc.perform(get("/api/reference-catalogs/c_tinhthanhpho")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.content[0].itemName", notNullValue()));
    }

    @Test
    @DisplayName("7. GET /api/documents/folders - Lấy danh sách cây thư mục tài liệu")
    void testGetDocumentFolders() throws Exception {
        mockMvc.perform(get("/api/documents/folders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].folderName", notNullValue()));
    }

    @Test
    @DisplayName("8. GET /api/documents - Tra cứu và phân trang danh sách tài liệu")
    void testGetDocuments() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(200)))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].fileName", notNullValue()));
    }

    @Test
    @DisplayName("9. Phân quyền truy cập - ROLE_VIEWER bị chặn truy cập dataset quản trị nhạy cảm (403)")
    void testPermissionAccessDenied() throws Exception {
        mockMvc.perform(get("/api/datasets/maintenance_detail_baidoxe_chitiet/records")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("10. An ninh & Phòng chống SQL Injection - Từ chối tham số và dataset không hợp lệ (400 / 404)")
    void testSecurityAndValidation() throws Exception {
        // Tên dataset chứa ký tự đặc biệt không an toàn (chống SQL injection / path traversal)
        mockMvc.perform(get("/api/datasets/invalid$name!bad/records")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Dataset không tồn tại
        mockMvc.perform(get("/api/datasets/non_existent_dataset_abc/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Sort theo cột lạ không nằm trong allowlist
        mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .param("sort", "malicious_col;--")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Bounding box sai logic (minLon > maxLon)
        mockMvc.perform(get("/api/datasets/tbl_bridge/geo")
                        .param("minLon", "110.0")
                        .param("maxLon", "102.0")
                        .param("minLat", "8.0")
                        .param("maxLat", "20.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
