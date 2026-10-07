package vn.gov.drvn.kcht.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.security.UserPrincipal;
import vn.gov.drvn.kcht.service.AuthService;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "Authentication & Users", description = "Đăng nhập, phân quyền, quản lý phiên và quản trị tài khoản cán bộ")
public class AuthApiController {

    private final AuthService authService;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final String cookieDomain;
    private final String cookiePath;
    private final long refreshTokenExpirationMs;
    private final String activeProfiles;

    public AuthApiController(
            AuthService authService,
            @Value("${kcht.security.cookie.name}") String cookieName,
            @Value("${kcht.security.cookie.secure}") boolean cookieSecure,
            @Value("${kcht.security.cookie.same-site}") String cookieSameSite,
            @Value("${kcht.security.cookie.domain}") String cookieDomain,
            @Value("${kcht.security.cookie.path}") String cookiePath,
            @Value("${kcht.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs,
            @Value("${spring.profiles.active:}") String activeProfiles) {
        this.authService = authService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.cookieDomain = cookieDomain;
        this.cookiePath = cookiePath;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.activeProfiles = activeProfiles;
        validateCookieConfiguration();
    }

    private void validateCookieConfiguration() {
        if (cookieName == null || cookieName.isBlank() || cookieSameSite == null || cookieSameSite.isBlank()
                || cookiePath == null || cookiePath.isBlank()) {
            throw new IllegalStateException("Cấu hình refresh cookie chưa đầy đủ");
        }
        if (!Set.of("Lax", "Strict", "None").contains(cookieSameSite)) {
            throw new IllegalStateException("REFRESH_COOKIE_SAME_SITE không hợp lệ");
        }
        boolean production = Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch("prod"::equalsIgnoreCase);
        if (production && !cookieSecure) {
            throw new IllegalStateException("Production bắt buộc REFRESH_COOKIE_SECURE=true");
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống",
            description = "Xác thực tài khoản cán bộ, kiểm tra Rate Limit chống tấn công dò mật khẩu, trả về JWT Access Token ngắn hạn và Refresh Token xoay vòng.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng nhập thành công",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Sai tên đăng nhập hoặc mật khẩu",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "429", description = "Quá số lần thử cho phép (Rate Limit)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        AuthResponseDto responseDto = authService.login(request, clientIp, userAgent);

        // Thiết lập Refresh Token an toàn vào HttpOnly Cookie
        setRefreshTokenCookie(httpResponse, responseDto.getRefreshToken(), Duration.ofMillis(refreshTokenExpirationMs));

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới Access Token",
            description = "Sử dụng Refresh Token để lấy Access Token mới theo cơ chế xoay vòng đơn kỳ (Single-use rotation). Nhận token từ request body hoặc HttpOnly cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Làm mới thành công",
                    content = @Content(schema = @Schema(implementation = AuthResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Refresh Token không hợp lệ hoặc đã hết hạn",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<AuthResponseDto> refreshToken(
            @RequestBody(required = false) RefreshTokenRequestDto request,
            @CookieValue(name = "${kcht.security.cookie.name}", required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String tokenToUse = (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank())
                ? request.getRefreshToken()
                : cookieRefreshToken;

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        AuthResponseDto responseDto = authService.refreshToken(tokenToUse, clientIp, userAgent);

        setRefreshTokenCookie(httpResponse, responseDto.getRefreshToken(), Duration.ofMillis(refreshTokenExpirationMs));

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất tài khoản",
            description = "Thu hồi Refresh Token và xóa cookie HttpOnly để kết thúc phiên đăng nhập.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Đăng xuất thành công")
    })
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequestDto request,
            @CookieValue(name = "${kcht.security.cookie.name}", required = false) String cookieRefreshToken,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        String tokenToRevoke = (request != null && request.getRefreshToken() != null)
                ? request.getRefreshToken()
                : cookieRefreshToken;

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        authService.logout(tokenToRevoke, principal, clientIp, userAgent);

        // Xóa cookie HttpOnly
        setRefreshTokenCookie(httpResponse, "", Duration.ZERO);

        return ResponseEntity.ok(Map.of("message", "Đăng xuất thành công"));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại",
            description = "Trả về hồ sơ cán bộ đăng nhập cùng vai trò và toàn bộ danh mục quyền chi tiết.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = UserSummaryDto.class))),
            @ApiResponse(responseCode = "401", description = "Chưa xác thực")
    })
    public ResponseEntity<UserSummaryDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getCurrentUser(principal));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('USER:MANAGE_USERS')")
    @Operation(summary = "Quản trị danh sách người dùng",
            description = "Dành riêng cho cán bộ Quản trị và Lãnh đạo tra cứu danh sách tài khoản phân quyền.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    public ResponseEntity<PagedResponse<UserSummaryDto>> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Tìm trên tên đăng nhập, họ tên, email, mã hoặc tên vai trò")
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(authService.getUsers(page, size, q));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USER:MANAGE_USERS')")
    @Operation(summary = "Thêm mới tài khoản cán bộ",
            description = "Dành riêng cho Quản trị viên (ROLE_ADMIN) tạo tài khoản mới với mật khẩu mã hóa BCrypt.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo mới thành công",
                    content = @Content(schema = @Schema(implementation = UserSummaryDto.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu hoặc mã vai trò không hợp lệ"),
            @ApiResponse(responseCode = "403", description = "Không có quyền quản trị")
    })
    public ResponseEntity<UserSummaryDto> createUser(
            @Valid @RequestBody CreateUserRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        String currentAdmin = principal != null ? principal.getUsername() : "system_admin";

        UserSummaryDto created = authService.createUser(request, currentAdmin, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(maxAge)
                .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookieBuilder.domain(cookieDomain);
        }

        ResponseCookie cookie = cookieBuilder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
