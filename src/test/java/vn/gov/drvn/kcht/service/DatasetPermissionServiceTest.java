package vn.gov.drvn.kcht.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.gov.drvn.kcht.exception.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit test: DatasetPermissionService - Phân quyền và bảo vệ môi trường sản xuất")
class DatasetPermissionServiceTest {

    private DatasetPermissionService createService(String profile, boolean allowAnonymousAdmin) {
        DatasetPermissionService service = new DatasetPermissionService();
        ReflectionTestUtils.setField(service, "activeProfile", profile);
        ReflectionTestUtils.setField(service, "allowAnonymousAdmin", allowAnonymousAdmin);
        return service;
    }

    @Test
    @DisplayName("Dev mode: Cho phép anonymous truy cập dataset thông thường và quản trị khi bật cờ dev")
    void testDevAnonymousAdmin() {
        DatasetPermissionService service = createService("dev", true);
        assertDoesNotThrow(() -> service.checkDatasetAccess("tbl_bridge", null));
        assertDoesNotThrow(() -> service.checkDatasetAccess("audit_log", null));
    }

    @Test
    @DisplayName("Production mode: Anonymous tự động chuyển thành ROLE_VIEWER và bị chặn truy cập dataset nhạy cảm")
    void testProdAnonymousBlockedForSensitiveDatasets() {
        DatasetPermissionService service = createService("prod", false);

        // Dataset thông thường vẫn được phép xem
        assertDoesNotThrow(() -> service.checkDatasetAccess("tbl_bridge", null));

        // Dataset nhạy cảm (audit_log, app_user) bị từ chối với 403 AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("audit_log", null));
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("app_user", null));
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("maintenance_detail_baidoxe", null));
    }

    @Test
    @DisplayName("ROLE_ADMIN luôn có quyền truy cập tất cả dataset trên mọi môi trường")
    void testAdminFullAccess() {
        DatasetPermissionService service = createService("production", false);
        assertDoesNotThrow(() -> service.checkDatasetAccess("audit_log", "ROLE_ADMIN"));
        assertDoesNotThrow(() -> service.checkDatasetAccess("app_user", "ROLE_ADMIN"));
        assertDoesNotThrow(() -> service.checkDatasetAccess("tbl_bridge", "admin")); // normalize 'admin' -> 'ROLE_ADMIN'
    }

    @Test
    @DisplayName("ROLE_VIEWER bị chặn truy cập bảo trì chi tiết và dataset quản trị")
    void testViewerRestrictedAccess() {
        DatasetPermissionService service = createService("dev", true);

        // Hợp lệ cho tài sản công khai
        assertDoesNotThrow(() -> service.checkDatasetAccess("tbl_bridge", "ROLE_VIEWER"));
        assertDoesNotThrow(() -> service.checkDatasetAccess("mst_national_road", "ROLE_VIEWER"));

        // Chặn bảo trì chi tiết
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("maintenance_detail_baidoxe", "ROLE_VIEWER"));

        // Chặn dataset quản trị
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("import_job", "ROLE_VIEWER"));
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("audit_log", "ROLE_VIEWER"));
    }

    @Test
    @DisplayName("ROLE_OPERATOR có quyền truy cập bảo trì chi tiết nhưng bị chặn dataset quản trị tài khoản / kiểm toán")
    void testOperatorAccess() {
        DatasetPermissionService service = createService("dev", true);

        assertDoesNotThrow(() -> service.checkDatasetAccess("tbl_bridge", "ROLE_OPERATOR"));
        assertDoesNotThrow(() -> service.checkDatasetAccess("maintenance_detail_baidoxe", "ROLE_OPERATOR"));

        // Bị chặn tài khoản / kiểm toán
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("audit_log", "ROLE_OPERATOR"));
        assertThrows(AccessDeniedException.class, () -> service.checkDatasetAccess("app_user", "ROLE_OPERATOR"));
    }
}
