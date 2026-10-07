package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("P2.1 - Kiểm thử thuộc tính bảo mật của Cookie Refresh Token")
public class AuthCookieSecurityTest {

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
            "kcht.security.cookie.secure=false",
            "kcht.security.cookie.same-site=Lax",
            "kcht.security.cookie.path=/api/auth",
            "kcht.security.jwt.refresh-token-expiration-ms=604800000"
    })
    @Nested
    @DisplayName("1. Môi trường Development (HTTP cục bộ, Secure=false)")
    class DevelopmentCookieTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private LoginRateLimiter loginRateLimiter;

        @Test
        @DisplayName("Cookie có HttpOnly, SameSite=Lax, Path=/api/auth và không có cờ Secure trên HTTP")
        void testDevelopmentCookieAttributes() throws Exception {
            loginRateLimiter.resetAttempts("127.0.0.1", "admin");

            LoginRequestDto loginDto = new LoginRequestDto("admin", "Admin@2026!");
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
            assertFalse(setCookieHeaders.isEmpty());

            String refreshCookie = setCookieHeaders.stream()
                    .filter(c -> c.startsWith("kcht_refresh_token="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Không tìm thấy Set-Cookie kcht_refresh_token"));

            assertTrue(refreshCookie.contains("HttpOnly"), "Cookie phải có cờ HttpOnly");
            assertTrue(refreshCookie.contains("Path=/api/auth"), "Path phải là /api/auth");
            assertTrue(refreshCookie.contains("SameSite=Lax"), "SameSite phải là Lax");
            assertTrue(refreshCookie.contains("Max-Age="), "Cookie phải có Max-Age");
            assertFalse(refreshCookie.contains("Secure;"), "Môi trường dev không được bật Secure");
        }
    }

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
            "kcht.security.cookie.secure=true",
            "kcht.security.cookie.same-site=Strict",
            "kcht.security.cookie.domain=drvn.gov.vn",
            "kcht.security.cookie.path=/api/auth",
            "kcht.security.jwt.refresh-token-expiration-ms=604800000"
    })
    @Nested
    @DisplayName("2. Môi trường Production HTTPS (Secure=true, SameSite=Strict, Domain)")
    class ProductionCookieTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private LoginRateLimiter loginRateLimiter;

        @Test
        @DisplayName("Cookie bắt buộc có Secure=true, SameSite=Strict và Domain khi cấu hình Production")
        void testProductionCookieAttributes() throws Exception {
            loginRateLimiter.resetAttempts("127.0.0.1", "admin");

            LoginRequestDto loginDto = new LoginRequestDto("admin", "Admin@2026!");
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDto)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
            assertFalse(setCookieHeaders.isEmpty());

            String refreshCookie = setCookieHeaders.stream()
                    .filter(c -> c.startsWith("kcht_refresh_token="))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Không tìm thấy Set-Cookie kcht_refresh_token"));

            assertTrue(refreshCookie.contains("HttpOnly"), "Cookie phải có HttpOnly");
            assertTrue(refreshCookie.contains("Secure"), "Production bắt buộc phải có cờ Secure");
            assertTrue(refreshCookie.contains("SameSite=Strict"), "SameSite phải là Strict");
            assertTrue(refreshCookie.contains("Domain=drvn.gov.vn"), "Domain phải là drvn.gov.vn");
            assertTrue(refreshCookie.contains("Path=/api/auth"), "Path phải là /api/auth");
        }
    }
}
