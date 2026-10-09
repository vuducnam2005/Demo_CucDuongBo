package vn.gov.drvn.kcht.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/vroad/route-assignments")
public class RouteAssignmentController {
    private static final String SELECT_ASSIGNMENT = """
            SELECT assignment.id, assignment.user_id, actor.username, actor.full_name, actor.branch_id,
                   assignment.route_name, assignment.chainage_from_m, assignment.chainage_to_m,
                   assignment.purpose, assignment.assigned_at
            FROM vroad_route_assignment assignment JOIN app_user actor ON actor.id = assignment.user_id
            """;
    private final JdbcTemplate database;

    public RouteAssignmentController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping
    public AssignmentPage list(@AuthenticationPrincipal UserPrincipal principal,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        requireUser(principal);
        if (page < 0 || page > 1000000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        }
        String where = " WHERE (? = 'ROLE_ADMIN' OR assignment.user_id = ?)";
        Long total = database.queryForObject("SELECT count(*) FROM vroad_route_assignment assignment" + where,
                Long.class, principal.getRoleCode(), principal.getId());
        List<Assignment> content = database.query(SELECT_ASSIGNMENT + where + " ORDER BY assignment.id DESC LIMIT ? OFFSET ?",
                this::assignment, principal.getRoleCode(), principal.getId(), size, page * size);
        return new AssignmentPage(content, total == null ? 0 : total, page, size);
    }

    @GetMapping("/routes")
    public List<RouteOption> routes(@AuthenticationPrincipal UserPrincipal principal,
                                    @RequestParam(defaultValue = "") String q) {
        requireUser(principal);
        if (q.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Từ khóa quá dài");
        return database.query("""
                SELECT COALESCE(item.raw_payload->>'route_name', item.raw_payload->>'road_name') AS route_name,
                       COUNT(*) AS records
                FROM raw_dataset_record item
                WHERE item.dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri')
                  AND vroad_record_visible(item.id, ?)
                  AND COALESCE(item.raw_payload->>'route_name', item.raw_payload->>'road_name') ILIKE ?
                GROUP BY 1 ORDER BY 1 LIMIT 100
                """, (result, row) -> new RouteOption(result.getString("route_name"), result.getLong("records")),
                principal.getId(), "%" + q.trim() + "%");
    }

    @PostMapping
    @Transactional
    public Assignment assign(@AuthenticationPrincipal UserPrincipal principal, @RequestBody AssignmentRequest request) {
        requireAdmin(principal);
        if (request == null || request.userId() == null || request.userId() < 1 || request.routeName() == null
                || request.routeName().isBlank() || request.routeName().length() > 500
                || invalidBound(request.chainageFromM()) || invalidBound(request.chainageToM())
                || (request.chainageFromM() != null && request.chainageToM() != null
                    && request.chainageFromM().compareTo(request.chainageToM()) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tuyến và khoảng lý trình (m) không hợp lệ");
        }
        String route = request.routeName().trim();
        Long target = database.queryForObject("""
                SELECT count(*) FROM app_user actor JOIN app_role role ON role.id = actor.role_id
                WHERE actor.id = ? AND actor.is_active AND NULLIF(trim(actor.branch_id), '') IS NOT NULL
                  AND role.role_code IN ('ROLE_EDITOR', 'ROLE_MANAGER', 'ROLE_VIEWER')
                """, Long.class, request.userId());
        if (target == null || target == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài khoản đang hoạt động phải được gán đơn vị trước");
        }
        Long observed = database.queryForObject("""
                SELECT count(*) FROM raw_dataset_record
                WHERE dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri')
                  AND COALESCE(raw_payload->>'route_name', raw_payload->>'road_name') = ?
                """, Long.class, route);
        if (observed == null || observed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tuyến chưa có trong dữ liệu nguồn được nhập");
        }
        Long id = database.queryForObject("""
                INSERT INTO vroad_route_assignment(user_id, route_name, chainage_from_m, chainage_to_m, assigned_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, route_name) DO UPDATE SET chainage_from_m = EXCLUDED.chainage_from_m,
                    chainage_to_m = EXCLUDED.chainage_to_m, assigned_by = EXCLUDED.assigned_by, assigned_at = CURRENT_TIMESTAMP
                RETURNING id
                """, Long.class, request.userId(), route, request.chainageFromM(), request.chainageToM(), principal.getId());
        audit(principal, id, "VROAD_ROUTE_ASSIGN");
        return database.queryForObject(SELECT_ASSIGNMENT + " WHERE assignment.id = ?", this::assignment, id);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void revoke(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        requireAdmin(principal);
        if (id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã phân tuyến không hợp lệ");
        if (database.update("DELETE FROM vroad_route_assignment WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phân tuyến");
        }
        audit(principal, id, "VROAD_ROUTE_REVOKE");
    }

    private boolean invalidBound(BigDecimal bound) {
        return bound != null && (bound.signum() < 0 || bound.compareTo(new BigDecimal("1000000000000")) >= 0 || bound.scale() > 3);
    }

    private Assignment assignment(ResultSet result, int row) throws SQLException {
        return new Assignment(result.getLong("id"), result.getLong("user_id"), result.getString("username"),
                result.getString("full_name"), result.getString("branch_id"), result.getString("route_name"),
                result.getBigDecimal("chainage_from_m"), result.getBigDecimal("chainage_to_m"),
                result.getString("purpose"), result.getObject("assigned_at", OffsetDateTime.class));
    }

    private void requireUser(UserPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
        }
    }

    private void requireAdmin(UserPrincipal principal) {
        requireUser(principal);
        if (!"ROLE_ADMIN".equals(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị được phân tuyến");
        }
    }

    private void audit(UserPrincipal principal, Long id, String action) {
        database.update("""
                INSERT INTO audit_log(user_id, username, action, entity_type, entity_id)
                VALUES (?, ?, ?, 'VROAD_ROUTE_ASSIGNMENT', ?)
                """, principal.getId(), principal.getUsername(), action, id.toString());
    }

    public record AssignmentRequest(Long userId, String routeName, BigDecimal chainageFromM, BigDecimal chainageToM) {}
    public record Assignment(long id, long userId, String username, String fullName, String branchId, String routeName,
                             BigDecimal chainageFromM, BigDecimal chainageToM, String purpose, OffsetDateTime assignedAt) {}
    public record AssignmentPage(List<Assignment> content, long totalElements, int page, int size) {}
    public record RouteOption(String routeName, long records) {}
}
