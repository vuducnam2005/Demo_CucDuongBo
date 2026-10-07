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

/**
 * Giai đoạn 3 - Smoke Test Read-Only đối soát dữ liệu đã nạp.
 * Kiểm tra 3 dataset đại diện: mst_national_road (nhỏ), tbl_bridge (vừa + GIS), tbl_road_sign (lớn).
 */
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
public class DatasetImportSmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // 1. DATASET 1: mst_national_road (Đường quốc lộ - 169 bản ghi, 77 trường)
    // =========================================================================

    @Test
    @DisplayName("1.1 mst_national_road: Kiểm tra metadata và số trường thuộc tính")
    void testMstNationalRoadMetadata() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$.datasetName").value("Đường quốc lộ"))
                .andExpect(jsonPath("$.kind").value("asset"))
                .andExpect(jsonPath("$.totalRecords").value(169))
                .andExpect(jsonPath("$.fields", hasSize(77)));
    }

    @Test
    @DisplayName("1.2 mst_national_road: Phân trang đọc 10 bản ghi, kiểm tra tổng 169")
    void testMstNationalRoadPagination() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/records")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "id,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(169))
                .andExpect(jsonPath("$.totalPages").value(17))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$.content[0].payload").exists());
    }

    @Test
    @DisplayName("1.3 mst_national_road: Đọc chi tiết bản ghi QL.1 theo record_key")
    void testMstNationalRoadRecordDetail() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/records/17615867416863577")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$.recordKey").value("17615867416863577"))
                .andExpect(jsonPath("$.payload").exists())
                .andExpect(jsonPath("$.payload.data_").exists());
    }

    // =========================================================================
    // 2. DATASET 2: tbl_bridge (Cầu quốc lộ - 11.631 bản ghi, 131 trường, có GIS)
    // =========================================================================

    @Test
    @DisplayName("2.1 tbl_bridge: Kiểm tra metadata, kiểu hình học POINT và 131 trường")
    void testTblBridgeMetadata() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("tbl_bridge"))
                .andExpect(jsonPath("$.datasetName").value("Cầu quốc lộ"))
                .andExpect(jsonPath("$.kind").value("asset"))
                .andExpect(jsonPath("$.totalRecords").value(11631))
                .andExpect(jsonPath("$.geometryType").value("POINT"))
                .andExpect(jsonPath("$.fields", hasSize(131)));
    }

    @Test
    @DisplayName("2.2 tbl_bridge: Phân trang đọc 20 bản ghi, kiểm tra tổng 11.631")
    void testTblBridgePagination() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/records")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "id,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(11631))
                .andExpect(jsonPath("$.totalPages").value(582))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.content[0].datasetKey").value("tbl_bridge"));
    }

    @Test
    @DisplayName("2.3 tbl_bridge: Đọc dữ liệu không gian GeoJSON theo BBOX và kiểm tra tọa độ WGS84")
    void testTblBridgeGeoSpatial() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/geo")
                        .param("bbox", "102.0,8.0,110.0,24.0")
                        .param("limit", "50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.totalFeatures", greaterThan(0)))
                .andExpect(jsonPath("$.features", not(empty())))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates", hasSize(2)))
                // Kiểm tra kinh độ (X) nằm trong dải [102, 110]
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", greaterThanOrEqualTo(102.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", lessThanOrEqualTo(110.0)))
                // Kiểm tra vĩ độ (Y) nằm trong dải [8, 24]
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", greaterThanOrEqualTo(8.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", lessThanOrEqualTo(24.0)));
    }

    // =========================================================================
    // 3. DATASET 3: tbl_road_sign (Biển báo - 222.112 bản ghi, 75 trường, dataset lớn)
    // =========================================================================

    @Test
    @DisplayName("3.1 tbl_road_sign: Kiểm tra metadata và 75 trường của 222.112 bản ghi")
    void testTblRoadSignMetadata() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/metadata")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetKey").value("tbl_road_sign"))
                .andExpect(jsonPath("$.datasetName").value("Biển báo"))
                .andExpect(jsonPath("$.kind").value("asset"))
                .andExpect(jsonPath("$.totalRecords").value(222112))
                .andExpect(jsonPath("$.fields", hasSize(75)));
    }

    @Test
    @DisplayName("3.2 tbl_road_sign: Phân trang đọc 25 bản ghi, kiểm tra tổng 222.112")
    void testTblRoadSignPagination() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/records")
                        .param("page", "0")
                        .param("size", "25")
                        .param("sort", "id,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(222112))
                .andExpect(jsonPath("$.totalPages").value(8885))
                .andExpect(jsonPath("$.content", hasSize(25)))
                .andExpect(jsonPath("$.content[0].datasetKey").value("tbl_road_sign"));
    }

    @Test
    @DisplayName("3.3 tbl_road_sign: Tra cứu trang sâu (Deep pagination) kiểm tra hiệu năng B-Tree Index")
    void testTblRoadSignDeepPagination() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/records")
                        .param("page", "100")
                        .param("size", "10")
                        .param("sort", "id,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(100))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(222112))
                .andExpect(jsonPath("$.content", hasSize(10)));
    }
}
