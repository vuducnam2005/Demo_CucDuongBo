package vn.gov.drvn.kcht.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/region/assets/{id}")
public class RegionReviewController {
    private final JdbcTemplate jdbcTemplate;

    public RegionReviewController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/review")
    public ReviewItem review(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        String branch = requireBranch(principal);
        requireVisibleRecord(id, principal);
        List<ReviewItem> items = jdbcTemplate.query("""
                SELECT id, status, version, reason, submitted_at, reviewed_at
                FROM region_demo_review WHERE record_id = ? AND branch_id = ?
                """, (result, row) -> new ReviewItem(result.getLong("id"), result.getString("status"),
                result.getInt("version"), result.getString("reason"),
                result.getObject("submitted_at", OffsetDateTime.class),
                result.getObject("reviewed_at", OffsetDateTime.class)), id, branch);
        return items.isEmpty() ? new ReviewItem(null, "CHƯA_GỬI", 0, null, null, null) : items.get(0);
    }

    @PostMapping("/submit")
    @Transactional
    public ReviewItem submit(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        String branch = requireBranch(principal);
        if (!"ROLE_EDITOR".equals(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ cán bộ nhập liệu được gửi duyệt nội bộ");
        }
        requireVisibleRecord(id, principal);
        List<Long> updated = jdbcTemplate.query("""
                INSERT INTO region_demo_review(record_id, branch_id, status, submitted_by)
                VALUES (?, ?, 'IN_REVIEW', ?)
                ON CONFLICT (record_id) DO UPDATE SET
                    status = 'IN_REVIEW', version = region_demo_review.version + 1,
                    submitted_by = EXCLUDED.submitted_by, submitted_at = CURRENT_TIMESTAMP,
                    reviewed_by = NULL, reviewed_at = NULL, reason = NULL
                WHERE region_demo_review.branch_id = EXCLUDED.branch_id AND region_demo_review.status = 'RETURNED'
                RETURNING id
                """, (result, row) -> result.getLong(1), id, branch, principal.getId());
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản ghi đang chờ hoặc đã kiểm tra");
        }
        audit(principal, id, branch, "VROAD_REVIEW_SUBMIT", "IN_REVIEW");
        return review(principal, id);
    }

    @PostMapping("/decision")
    @Transactional
    public ReviewItem decide(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id,
                             @RequestBody DecisionRequest request) {
        String branch = requireBranch(principal);
        if (!"ROLE_MANAGER".equals(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ người quản lý đơn vị được kiểm tra nội bộ");
        }
        requireVisibleRecord(id, principal);
        String decision = request == null || request.decision() == null ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "RETURNED").contains(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ chấp nhận APPROVED hoặc RETURNED");
        }
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.length() > 1000 || ("RETURNED".equals(decision) && reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trả về cần lý do (tối đa 1.000 ký tự)");
        }
        List<Long> updated = jdbcTemplate.query("""
                UPDATE region_demo_review SET status = ?, reason = ?, reviewed_by = ?,
                    reviewed_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE record_id = ? AND branch_id = ? AND status = 'IN_REVIEW' AND submitted_by <> ?
                RETURNING id
                """, (result, row) -> result.getLong(1), decision, reason, principal.getId(), id, branch, principal.getId());
        if (updated.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chưa gửi duyệt hoặc không thể tự duyệt bản ghi");
        }
        audit(principal, id, branch, "VROAD_REVIEW_" + decision, decision);
        return review(principal, id);
    }

    private String requireBranch(UserPrincipal principal) {
        if (principal == null || principal.getBranchId() == null || principal.getBranchId().isBlank()
                || !List.of("ROLE_EDITOR", "ROLE_MANAGER").contains(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cần tài khoản đơn vị");
        }
        return principal.getBranchId();
    }

    private void requireVisibleRecord(long id, UserPrincipal principal) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM raw_dataset_record
                WHERE id = ? AND dataset_key IN ('demo_region_assets', 'vroad_assets', 'vroad_defects', 'vroad_iri')
                  AND vroad_record_visible(id, ?)
                """, Long.class, id, principal.getId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi trong phạm vi được cấp");
        }
    }

    private void audit(UserPrincipal principal, long id, String branch, String action, String status) {
        jdbcTemplate.update("""
                INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                VALUES (?, ?, ?, 'VROAD_SOURCE_REVIEW', ?, jsonb_build_object('branch_id', ?, 'status', ?))
                """, principal.getId(), principal.getUsername(), action, Long.toString(id), branch, status);
    }

    public record DecisionRequest(String decision, String reason) {}
    public record ReviewItem(Long id, String status, int version, String reason,
                             OffsetDateTime submittedAt, OffsetDateTime reviewedAt) {}
}
