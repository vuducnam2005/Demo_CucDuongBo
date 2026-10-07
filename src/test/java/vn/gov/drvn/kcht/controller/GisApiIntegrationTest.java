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
class GisApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/datasets/{dataset}/geo: BBOX query returns valid GeoJSON FeatureCollection")
    void testGeoData_WithBbox_ReturnsFeatures() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/geo")
                        .param("minLon", "105.0")
                        .param("minLat", "20.0")
                        .param("maxLon", "107.0")
                        .param("maxLat", "22.0")
                        .param("limit", "50")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", notNullValue()))
                .andExpect(jsonPath("$.features", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.features[0].type", is("Feature")))
                .andExpect(jsonPath("$.features[0].geometry.type", is("Point")))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", greaterThanOrEqualTo(105.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", lessThanOrEqualTo(107.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", greaterThanOrEqualTo(20.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", lessThanOrEqualTo(22.0)))
                .andExpect(jsonPath("$.features[0].properties.dataset_key", is("tbl_bridge")));
    }

    @Test
    @DisplayName("GET /api/datasets/tbl_road_sign/geo: Resolves coordinates via parent pole and returns features")
    void testGeoData_RoadSign_ResolvesCoordinatesViaParent() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/geo")
                        .param("bbox", "105.0,16.0,108.0,22.0")
                        .param("limit", "50")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", notNullValue()))
                .andExpect(jsonPath("$.features", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.features[0].properties.dataset_key", is("tbl_road_sign")))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", notNullValue()))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/datasets/{dataset}/geo: Supports filtering by branch and keyword")
    void testGeoData_WithFilters_BranchAndKeyword() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_bridge/geo")
                        .param("branch", "kqldb_1")
                        .param("limit", "20")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/datasets/{dataset}/clusters: Spatial grid clustering groups points safely")
    void testSpatialClusters_ReturnsClusterPins() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/clusters")
                        .param("zoom", "8")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", notNullValue()))
                .andExpect(jsonPath("$.features", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.features[0].properties.is_cluster", is(true)))
                .andExpect(jsonPath("$.features[0].properties.point_count", greaterThan(0)))
                .andExpect(jsonPath("$.features[0].properties.dataset_key", is("tbl_road_sign")));
    }

    @Test
    @DisplayName("GIS filters preserve dataset-specific columns and parameter bindings")
    void testGeoAndClusters_WithDatasetFilters_Return200() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/geo")
                        .param("branch", "cuc_ql_duong_bo_1")
                        .param("status", "Đang khai thác")
                        .param("q", "bien")
                        .param("limit", "5")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")));

        mockMvc.perform(get("/api/datasets/tbl_bridge/clusters")
                        .param("minLon", "102")
                        .param("minLat", "8")
                        .param("maxLon", "110")
                        .param("maxLat", "24")
                        .param("q", "cau")
                        .param("zoom", "10")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")));
    }

    @Test
    @DisplayName("Large geometry dataset uses bounded EPSG:4326 GeoJSON response")
    void testLargeGeometryDataset_UsesBboxAndBoundedLimit() throws Exception {
        mockMvc.perform(get("/api/datasets/road_sphere_mirror/geo")
                        .param("bbox", "102,8,110,24")
                        .param("limit", "25")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", hasSize(lessThanOrEqualTo(25))))
                .andExpect(jsonPath("$.features[0].geometry.type", is("Point")));
    }

    @Test
    @DisplayName("GIS honors dataset RBAC before executing spatial query")
    void testGeoData_SensitiveDataset_ViewerGetsForbidden() throws Exception {
        mockMvc.perform(get("/api/datasets/maintenance_detail_baidoxe_chitiet/geo")
                        .param("bbox", "102,8,110,24")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("GET /api/datasets/gis/benchmark: Executes benchmark on tbl_road_sign without full dump")
    void testGisBenchmark_ExecutesWithoutFullDump() throws Exception {
        mockMvc.perform(get("/api/datasets/gis/benchmark")
                        .param("dataset1", "tbl_road_sign")
                        .param("dataset2", "road_sphere_mirror")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceNoFullLoad", is(true)))
                .andExpect(jsonPath("$.safetyAssessment", containsString("PASS")))
                .andExpect(jsonPath("$.datasets", hasSize(2)))
                .andExpect(jsonPath("$.datasets[0].datasetKey", is("tbl_road_sign")))
                .andExpect(jsonPath("$.datasets[0].totalDatasetRecords", is(222112)))
                .andExpect(jsonPath("$.datasets[0].bboxFeaturesReturned", lessThanOrEqualTo(2000)))
                .andExpect(jsonPath("$.datasets[0].compressionRatioPercent", greaterThan(90.0)))
                .andExpect(jsonPath("$.datasets[1].datasetKey", is("road_sphere_mirror")))
                .andExpect(jsonPath("$.datasets[1].totalDatasetRecords", greaterThan(50000)));
    }

    @Test
    @DisplayName("GET /api/datasets/tbl_km_post/geo: Road asset layer returns valid EPSG:4326 GeoJSON points within BBOX")
    void testGeoData_RoadKmPost_ReturnsFeaturesInBbox() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_km_post/geo")
                        .param("minLon", "102.0")
                        .param("minLat", "8.0")
                        .param("maxLon", "110.0")
                        .param("maxLat", "24.0")
                        .param("limit", "50")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", notNullValue()))
                .andExpect(jsonPath("$.features", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.features[0].type", is("Feature")))
                .andExpect(jsonPath("$.features[0].geometry.type", is("Point")))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", greaterThanOrEqualTo(102.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]", lessThanOrEqualTo(110.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", greaterThanOrEqualTo(8.0)))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]", lessThanOrEqualTo(24.0)))
                .andExpect(jsonPath("$.features[0].properties.dataset_key", is("tbl_km_post")));
    }

    @Test
    @DisplayName("GET /api/datasets/mst_national_road/geo: Road dataset with empty coordinates returns valid FeatureCollection without error")
    void testGeoData_RoadNetwork_HandlesEmptyCoordinatesSafely() throws Exception {
        mockMvc.perform(get("/api/datasets/mst_national_road/geo")
                        .param("bbox", "102,8,110,24")
                        .param("limit", "20")
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("FeatureCollection")))
                .andExpect(jsonPath("$.features", hasSize(0)));
    }
}
