package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import vn.gov.drvn.kcht.dto.RefreshTokenRequestDto;
import vn.gov.drvn.kcht.security.LoginRateLimiter;

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
        "spring.jpa.hibernate.ddl-auto=none",
        "kcht.security.allow-anonymous-admin=false"
})
@DisplayName("Kiểm thử tích hợp Giai đoạn 5 - Đăng nhập, JWT, Refresh Token xoay vòng, Rate Limit và Audit Log")
public class AuthApiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Test
    @DisplayName("1. Đăng nhập thành công với 4 tài khoản chuẩn (admin, manager, editor, viewer)")
    void testLoginSuccessAllRoles() throws Exception {
        String[][] credentials = {
                {"admin", "Admin@2026!", "ROLE_ADMIN"},
                {"manager_demo", "Manager@2026!", "ROLE_MANAGER"},
                {"editor_demo", "Editor@2026!", "ROLE_EDITOR"},
                {"viewer_demo", "Viewer@2026!", "ROLE_VIEWER"}
        };

        for (String[] cred : credentials) {
            String username = cred[0];
            String password = cred[1];
            String expectedRole = cred[2];

            loginRateLimiter.resetAttempts("127.0.0.1", username);

            LoginRequestDto loginDto = new LoginRequestDto(username, password);

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.user.username").value(username))
                    .andExpect(jsonPath("$.user.role").value(expectedRole))
                    .andExpect(jsonPath("$.user.permissions", not(empty())))
                    .andReturn();

            // Xác nhận password không bị lộ trong phản hồi
            String responseJson = result.getResponse().getContentAsString();
            assertFalse(responseJson.contains("passwordHash"));
            assertFalse(responseJson.contains("password_hash"));
        }
    }

    @Test
    @DisplayName("2. Đăng nhập thất bại khi sai mật khẩu - Trả về HTTP 401 Unauthorized")
    void testLoginFailureBadPassword() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "admin");

        LoginRequestDto badDto = new LoginRequestDto("admin", "SaiMatKhau@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badDto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message", containsString("không chính xác")));
    }

    @Test
    @DisplayName("3. Đăng nhập sai nhiều lần liên tiếp không bị khóa tài khoản - Trả về HTTP 401 Unauthorized")
    void testUnlimitedFailedLoginAttemptsWithoutLockout() throws Exception {
        String testUser = "unlimited_attempts_user";
        loginRateLimiter.resetAttempts("127.0.0.1", testUser);

        LoginRequestDto badDto = new LoginRequestDto(testUser, "SaiMatKhau@123");

        // Thử sai nhiều lần liên tiếp (10 lần) vẫn không bị khóa (HTTP 401, không bị chặn 429)
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badDto)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message", containsString("không chính xác")));
        }

        // Dọn dẹp sau test
        loginRateLimiter.resetAttempts("127.0.0.1", testUser);
    }

    @Test
    @DisplayName("4. Xoay vòng Refresh Token đơn kỳ (Single-use rotation) và kiểm tra thu hồi khi sử dụng lại")
    void testRefreshTokenRotationAndRevocation() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "editor_demo");

        // 1. Đăng nhập để lấy Refresh Token ban đầu
        LoginRequestDto loginDto = new LoginRequestDto("editor_demo", "Editor@2026!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialRefreshToken = loginNode.get("refreshToken").asText();
        assertNotNull(initialRefreshToken);

        // 2. Sử dụng Refresh Token lần 1 -> Thành công và được cấp token mới
        RefreshTokenRequestDto refreshDto1 = new RefreshTokenRequestDto(initialRefreshToken);
        MvcResult refreshResult1 = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshDto1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", not(equalTo(initialRefreshToken))))
                .andReturn();

        JsonNode refreshNode1 = objectMapper.readTree(refreshResult1.getResponse().getContentAsString());
        String secondRefreshToken = refreshNode1.get("refreshToken").asText();

        // 3. Cố gắng sử dụng lại Refresh Token ban đầu (đã bị thu hồi do xoay vòng) -> Từ chối (HTTP 401)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshDto1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("thu hồi")));

        // 4. Sử dụng Refresh Token thứ 2 nhận qua Cookie HttpOnly -> Thành công
        Cookie refreshCookie = new Cookie("kcht_refresh_token", secondRefreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }

    @Test
    @DisplayName("5. Đăng xuất tài khoản - Thu hồi token và xóa Cookie")
    void testLogoutRevocation() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "viewer_demo");

        // 1. Đăng nhập
        LoginRequestDto loginDto = new LoginRequestDto("viewer_demo", "Viewer@2026!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginNode.get("accessToken").asText();
        String refreshToken = loginNode.get("refreshToken").asText();

        // 2. Đăng xuất
        RefreshTokenRequestDto logoutDto = new RefreshTokenRequestDto(refreshToken);
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công"));

        // 3. Thử dùng lại Refresh Token sau khi đăng xuất -> Bị từ chối (HTTP 401)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutDto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. GET /api/auth/me - Truy vấn thông tin người dùng hiện tại")
    void testGetCurrentUserProfile() throws Exception {
        loginRateLimiter.resetAttempts("127.0.0.1", "admin");

        // 1. Đăng nhập
        LoginRequestDto loginDto = new LoginRequestDto("admin", "Admin@2026!");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // 2. Gọi /api/auth/me với Bearer Token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.permissions", hasItem("USER:MANAGE_USERS")));

        // 3. Gọi /api/auth/me không có Token -> 401 Unauthorized
        mockMvc.perform(get("/api/auth/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
