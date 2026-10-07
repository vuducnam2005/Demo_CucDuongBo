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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.gov.drvn.kcht.dto.CreateUserRequestDto;
import vn.gov.drvn.kcht.dto.LoginRequestDto;
import vn.gov.drvn.kcht.security.LoginRateLimiter;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
@DisplayName("Kiểm thử tích hợp Giai đoạn 5 - Phân quyền RBAC (Truy cập đúng / Từ chối truy cập)")
public class RbacAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String createdUsername;

    @AfterEach
    void cleanupCreatedUserFixture() {
        if (createdUsername == null) {
            return;
        }

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM audit_log WHERE username = ?", createdUsername);
            jdbcTemplate.update("DELETE FROM app_user WHERE username = ?", createdUsername);
        });
        loginRateLimiter.resetAttempts("127.0.0.1", createdUsername);
        createdUsername = null;
    }

    private String getAccessToken(String username, String password) throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", username);
        LoginRequestDto loginDto = new LoginRequestDto(username, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("1. Quản trị viên (ROLE_ADMIN) có quyền xem danh sách người dùng và nhật ký kiểm toán (200 OK)")
    void testAdminAccessAllowed() throws Exception {
        String adminToken = getAccessToken("admin", "Admin@2026!");

        // Truy cập danh sách người dùng
        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(4)));

        // Truy cập nhật ký kiểm toán
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())));
    }

    @Test
    @DisplayName("2. Người xem (ROLE_VIEWER) bị từ chối truy cập quản trị người dùng và kiểm toán (403 Forbidden)")
    void testViewerAccessDeniedToAdminEndpoints() throws Exception {
        String viewerToken = getAccessToken("viewer_demo", "Viewer@2026!");

        // Cố gắng xem danh sách tài khoản
        mockMvc.perform(get("/api/auth/users")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // Cố gắng xem nhật ký kiểm toán
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("3. Lãnh đạo (ROLE_MANAGER) được xem Audit Log nhưng bị từ chối tạo người dùng mới (403 Forbidden)")
    void testManagerPermissions() throws Exception {
        String managerToken = getAccessToken("manager_demo", "Manager@2026!");

        // Được xem Audit Log
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Bị từ chối khi cố gắng tạo tài khoản mới (chỉ admin có USER:MANAGE_USERS)
        CreateUserRequestDto createUserDto = new CreateUserRequestDto();
        createUserDto.setUsername("unauthorized_create");
        createUserDto.setEmail("test_create@drvn.gov.vn");
        createUserDto.setPassword("Test@2026!");
        createUserDto.setFullName("Unauthorized Creation Attempt");
        createUserDto.setRoleCode("ROLE_VIEWER");

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("4. Quản trị viên (ROLE_ADMIN) tạo tài khoản mới và tài khoản mới đăng nhập thành công")
    void testAdminCreateUserAndLogin() throws Exception {
        String adminToken = getAccessToken("admin", "Admin@2026!");

        long timestamp = System.currentTimeMillis();
        String newUsername = "test_tech_" + timestamp;
        createdUsername = newUsername;
        String newEmail = "tech_" + timestamp + "@drvn.gov.vn";

        CreateUserRequestDto createUserDto = new CreateUserRequestDto();
        createUserDto.setUsername(newUsername);
        createUserDto.setEmail(newEmail);
        createUserDto.setPassword("TechSecret@2026!");
        createUserDto.setFullName("Kỹ sư Hiện trường Mới");
        createUserDto.setRoleCode("ROLE_EDITOR");
        createUserDto.setBranchId("kqldb_1");

        // Admin tạo người dùng mới (HTTP 201)
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createUserDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(newUsername))
                .andExpect(jsonPath("$.role").value("ROLE_EDITOR"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        CreateUserRequestDto weakPasswordRequest = new CreateUserRequestDto();
        weakPasswordRequest.setUsername("weak_password_" + timestamp);
        weakPasswordRequest.setEmail("weak_password_" + timestamp + "@drvn.gov.vn");
        weakPasswordRequest.setPassword("weakpass");
        weakPasswordRequest.setFullName("Mật khẩu yếu");
        weakPasswordRequest.setRoleCode("ROLE_VIEWER");

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakPasswordRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasItem(org.hamcrest.Matchers.containsString("password:"))));

        // Người dùng mới đăng nhập với mật khẩu vừa tạo
        loginRateLimiter.resetAttempts("127.0.0.1", newUsername);
        LoginRequestDto newLoginDto = new LoginRequestDto(newUsername, "TechSecret@2026!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.username").value(newUsername))
                .andExpect(jsonPath("$.user.role").value("ROLE_EDITOR"));
    }

    @Test
    @DisplayName("5. Truy cập không có Token vào endpoint bảo vệ trả về HTTP 401 Unauthorized")
    void testAnonymousAccessDenied() throws Exception {
        mockMvc.perform(get("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("6. Upload tài liệu từ chối Viewer và cho phép Editor đi qua lớp phân quyền")
    void testDocumentUploadRoleEnforcement() throws Exception {
        MockMultipartFile emptyPdf = new MockMultipartFile(
                "file", "empty.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[0]);

        String viewerToken = getAccessToken("viewer_demo", "Viewer@2026!");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(emptyPdf)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        String editorToken = getAccessToken("editor_demo", "Editor@2026!");
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(emptyPdf)
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("7. Danh sách người dùng trả contract phân trang, tìm kiếm toàn hệ thống và kết quả rỗng")
    void testUserPaginationAndGlobalSearchContract() throws Exception {
        String adminToken = getAccessToken("admin", "Admin@2026!");

        mockMvc.perform(get("/api/auth/users")
                        .param("page", "0")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").isBoolean())
                .andExpect(jsonPath("$.content[0].active").isBoolean())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/auth/users")
                        .param("page", "0")
                        .param("size", "10")
                        .param("q", "viewer_demo")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("viewer_demo"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/auth/users")
                        .param("q", "ROLE_MANAGER")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].role", hasItem("ROLE_MANAGER")));

        mockMvc.perform(get("/api/auth/users")
                        .param("q", "__khong_co_tai_khoan__")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }
}
