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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
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
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Kiểm thử tích hợp Giai đoạn 8 - Cây phân cấp KCHT và Xuất dữ liệu CSV")
public class DatasetApiTreeAndExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    private String authToken;

    @BeforeEach
    void setUp() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "viewer_demo");
        LoginRequestDto loginDto = new LoginRequestDto("viewer_demo", "Viewer@2026!");
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();
        this.authToken = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("1. GET /api/datasets/tree - Trả về cây danh mục phân cấp với các nhánh nghiệp vụ chính")
    void testGetDatasetTreeRoot() throws Exception {
        mockMvc.perform(get("/api/datasets/tree")
                        .header("Authorization", "Bearer " + authToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].key").value("group_roads"))
                .andExpect(jsonPath("$[0].children", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].children[0].datasetKey").value("mst_national_road"))
                .andExpect(jsonPath("$[1].key").value("group_bridges"))
                .andExpect(jsonPath("$[1].children[0].datasetKey").value("tbl_bridge"))
                .andExpect(jsonPath("$[2].key").value("group_traffic_signs"))
                .andExpect(jsonPath("$[2].children[0].datasetKey").value("tbl_road_sign"));
    }

    @Test
    @DisplayName("2. GET /api/datasets/tree?parent=group_all_assets - Lazy load danh sách 57 tập tài sản vật thể")
    void testGetDatasetTreeLazyLoadAssets() throws Exception {
        mockMvc.perform(get("/api/datasets/tree")
                        .param("parent", "group_all_assets")
                        .header("Authorization", "Bearer " + authToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(50))))
                .andExpect(jsonPath("$[0].isLeaf").value(true))
                .andExpect(jsonPath("$[0].kind").value("asset"));
    }

    @Test
    @DisplayName("3. GET /api/datasets/tbl_bridge/export - Xuất file CSV có UTF-8 BOM và headers đầy đủ")
    void testExportBridgesToCsv() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/datasets/tbl_bridge/export")
                        .param("limit", "20")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("tbl_bridge_export.csv")))
                .andReturn();

        byte[] contentBytes = result.getResponse().getContentAsByteArray();
        assertNotNull(contentBytes);
        assertTrue(contentBytes.length > 0);

        // Kiểm tra UTF-8 BOM: 0xEF, 0xBB, 0xBF
        assertEquals((byte) 0xEF, contentBytes[0]);
        assertEquals((byte) 0xBB, contentBytes[1]);
        assertEquals((byte) 0xBF, contentBytes[2]);

        String csvString = new String(contentBytes, StandardCharsets.UTF_8);
        assertTrue(csvString.contains("STT,Mã bản ghi,Tên / Nhãn tài sản,Đơn vị quản lý,Trạng thái"));
    }

    @Test
    @DisplayName("4. GET /api/datasets/tbl_road_sign/export - Xuất biển báo có lọc keyword")
    void testExportRoadSignsWithKeyword() throws Exception {
        mockMvc.perform(get("/api/datasets/tbl_road_sign/export")
                        .param("q", "kqldb_1")
                        .param("limit", "10")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("tbl_road_sign_export.csv")));
    }
}
