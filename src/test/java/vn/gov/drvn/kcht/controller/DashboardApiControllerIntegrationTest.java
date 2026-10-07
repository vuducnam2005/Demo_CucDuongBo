package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.gov.drvn.kcht.dto.LoginRequestDto;
import vn.gov.drvn.kcht.security.LoginRateLimiter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        "spring.jpa.hibernate.ddl-auto=none",
        "kcht.security.allow-anonymous-admin=false"
})
@DisplayName("Kiểm thử tích hợp Giai đoạn 7 - Dashboard API và Thống kê tổng hợp phía Server")
public class DashboardApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    private String viewerToken;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "viewer_demo");
        LoginRequestDto loginDto = new LoginRequestDto("viewer_demo", "Viewer@2026!");
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();
        this.viewerToken = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("1. GET /api/dashboard/summary - Trả về số liệu tổng quan KPI đầy đủ")
    void testDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets", greaterThan(800000)))
                .andExpect(jsonPath("$.totalDatasets").value(658))
                .andExpect(jsonPath("$.physicalAssetDatasets").value(57))
                .andExpect(jsonPath("$.moduleDatasets").value(601))
                .andExpect(jsonPath("$.totalBridges").value(11631))
                .andExpect(jsonPath("$.totalRoadSigns").value(222112))
                .andExpect(jsonPath("$.totalNationalRoadRoutes").value(168))
                .andExpect(jsonPath("$.totalNationalRoadLengthKm", greaterThan(25000.0)))
                .andExpect(jsonPath("$.sourceDataset", notNullValue()))
                .andExpect(jsonPath("$.filter", notNullValue()))
                .andExpect(jsonPath("$.lastUpdated", notNullValue()));
    }

    @Test
    @DisplayName("2. GET /api/dashboard/stats/branches - Trả về phân bổ theo Chi nhánh / Khu QLĐB")
    void testBranchStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/branches")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$[0].branchId", notNullValue()))
                .andExpect(jsonPath("$[0].branchName", notNullValue()))
                .andExpect(jsonPath("$[0].totalAssets", greaterThan(10000)))
                .andExpect(jsonPath("$[0].sourceDataset").value("mv_dashboard_branch_stats"))
                .andExpect(jsonPath("$[0].filter", notNullValue()));
    }

    @Test
    @DisplayName("3. GET /api/dashboard/stats/datasets - Trả về top các tập dữ liệu tài sản lớn nhất")
    void testTopDatasets() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/datasets")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].datasetKey").value("tbl_road_sign"))
                .andExpect(jsonPath("$[0].totalRecords").value(222112))
                .andExpect(jsonPath("$[0].kind").value("asset"));
    }

    @Test
    @DisplayName("4. GET /api/dashboard/stats/road-signs - Trả về thống kê chuyên đề biển báo")
    void testRoadSignStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/road-signs")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSigns").value(222112))
                .andExpect(jsonPath("$.byBranch", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.byShape", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.sourceDataset").value("tbl_road_sign"));
    }

    @Test
    @DisplayName("5. GET /api/dashboard/stats/road-lengths - Trả về thống kê chiều dài quốc lộ")
    void testRoadLengthStats() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/road-lengths")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRoutes").value(168))
                .andExpect(jsonPath("$.totalLengthKm", greaterThan(27000.0)))
                .andExpect(jsonPath("$.longestRoutes", hasSize(10)))
                .andExpect(jsonPath("$.longestRoutes[0].routeName", containsString("QL.1")))
                .andExpect(jsonPath("$.distribution", hasSize(greaterThan(0))));
    }

    @Test
    @DisplayName("6. GET /api/dashboard/stats/recent-assets - Trả về danh sách tài sản cập nhật gần đây")
    void testRecentAssets() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats/recent-assets?limit=5")
                        .header("Authorization", "Bearer " + viewerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].datasetKey", notNullValue()))
                .andExpect(jsonPath("$[0].recordKey", notNullValue()))
                .andExpect(jsonPath("$[0].importedAt", notNullValue()));
    }

    @Test
    @DisplayName("7. Chưa đăng nhập bị từ chối truy cập (401 Unauthorized)")
    void testUnauthenticatedDenied() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
