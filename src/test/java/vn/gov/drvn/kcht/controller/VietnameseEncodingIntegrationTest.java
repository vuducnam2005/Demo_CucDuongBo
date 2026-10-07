package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class VietnameseEncodingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("P0.6: Database app_user và app_role không còn chứa dấu hỏi '?' thay thế tiếng Việt")
    void testDatabaseDirectEncodingNoQuestionMarks() {
        Integer corruptUserCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_user WHERE full_name LIKE '%?%'", Integer.class);
        assertEquals(0, corruptUserCount, "Bảng app_user không được chứa ký tự ? do lỗi encoding");

        Integer corruptRoleCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_role WHERE role_name LIKE '%?%' OR description LIKE '%?%'", Integer.class);
        assertEquals(0, corruptRoleCount, "Bảng app_role không được chứa ký tự ? do lỗi encoding");

        String adminName = jdbcTemplate.queryForObject(
                "SELECT full_name FROM app_user WHERE username = 'admin'", String.class);
        assertEquals("Quản trị viên Hệ thống", adminName);

        String roleName = jdbcTemplate.queryForObject(
                "SELECT role_name FROM app_role WHERE role_code = 'ROLE_ADMIN'", String.class);
        assertEquals("Quản trị viên Toàn quyền", roleName);
    }

    @Test
    @DisplayName("P0.6: API GET /api/auth/users trả về họ tên tiếng Việt chuẩn UTF-8 không có mojibake")
    void testUserApiReturnsCorrectVietnameseStrings() throws Exception {
        mockMvc.perform(get("/api/auth/users")
                        .param("q", "admin")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].fullName", is("Quản trị viên Hệ thống")))
                .andExpect(jsonPath("$.content[0].fullName", not(containsString("?"))))
                .andExpect(jsonPath("$.content[0].roleName", is("Quản trị viên Toàn quyền")));
    }

    @Test
    @DisplayName("P0.6: Kiểm thử các từ khóa bắt buộc tiếng Việt: Quản trị viên, Hệ thống, Đường quốc lộ, Cầu đường bộ, Tỉnh/thành phố")
    void testRequiredVietnameseKeywordsPreservation() throws Exception {
        // 1. Kiểm tra từ khóa 'Đường quốc lộ' (mst_national_road)
        mockMvc.perform(get("/api/datasets")
                        .param("q", "mst_national_road")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].datasetName", is("Đường quốc lộ")));

        // 2. Kiểm tra từ khóa 'Cầu' qua dataset tbl_bridge
        mockMvc.perform(get("/api/datasets")
                        .param("q", "tbl_bridge")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].datasetName", is("Cầu quốc lộ")));

        // 3. Kiểm tra từ khóa 'Quản trị viên' và 'Hệ thống' qua user API
        mockMvc.perform(get("/api/auth/users")
                        .param("q", "admin")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].fullName", is("Quản trị viên Hệ thống")))
                .andExpect(jsonPath("$.content[0].roleName", is("Quản trị viên Toàn quyền")));

        // 4. Kiểm tra chuỗi chứa 'Cầu đường bộ' và 'Tỉnh/thành phố' trực tiếp từ DB qua JDBC UTF-8
        String testPhrase = "Quản trị viên Hệ thống quản lý Tuyến Đường quốc lộ và Cầu đường bộ cấp Tỉnh/thành phố";
        String roundTrip = jdbcTemplate.queryForObject("SELECT ?::text", String.class, testPhrase);
        assertEquals(testPhrase, roundTrip, "Chuỗi tiếng Việt đầy đủ dấu phải bảo toàn tuyệt đối qua JDBC UTF-8");
    }
}
