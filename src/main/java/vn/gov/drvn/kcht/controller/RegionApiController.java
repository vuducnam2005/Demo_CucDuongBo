package vn.gov.drvn.kcht.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/region")
public class RegionApiController {
    private static final String VISIBLE = """
            FROM raw_dataset_record r
            JOIN dataset_registry d ON d.dataset_key = r.dataset_key
            WHERE (d.kind = 'asset' OR r.dataset_key IN ('demo_region_assets', 'vroad_defects', 'vroad_iri'))
              AND vroad_record_visible(r.id, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public RegionApiController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summary")
    public RegionSummary summary(@AuthenticationPrincipal UserPrincipal principal) {
        String branchId = requireBranch(principal);
        String sql = "SELECT COUNT(*) AS records, COUNT(DISTINCT r.dataset_key) AS datasets, "
                + "COUNT(*) FILTER (WHERE r.raw_payload->>'is_demo' = 'true') AS demo_records " + VISIBLE;
        return jdbcTemplate.queryForObject(sql, (result, row) -> new RegionSummary(
                branchId, result.getLong("records"), result.getLong("datasets"),
                result.getLong("demo_records"), "QC nội bộ; phân tuyến demo không xác nhận địa bàn quản lý chính thức", OffsetDateTime.now()
        ), principal.getId());
    }

    @GetMapping("/assets")
    public RegionPage assets(@AuthenticationPrincipal UserPrincipal principal,
                             @RequestParam(required = false) String dataset,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        String branchId = requireBranch(principal);
        if (page < 0 || size < 1 || size > 100 || page > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        }
        StringBuilder where = new StringBuilder(VISIBLE);
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(principal.getId());
        if (dataset != null && !dataset.isBlank()) {
            where.append(" AND r.dataset_key = ?");
            parameters.add(dataset.trim());
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (r.record_key ILIKE ? OR r.raw_payload->>'fielddisplay' ILIKE ? OR r.raw_payload->>'route_name' ILIKE ?)");
            String keyword = "%" + q.trim() + "%";
            parameters.add(keyword);
            parameters.add(keyword);
            parameters.add(keyword);
        }
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) " + where, Long.class, parameters.toArray());
        parameters.add(size);
        parameters.add(page * size);
        String sql = "SELECT r.id, r.dataset_key, r.record_key, "
                + "COALESCE(r.raw_payload->>'fielddisplay', r.raw_payload->>'name', r.raw_payload->>'text', r.record_key) AS display_name, "
                + "COALESCE(r.raw_payload->>'route_name', r.raw_payload->>'road_name') AS route_name, r.record_status, "
                + "(r.raw_payload->>'is_demo' = 'true') AS is_demo " + where
                + " ORDER BY r.id DESC LIMIT ? OFFSET ?";
        List<RegionAsset> records = jdbcTemplate.query(sql, this::mapAsset, parameters.toArray());
        return new RegionPage(records, count == null ? 0 : count, page, size);
    }

    @GetMapping("/assets/{id}")
    public RegionAsset asset(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        String branchId = requireBranch(principal);
        String sql = "SELECT r.id, r.dataset_key, r.record_key, "
                + "COALESCE(r.raw_payload->>'fielddisplay', r.raw_payload->>'name', r.raw_payload->>'text', r.record_key) AS display_name, "
                + "COALESCE(r.raw_payload->>'route_name', r.raw_payload->>'road_name') AS route_name, r.record_status, "
                + "(r.raw_payload->>'is_demo' = 'true') AS is_demo " + VISIBLE + " AND r.id = ?";
        return jdbcTemplate.query(sql, this::mapAsset, principal.getId(), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi trong phạm vi đơn vị"));
    }

    private RegionAsset mapAsset(ResultSet result, int row) throws SQLException {
        return new RegionAsset(result.getLong("id"), result.getString("dataset_key"),
                result.getString("record_key"), result.getString("display_name"),
                result.getString("route_name"), result.getString("record_status"), result.getBoolean("is_demo"));
    }

    private String requireBranch(UserPrincipal principal) {
        if (principal == null || principal.getBranchId() == null || principal.getBranchId().isBlank()
                || !List.of("ROLE_EDITOR", "ROLE_MANAGER", "ROLE_VIEWER").contains(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đơn vị được xem dữ liệu thô của đơn vị");
        }
        return principal.getBranchId();
    }

    public record RegionSummary(String branchId, long totalRecords, long totalDatasets, long demoRecords,
                                String sourceStatus, OffsetDateTime calculatedAt) {}
    public record RegionAsset(long id, String datasetKey, String recordKey, String displayName,
                              String routeName, String recordStatus, boolean isDemo) {}
    public record RegionPage(List<RegionAsset> content, long totalElements, int page, int size) {}
}
