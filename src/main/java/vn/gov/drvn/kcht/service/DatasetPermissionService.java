package vn.gov.drvn.kcht.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.exception.AccessDeniedException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.Set;

@Service
public class DatasetPermissionService {

    private static final Logger log = LoggerFactory.getLogger(DatasetPermissionService.class);

    // Danh sách 4 vai trò chuẩn trong hệ thống
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_MANAGER = "ROLE_MANAGER";
    public static final String ROLE_EDITOR = "ROLE_EDITOR";
    public static final String ROLE_VIEWER = "ROLE_VIEWER";
    public static final String ROLE_OPERATOR = "ROLE_OPERATOR"; // Tương thích với cấu hình cũ (tương đương ROLE_EDITOR)

    // Một số dataset nội bộ cần quyền quản trị đặc biệt
    private static final Set<String> SENSITIVE_DATASETS = Set.of(
            "app_user", "app_role", "app_permission", "audit_log", "import_job", "import_error"
    );

    @Value("${spring.profiles.active:dev}")
    private String activeProfile = "dev";

    @Value("${kcht.security.allow-anonymous-admin:true}")
    private boolean allowAnonymousAdmin = true;

    /**
     * Kiểm tra quyền truy cập dataset dựa theo vai trò của người dùng.
     *
     * @param datasetKey Mã tập dữ liệu cần truy cập
     * @param userRole   Vai trò truyền vào (từ header X-User-Role hoặc Security Context)
     * @throws AccessDeniedException nếu vai trò không đủ quyền
     */
    public void checkDatasetAccess(String datasetKey, String userRole) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current != null && current.isAuthenticated() && current.getPrincipal() instanceof UserPrincipal) {
            userRole = current.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .findFirst().orElse(ROLE_VIEWER);
        }
        if (userRole == null || userRole.isBlank()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                for (GrantedAuthority authority : auth.getAuthorities()) {
                    String authStr = authority.getAuthority();
                    if (authStr.startsWith("ROLE_")) {
                        userRole = authStr;
                        break;
                    }
                }
            }

            if (userRole == null || userRole.isBlank()) {
                boolean isProd = activeProfile != null && (activeProfile.contains("prod") || activeProfile.contains("production"));
                if (isProd || !allowAnonymousAdmin) {
                    userRole = ROLE_VIEWER; // Môi trường production không mở quyền admin ẩn danh
                } else {
                    userRole = ROLE_ADMIN; // Profile dev/local nội bộ
                }
            }
        }

        String normalizedRole = userRole.toUpperCase().trim();
        if (!normalizedRole.startsWith("ROLE_")) {
            normalizedRole = "ROLE_" + normalizedRole;
        }

        log.debug("Kiểm tra phân quyền: Role={}, Dataset={}", normalizedRole, datasetKey);

        if (ROLE_ADMIN.equals(normalizedRole)) {
            return; // Toàn quyền truy cập mọi dataset
        }

        if (SENSITIVE_DATASETS.contains(datasetKey.toLowerCase())) {
            log.warn("Từ chối truy cập dataset nhạy cảm {} cho vai trò {}", datasetKey, normalizedRole);
            throw new AccessDeniedException(
                    String.format("Vai trò '%s' không có quyền truy cập tập dữ liệu quản trị '%s'.", normalizedRole, datasetKey)
            );
        }

        // ROLE_VIEWER chỉ được xem các dataset dạng tài sản công khai và danh mục
        if (ROLE_VIEWER.equals(normalizedRole)) {
            if (datasetKey.startsWith("maintenance_detail_") || datasetKey.startsWith("import_")) {
                throw new AccessDeniedException(
                        String.format("Vai trò người xem '%s' không được phép truy cập phân hệ bảo trì chi tiết '%s'.", normalizedRole, datasetKey)
                );
            }
        }
    }

    /**
     * Kiểm tra quyền hạn chi tiết gắn theo module, dataset và hành động (action).
     *
     * @param module  Tên module nghiệp vụ (DASHBOARD, ASSET, GIS, REPORT, DOCUMENT, CATALOG, IMPORT, USER, AUDIT)
     * @param dataset Mã dataset cụ thể (hoặc null nếu thao tác chung toàn module)
     * @param action  Hành động yêu cầu: read, create, update, delete, export, manage_users
     * @throws AccessDeniedException nếu không có quyền
     */
    public void checkPermission(String module, String dataset, String action) {
        checkPermission(module, dataset, action, null);
    }

    /**
     * Kiểm tra quyền hạn chi tiết gắn theo module, dataset, hành động và vai trò truyền vào.
     */
    public void checkPermission(String module, String dataset, String action, String userRole) {
        String normalizedRole = null;
        if (userRole != null && !userRole.isBlank()) {
            normalizedRole = userRole.toUpperCase().trim();
            if (!normalizedRole.startsWith("ROLE_")) {
                normalizedRole = "ROLE_" + normalizedRole;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String authStr = authority.getAuthority();
                if (authStr.startsWith("ROLE_")) {
                    normalizedRole = authStr;
                    break;
                }
            }
        }

        // ROLE_ADMIN có toàn quyền trên toàn hệ thống
        if (ROLE_ADMIN.equals(normalizedRole) || (auth != null && auth.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority())))) {
            if (dataset != null && !dataset.isBlank()) {
                checkDatasetAccess(dataset, normalizedRole);
            }
            return;
        }

        String normAction = action != null ? action.toUpperCase().trim() : "READ";
        String normModule = module != null ? module.toUpperCase().trim() : "ASSET";

        // Kiểm tra theo role nếu có
        if (normalizedRole != null) {
            if (ROLE_VIEWER.equals(normalizedRole)) {
                if ("CREATE".equals(normAction) || "UPDATE".equals(normAction) || "DELETE".equals(normAction) || "IMPORT".equals(normAction)) {
                    throw new AccessDeniedException(
                            String.format("Vai trò người xem '%s' chỉ có quyền tra cứu, không được phép thực hiện '%s' trên phân hệ '%s'.",
                                    normalizedRole, normAction, normModule)
                    );
                }
            } else if (ROLE_EDITOR.equals(normalizedRole) || ROLE_OPERATOR.equals(normalizedRole)) {
                if ("DELETE".equals(normAction)) {
                    throw new AccessDeniedException(
                            String.format("Vai trò '%s' không có quyền xóa bản ghi trên phân hệ '%s'. Yêu cầu cấp Quản lý (ROLE_MANAGER) hoặc Quản trị viên.",
                                    normalizedRole, normModule)
                    );
                }
                if ("IMPORT".equals(normAction)) {
                    throw new AccessDeniedException(
                            String.format("Vai trò '%s' không có quyền nạp dữ liệu hàng loạt trên phân hệ '%s'. Yêu cầu vai trò Quản trị viên (ROLE_ADMIN).",
                                    normalizedRole, normModule)
                    );
                }
            } else if (ROLE_MANAGER.equals(normalizedRole)) {
                if ("IMPORT".equals(normAction)) {
                    // Manager allowed read/preview import, but execute import requires ADMIN
                    log.info("ROLE_MANAGER thực hiện thao tác IMPORT trên {}", normModule);
                }
            }
        }

        // Kiểm tra chi tiết qua authorities trong SecurityContext nếu có
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String requiredPermission = (normModule + ":" + normAction).trim();
            boolean hasRequiredAuth = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(a -> a.equalsIgnoreCase(requiredPermission));

            if (!hasRequiredAuth && (normalizedRole == null || ROLE_VIEWER.equals(normalizedRole))) {
                throw new AccessDeniedException(
                        String.format("Từ chối truy cập: Tài khoản không có quyền '%s' trên phân hệ '%s'.",
                                requiredPermission, normModule)
                );
            }
        }

        // Nếu có dataset cụ thể, tiếp tục kiểm tra quyền hạn dataset
        if (dataset != null && !dataset.isBlank()) {
            checkDatasetAccess(dataset, normalizedRole);
        }
    }
}
