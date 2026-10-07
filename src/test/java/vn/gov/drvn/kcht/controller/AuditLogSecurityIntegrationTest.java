package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.gov.drvn.kcht.dto.LoginRequestDto;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.repository.AuditLogRepository;
import vn.gov.drvn.kcht.security.LoginRateLimiter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
@DisplayName("Kiểm thử bảo mật Nhật ký Kiểm toán - Bảo vệ an toàn bí mật, không ghi log credential hoặc token")
public class AuditLogSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private LoginRateLimiter loginRateLimiter;

    @Test
    @DisplayName("Xác minh Audit Log ghi nhận sự kiện nhưng KHÔNG rò rỉ mật khẩu, token hay bí mật")
    void testAuditLogSanitizationAndNoCredentialLeakage() throws Exception {
        String testUsername = "audit_security_user";
        String sensitivePassword = "SuperSecretPassword123!@#";

        loginRateLimiter.resetAttempts("127.0.0.1", testUsername);

        // 1. Thử đăng nhập sai mật khẩu để kích hoạt LOGIN_FAILED audit event
        LoginRequestDto failedLogin = new LoginRequestDto(testUsername, sensitivePassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failedLogin)))
                .andExpect(status().isUnauthorized());

        // 2. Đăng nhập thành công với admin để kích hoạt LOGIN_SUCCESS
        LoginRequestDto successLogin = new LoginRequestDto("admin", "Admin@2026!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(successLogin)))
                .andExpect(status().isOk());

        // 3. Quét toàn bộ các bản ghi trong audit_log để kiểm tra tính toàn vẹn
        List<AuditLogEntity> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Audit log không được rỗng");

        for (AuditLogEntity entry : logs) {
            String newValues = entry.getNewValues() != null ? entry.getNewValues() : "";
            String oldValues = entry.getOldValues() != null ? entry.getOldValues() : "";

            // Tuyệt đối không chứa mật khẩu bản rõ của người dùng
            assertFalse(newValues.contains(sensitivePassword),
                    "Audit log new_values bị lộ mật khẩu người dùng!");
            assertFalse(oldValues.contains(sensitivePassword),
                    "Audit log old_values bị lộ mật khẩu người dùng!");
            assertFalse(newValues.contains("Admin@2026!"),
                    "Audit log new_values bị lộ mật khẩu admin!");

            // Không chứa các từ khóa nhạy cảm trong JSON payload
            assertFalse(newValues.toLowerCase().contains("\"password\""),
                    "Audit log new_values chứa trường 'password'!");
            assertFalse(newValues.toLowerCase().contains("\"token\""),
                    "Audit log new_values chứa trường 'token'!");
            assertFalse(newValues.toLowerCase().contains("\"secret\""),
                    "Audit log new_values chứa trường 'secret'!");
        }
    }
}
