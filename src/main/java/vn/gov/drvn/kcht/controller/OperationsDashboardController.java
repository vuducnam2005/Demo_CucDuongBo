package vn.gov.drvn.kcht.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/operations")
public class OperationsDashboardController {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate database;

    public OperationsDashboardController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Summary summary() {
        List<CountPoint> datasets = database.query("""
                SELECT d.dataset_key AS code, d.dataset_name AS label, COUNT(r.id) AS total
                FROM dataset_registry d LEFT JOIN raw_dataset_record r ON r.dataset_key = d.dataset_key
                GROUP BY d.dataset_key, d.dataset_name ORDER BY total DESC, d.dataset_key
                """, (result, index) -> new CountPoint(result.getString("code"),
                result.getString("label"), result.getLong("total")));
        List<CountPoint> roles = database.query("""
                SELECT role.role_code AS code, role.role_name AS label,
                       COUNT(account.id) FILTER (WHERE account.is_active) AS total
                FROM app_role role LEFT JOIN app_user account ON account.role_id = role.id
                GROUP BY role.role_code, role.role_name ORDER BY role.role_code
                """, (result, index) -> new CountPoint(result.getString("code"),
                result.getString("label"), result.getLong("total")));
        List<CountPoint> branches = database.query("""
                SELECT COALESCE(branch_id, 'unassigned') AS code,
                       COUNT(*) AS total FROM document_metadata WHERE NOT is_deleted
                GROUP BY branch_id ORDER BY total DESC, code
                """, (result, index) -> new CountPoint(result.getString("code"),
                branchLabel(result.getString("code")), result.getLong("total")));
        LocalDate firstDay = LocalDate.now(VIETNAM).minusDays(6);
        OffsetDateTime from = firstDay.atStartOfDay(VIETNAM).toOffsetDateTime();
        Map<LocalDate, Long> loggedByDay = new HashMap<>();
        database.query("""
                SELECT (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,
                       COUNT(*) AS total FROM audit_log WHERE created_at >= ?
                GROUP BY day ORDER BY day
                """, (result, index) -> {
                    loggedByDay.put(result.getObject("day", LocalDate.class), result.getLong("total"));
                    return result.getLong("total");
                }, from);
        List<DayCount> activity = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate day = firstDay.plusDays(dayOffset);
            activity.add(new DayCount(day, loggedByDay.getOrDefault(day, 0L)));
        }
        long[] users = database.queryForObject("""
                SELECT COUNT(*), COUNT(*) FILTER (WHERE is_active) FROM app_user
                """, (result, index) -> new long[]{result.getLong(1), result.getLong(2)});
        long[] staging = database.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE status = 'PENDING'),
                       COUNT(*) FILTER (WHERE status = 'INVALID')
                FROM vroad_ingest_record
                """, (result, index) -> new long[]{result.getLong(1), result.getLong(2)});
        Long batches = database.queryForObject("SELECT COUNT(*) FROM vroad_ingest_batch", Long.class);
        Long resolved = database.queryForObject("SELECT COUNT(*) FROM vroad_defect_case", Long.class);
        Long observations = database.queryForObject("""
                SELECT COUNT(*) FROM traffic_interval interval_data
                JOIN traffic_station station ON station.id = interval_data.station_id
                WHERE station.is_demo
                """, Long.class);
        Long importedObservations = database.queryForObject("""
                SELECT COUNT(*) FROM traffic_interval interval_data
                JOIN traffic_station station ON station.id = interval_data.station_id
                WHERE NOT station.is_demo
                """, Long.class);
        Long densitySnapshots = database.queryForObject(
                "SELECT COUNT(*) FROM traffic_density_snapshot", Long.class);
        List<CountPoint> vehicles = database.query("""
                SELECT (CASE WHEN station.is_demo THEN 'DEMO:' ELSE 'IMPORTED:' END)
                    || interval_data.vehicle_class AS code,
                    interval_data.vehicle_class ||
                    (CASE WHEN station.is_demo THEN ' · mô phỏng' ELSE ' · nhập' END) AS label,
                    SUM(interval_data.vehicle_count) AS total
                FROM traffic_interval interval_data
                JOIN traffic_station station ON station.id = interval_data.station_id
                GROUP BY station.is_demo, interval_data.vehicle_class
                ORDER BY total DESC, code
                """, (result, index) -> new CountPoint(result.getString("code"),
                result.getString("label"), result.getLong("total")));
        OffsetDateTime lastImport = database.queryForObject(
                "SELECT MAX(imported_at) FROM raw_dataset_record",
                (result, index) -> result.getObject(1, OffsetDateTime.class));
        return new Summary(users[0], users[1], datasets.size(),
                datasets.stream().mapToLong(CountPoint::count).sum(),
                branches.stream().mapToLong(CountPoint::count).sum(),
                batches, staging[0], staging[1], resolved, observations,
                importedObservations, densitySnapshots, lastImport, OffsetDateTime.now(VIETNAM),
                datasets, roles, branches, activity, vehicles);
    }

    private String branchLabel(String code) {
        return switch (code) {
            case "moc_dbvn" -> "Cục Đường bộ Việt Nam";
            case "kqldb_1" -> "Khu Quản lý Đường bộ I";
            case "unassigned" -> "Chưa gán đơn vị";
            default -> code;
        };
    }

    public record CountPoint(String code, String label, long count) {}
    public record DayCount(LocalDate day, long count) {}
    public record Summary(long totalUsers, long activeUsers, long datasetCount, long rawRecords,
                          long storedDocuments, long stagedBatches, long pendingRecords,
                          long invalidRecords, long resolvedCases, long simulatedTrafficObservations,
                          long importedTrafficObservations, long trafficDensitySnapshots,
                          OffsetDateTime lastRawImportAt, OffsetDateTime calculatedAt,
                          List<CountPoint> datasets, List<CountPoint> activeRoles,
                          List<CountPoint> documentBranches, List<DayCount> auditActivity,
                          List<CountPoint> trafficVehiclesByClass) {}
}
