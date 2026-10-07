package vn.gov.drvn.kcht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.entity.AppPermissionEntity;
import vn.gov.drvn.kcht.entity.AppRefreshTokenEntity;
import vn.gov.drvn.kcht.entity.AppRoleEntity;
import vn.gov.drvn.kcht.entity.AppUserEntity;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.exception.UnauthorizedException;
import vn.gov.drvn.kcht.repository.AppPermissionRepository;
import vn.gov.drvn.kcht.repository.AppRefreshTokenRepository;
import vn.gov.drvn.kcht.repository.AppRoleRepository;
import vn.gov.drvn.kcht.repository.AppUserRepository;
import vn.gov.drvn.kcht.security.JwtTokenProvider;
import vn.gov.drvn.kcht.security.LoginRateLimiter;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLogService auditLogService;
    private final AppUserRepository appUserRepository;
    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final AppRefreshTokenRepository appRefreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       LoginRateLimiter loginRateLimiter,
                       AuditLogService auditLogService,
                       AppUserRepository appUserRepository,
                       AppRoleRepository appRoleRepository,
                       AppPermissionRepository appPermissionRepository,
                       AppRefreshTokenRepository appRefreshTokenRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginRateLimiter = loginRateLimiter;
        this.auditLogService = auditLogService;
        this.appUserRepository = appUserRepository;
        this.appRoleRepository = appRoleRepository;
        this.appPermissionRepository = appPermissionRepository;
        this.appRefreshTokenRepository = appRefreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Xác thực đăng nhập, kiểm tra rate limit, cấp phát JWT Access Token và Refresh Token bảo mật.
     */
    @Transactional
    public AuthResponseDto login(LoginRequestDto request, String ipAddress, String userAgent) {
        String username = request.getUsername().trim();

        // 1. Kiểm tra giới hạn tần suất đăng nhập (phòng chống tấn công brute-force)
        loginRateLimiter.checkRateLimit(ipAddress, username);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            loginRateLimiter.recordFailedAttempt(ipAddress, username);
            auditLogService.recordAuthEvent(username, null, "LOGIN_FAILED", ipAddress, userAgent,
                    Map.of("reason", "Sai tên đăng nhập hoặc mật khẩu"));
            throw new UnauthorizedException("Tên đăng nhập hoặc mật khẩu không chính xác");
        } catch (DisabledException ex) {
            auditLogService.recordAuthEvent(username, null, "LOGIN_FAILED", ipAddress, userAgent,
                    Map.of("reason", "Tài khoản đang bị khóa"));
            throw new UnauthorizedException("Tài khoản người dùng đã bị vô hiệu hóa");
        } catch (AuthenticationException ex) {
            loginRateLimiter.recordFailedAttempt(ipAddress, username);
            auditLogService.recordAuthEvent(username, null, "LOGIN_FAILED", ipAddress, userAgent,
                    Map.of("reason", "Lỗi xác thực hệ thống"));
            throw new UnauthorizedException("Xác thực không thành công: " + ex.getMessage());
        }

        // 2. Đặt lại bộ đếm khi đăng nhập thành công
        loginRateLimiter.resetAttempts(ipAddress, username);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        // 3. Sinh JWT Access Token ngắn hạn
        String accessToken = jwtTokenProvider.generateAccessToken(principal);

        // 4. Sinh Refresh Token ngẫu nhiên an toàn và lưu mã băm SHA-256 vào database
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = jwtTokenProvider.hashToken(refreshToken);
        OffsetDateTime expiresAt = jwtTokenProvider.calculateRefreshTokenExpiry();

        AppRefreshTokenEntity tokenEntity = new AppRefreshTokenEntity(
                principal.getId(), tokenHash, expiresAt, ipAddress, userAgent
        );
        appRefreshTokenRepository.save(tokenEntity);

        // 5. Ghi nhật ký kiểm toán đăng nhập thành công
        auditLogService.recordAuthEvent(principal.getUsername(), principal.getId(), "LOGIN_SUCCESS",
                ipAddress, userAgent, Map.of("role", principal.getRoleCode()));

        UserSummaryDto userSummary = toUserSummary(principal);

        return new AuthResponseDto(
                accessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                refreshToken,
                userSummary
        );
    }

    /**
     * Làm mới token bằng cơ chế xoay vòng đơn kỳ (Single-use Refresh Token Rotation).
     */
    @Transactional
    public AuthResponseDto refreshToken(String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token không được để trống");
        }

        String tokenHash = jwtTokenProvider.hashToken(refreshToken);
        AppRefreshTokenEntity tokenEntity = appRefreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    auditLogService.recordAuthEvent(null, null, "REFRESH_TOKEN_REJECTED", ipAddress, userAgent,
                            Map.of("reason", "Không tìm thấy refresh token"));
                    return new UnauthorizedException("Refresh token không hợp lệ hoặc không tồn tại");
                });

        if (tokenEntity.getRevoked()) {
            // Nghi ngờ token bị đánh cắp: Thu hồi toàn bộ token của người dùng này để bảo vệ tài khoản
            appRefreshTokenRepository.revokeAllByUserId(tokenEntity.getUserId());
            auditLogService.recordAuthEvent(null, tokenEntity.getUserId(), "REFRESH_TOKEN_BREACH_SUSPECTED", ipAddress, userAgent,
                    Map.of("warning", "Phát hiện sử dụng lại token đã thu hồi"));
            throw new UnauthorizedException("Refresh token đã bị thu hồi trước đó. Yêu cầu đăng nhập lại.");
        }

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            tokenEntity.setRevoked(true);
            appRefreshTokenRepository.save(tokenEntity);
            throw new UnauthorizedException("Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        // 1. Thu hồi token hiện tại (Single-use rotation)
        tokenEntity.setRevoked(true);
        appRefreshTokenRepository.save(tokenEntity);

        // 2. Tải thông tin người dùng và quyền hạn
        AppUserEntity user = appUserRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Người dùng không còn tồn tại"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UnauthorizedException("Tài khoản người dùng đã bị khóa");
        }

        AppRoleEntity role = appRoleRepository.findById(user.getRoleId()).orElse(null);
        List<AppPermissionEntity> permissions = appPermissionRepository.findByRoleId(user.getRoleId());
        UserPrincipal principal = UserPrincipal.create(user, role, permissions);

        // 3. Cấp phát cặp token mới
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken();
        String newTokenHash = jwtTokenProvider.hashToken(newRefreshToken);
        OffsetDateTime newExpiresAt = jwtTokenProvider.calculateRefreshTokenExpiry();

        AppRefreshTokenEntity newTokenEntity = new AppRefreshTokenEntity(
                user.getId(), newTokenHash, newExpiresAt, ipAddress, userAgent
        );
        appRefreshTokenRepository.save(newTokenEntity);

        // 4. Ghi nhật ký kiểm toán làm mới token
        auditLogService.recordAuthEvent(user.getUsername(), user.getId(), "TOKEN_REFRESH", ipAddress, userAgent,
                Map.of("status", "success"));

        return new AuthResponseDto(
                newAccessToken,
                jwtTokenProvider.getAccessTokenExpirationSeconds(),
                newRefreshToken,
                toUserSummary(principal)
        );
    }

    /**
     * Đăng xuất và thu hồi Refresh Token.
     */
    @Transactional
    public void logout(String refreshToken, UserPrincipal principal, String ipAddress, String userAgent) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = jwtTokenProvider.hashToken(refreshToken);
            appRefreshTokenRepository.revokeByTokenHash(tokenHash);
        }

        String username = principal != null ? principal.getUsername() : "anonymous";
        Long userId = principal != null ? principal.getId() : null;

        auditLogService.recordAuthEvent(username, userId, "LOGOUT", ipAddress, userAgent,
                Map.of("status", "success"));
    }

    /**
     * Lấy thông tin tài khoản hiện tại kèm ma trận quyền.
     */
    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser(UserPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Chưa đăng nhập");
        }
        return toUserSummary(principal);
    }

    /**
     * Quản trị: Thêm mới tài khoản cán bộ kèm băm mật khẩu BCrypt và ghi nhật ký kiểm toán.
     */
    @Transactional
    public UserSummaryDto createUser(CreateUserRequestDto request, String currentAdminUsername,
                                     String ipAddress, String userAgent) {
        if (appUserRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Tên đăng nhập '" + request.getUsername() + "' đã tồn tại.");
        }
        if (appUserRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' đã được sử dụng.");
        }

        String roleCode = request.getRoleCode().trim().toUpperCase();
        if (!roleCode.startsWith("ROLE_")) {
            roleCode = "ROLE_" + roleCode;
        }

        AppRoleEntity role = appRoleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new BadRequestException("Mã vai trò '" + request.getRoleCode() + "' không tồn tại trong hệ thống."));

        AppUserEntity newUser = new AppUserEntity(
                request.getUsername().trim(),
                request.getEmail().trim(),
                passwordEncoder.encode(request.getPassword()),
                request.getFullName().trim(),
                role.getId(),
                request.getBranchId()
        );
        newUser.setOrganizationId(request.getOrganizationId() != null ? request.getOrganizationId() : "moc_dbvn");
        newUser.setIsActive(true);

        AppUserEntity savedUser = appUserRepository.save(newUser);
        List<AppPermissionEntity> perms = appPermissionRepository.findByRoleId(role.getId());
        UserPrincipal principal = UserPrincipal.create(savedUser, role, perms);

        // Ghi nhật ký tạo người dùng (không chứa mật khẩu!)
        auditLogService.recordEntityAction(
                currentAdminUsername,
                null,
                "CREATE",
                "USER",
                savedUser.getUsername(),
                null,
                Map.of("username", savedUser.getUsername(), "email", savedUser.getEmail(), "role", role.getRoleCode()),
                ipAddress,
                userAgent
        );

        return toUserSummary(principal);
    }

    /**
     * Quản trị: Danh sách người dùng hệ thống có phân trang.
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryDto> getUsers(int page, int size, String query) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id").ascending());
        String normalizedQuery = query == null ? "" : query.trim();
        Page<AppUserEntity> userPage = normalizedQuery.isEmpty()
                ? appUserRepository.findAll(pageRequest)
                : appUserRepository.search(normalizedQuery, pageRequest);

        List<UserSummaryDto> dtos = userPage.getContent().stream().map(u -> {
            AppRoleEntity r = appRoleRepository.findById(u.getRoleId()).orElse(null);
            List<AppPermissionEntity> perms = appPermissionRepository.findByRoleId(u.getRoleId());
            return toUserSummary(UserPrincipal.create(u, r, perms));
        }).toList();

        return new PagedResponse<>(
                dtos,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }

    private UserSummaryDto toUserSummary(UserPrincipal principal) {
        return new UserSummaryDto(
                principal.getId(),
                principal.getUsername(),
                principal.getEmail(),
                principal.getFullName(),
                principal.getRoleCode(),
                principal.getRoleName(),
                principal.getOrganizationId(),
                principal.getBranchId(),
                principal.isEnabled(),
                principal.getPermissions()
        );
    }
}
