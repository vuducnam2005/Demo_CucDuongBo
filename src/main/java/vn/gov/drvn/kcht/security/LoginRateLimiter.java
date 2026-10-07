package vn.gov.drvn.kcht.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.exception.TooManyRequestsException;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private final int maxAttempts;
    private final long lockoutDurationMs;
    private final ConcurrentHashMap<String, AttemptInfo> attemptsMap = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${kcht.security.rate-limit.login-max-attempts:5}") int maxAttempts,
            @Value("${kcht.security.rate-limit.login-lockout-duration-ms:900000}") long lockoutDurationMs) {
        this.maxAttempts = maxAttempts;
        this.lockoutDurationMs = lockoutDurationMs;
    }

    private static class AttemptInfo {
        int failedCount;
        long firstAttemptTime;
        long lockedUntil;

        AttemptInfo(long now) {
            this.failedCount = 1;
            this.firstAttemptTime = now;
            this.lockedUntil = 0;
        }
    }

    /**
     * Kiểm tra trạng thái giới hạn tần suất đăng nhập.
     *
     * @param clientIp Địa chỉ IP của máy khách
     * @param username Tên đăng nhập
     * @throws TooManyRequestsException nếu bị khóa do vượt quá số lần thử
     */
    public void checkRateLimit(String clientIp, String username) {
        long now = System.currentTimeMillis();
        String ipKey = "ip:" + (clientIp != null ? clientIp : "unknown");
        String userKey = "user:" + (username != null ? username.toLowerCase().trim() : "unknown");

        checkKey(ipKey, now);
        if (username != null && !username.isBlank()) {
            checkKey(userKey, now);
        }
    }

    private void checkKey(String key, long now) {
        AttemptInfo info = attemptsMap.get(key);
        if (info == null) {
            return;
        }

        if (info.lockedUntil > now) {
            long remainingSeconds = (info.lockedUntil - now) / 1000;
            log.warn("Tài khoản hoặc IP [{}] bị khóa đăng nhập do rate limit (còn {} giây)", key, remainingSeconds);
            throw new TooManyRequestsException(
                    String.format("Đã vượt quá số lần đăng nhập sai cho phép. Vui lòng thử lại sau %d giây.", remainingSeconds)
            );
        }

        // Nếu đã hết hạn khóa thì tự động dọn dẹp
        if (info.lockedUntil > 0 && info.lockedUntil <= now) {
            attemptsMap.remove(key);
        }
    }

    /**
     * Ghi nhận một lần đăng nhập thất bại.
     */
    public void recordFailedAttempt(String clientIp, String username) {
        long now = System.currentTimeMillis();
        String ipKey = "ip:" + (clientIp != null ? clientIp : "unknown");
        recordFailedForKey(ipKey, now);

        if (username != null && !username.isBlank()) {
            String userKey = "user:" + username.toLowerCase().trim();
            recordFailedForKey(userKey, now);
        }
    }

    private void recordFailedForKey(String key, long now) {
        attemptsMap.compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptInfo(now);
            }

            // Nếu cửa sổ thời gian đã quá hạn khóa, đặt lại bộ đếm
            if (now - existing.firstAttemptTime > lockoutDurationMs) {
                return new AttemptInfo(now);
            }

            existing.failedCount++;
            if (existing.failedCount >= maxAttempts) {
                existing.lockedUntil = now + lockoutDurationMs;
                log.warn("Kích hoạt khóa rate limit cho [{}]: {} lần thử sai liên tiếp. Khóa trong {} ms.",
                        k, existing.failedCount, lockoutDurationMs);
            }
            return existing;
        });
    }

    /**
     * Đặt lại bộ đếm khi đăng nhập thành công.
     */
    public void resetAttempts(String clientIp, String username) {
        if (clientIp != null) {
            attemptsMap.remove("ip:" + clientIp);
        }
        if (username != null && !username.isBlank()) {
            attemptsMap.remove("user:" + username.toLowerCase().trim());
        }
    }
}
