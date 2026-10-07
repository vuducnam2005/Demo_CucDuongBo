package vn.gov.drvn.kcht.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenProvider(
            @Value("${kcht.security.jwt.secret}") String jwtSecret,
            @Value("${kcht.security.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs,
            @Value("${kcht.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {

        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret phải có tối thiểu 32 byte");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    /**
     * Tạo JWT Access Token ngắn hạn với đầy đủ thông tin người dùng và quyền hạn.
     */
    public String generateAccessToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId())
                .claim("fullName", userPrincipal.getFullName())
                .claim("role", userPrincipal.getRoleCode())
                .claim("roleName", userPrincipal.getRoleName())
                .claim("permissions", userPrincipal.getPermissions())
                .claim("branchId", userPrincipal.getBranchId())
                .claim("organizationId", userPrincipal.getOrganizationId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Tạo chuỗi Refresh Token ngẫu nhiên an toàn (64 ký tự hex).
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    /**
     * Tính toán mã băm SHA-256 của refresh token để lưu vào database (không lưu bản rõ).
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không tìm thấy thuật toán băm SHA-256", e);
        }
    }

    /**
     * Tính thời điểm hết hạn của Refresh Token.
     */
    public OffsetDateTime calculateRefreshTokenExpiry() {
        return OffsetDateTime.ofInstant(
                Instant.now().plusMillis(refreshTokenExpirationMs),
                ZoneOffset.UTC
        );
    }

    /**
     * Lấy thời gian sống (giây) của Access Token.
     */
    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMs / 1000;
    }

    /**
     * Trích xuất username từ JWT token.
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Trích xuất toàn bộ claims từ JWT token.
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Kiểm tra tính hợp lệ của JWT token.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Chữ ký JWT không hợp lệ hoặc bị thay đổi");
        } catch (ExpiredJwtException e) {
            log.warn("JWT token đã hết hạn");
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token không được hỗ trợ");
        } catch (IllegalArgumentException e) {
            log.warn("Chuỗi JWT rỗng hoặc không đúng định dạng");
        }
        return false;
    }
}
