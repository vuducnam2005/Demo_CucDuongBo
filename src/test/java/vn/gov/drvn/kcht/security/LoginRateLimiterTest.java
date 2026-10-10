package vn.gov.drvn.kcht.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.gov.drvn.kcht.exception.TooManyRequestsException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    @Test
    @DisplayName("1. Khi tính năng bị tắt (enabled = false): Nhập sai bao nhiêu lần cũng không bị khóa")
    void testWhenDisabled_NeverThrowsExceptionEvenAfterManyAttempts() {
        LoginRateLimiter limiter = new LoginRateLimiter(false, 0, 0);

        String ip = "192.168.1.100";
        String user = "test_user";

        // Thử sai 50 lần liên tiếp
        for (int i = 0; i < 50; i++) {
            limiter.recordFailedAttempt(ip, user);
        }

        // Kiểm tra không phát sinh lỗi khóa tài khoản
        assertDoesNotThrow(() -> limiter.checkRateLimit(ip, user));
    }

    @Test
    @DisplayName("2. Khi maxAttempts = 0: Không khóa tài khoản")
    void testWhenZeroAttempts_NeverThrowsException() {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 0, 900000);

        String ip = "192.168.1.101";
        String user = "another_user";

        for (int i = 0; i < 20; i++) {
            limiter.recordFailedAttempt(ip, user);
        }

        assertDoesNotThrow(() -> limiter.checkRateLimit(ip, user));
    }

    @Test
    @DisplayName("3. Khi tính năng được bật (enabled = true, maxAttempts = 5): Khóa khi vượt quá số lần cho phép")
    void testWhenEnabled_LocksOutAfterMaxAttempts() {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 5, 900000);

        String ip = "192.168.1.102";
        String user = "rate_limited_user";

        for (int i = 0; i < 4; i++) {
            limiter.recordFailedAttempt(ip, user);
            assertDoesNotThrow(() -> limiter.checkRateLimit(ip, user));
        }

        // Lần thứ 5 đạt ngưỡng khóa
        limiter.recordFailedAttempt(ip, user);
        assertThrows(TooManyRequestsException.class, () -> limiter.checkRateLimit(ip, user));
    }

    @Test
    @DisplayName("4. Khi tính năng được bật: resetAttempts sẽ xóa khóa thành công")
    void testWhenEnabled_ResetAttemptsClearsLockout() {
        LoginRateLimiter limiter = new LoginRateLimiter(true, 3, 900000);

        String ip = "192.168.1.103";
        String user = "reset_user";

        for (int i = 0; i < 3; i++) {
            limiter.recordFailedAttempt(ip, user);
        }

        assertThrows(TooManyRequestsException.class, () -> limiter.checkRateLimit(ip, user));

        limiter.resetAttempts(ip, user);
        assertDoesNotThrow(() -> limiter.checkRateLimit(ip, user));
    }
}
