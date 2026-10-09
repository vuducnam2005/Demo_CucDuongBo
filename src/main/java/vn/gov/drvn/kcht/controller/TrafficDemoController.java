package vn.gov.drvn.kcht.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/vroad/traffic")
public class TrafficDemoController {
    private final JdbcTemplate database;

    public TrafficDemoController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping("/stations")
    public List<Station> stations(@AuthenticationPrincipal UserPrincipal principal) {
        requireReader(principal);
        return database.query("""
                SELECT station_code, station_name, route_name, is_demo, source_system, segment_length_km
                FROM traffic_station
                WHERE (? = 'ROLE_ADMIN' OR branch_id = ?)
                ORDER BY station_code
                """, (result, row) -> new Station(result.getString("station_code"),
                result.getString("station_name"), result.getString("route_name"),
                result.getBoolean("is_demo"), result.getString("source_system"),
                result.getBigDecimal("segment_length_km")), principal.getRoleCode(), principal.getBranchId());
    }

    @GetMapping("/summary")
    public TrafficSummary summary(@AuthenticationPrincipal UserPrincipal principal,
                                  @RequestParam String stationCode,
                                  @RequestParam(required = false) OffsetDateTime from,
                                  @RequestParam(required = false) OffsetDateTime to) {
        requireReader(principal);
        if (stationCode == null || stationCode.length() > 80 || stationCode.isBlank()
                || (from == null) != (to == null)
                || (from != null && (!from.isBefore(to) || from.plusDays(31).isBefore(to)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã trạm hoặc khoảng thời gian không hợp lệ");
        }
        Station station = database.query("""
                SELECT station_code, station_name, route_name, is_demo, source_system, segment_length_km
                FROM traffic_station
                WHERE station_code = ? AND (? = 'ROLE_ADMIN' OR branch_id = ?)
                """, (result, row) -> new Station(result.getString("station_code"),
                result.getString("station_name"), result.getString("route_name"),
                result.getBoolean("is_demo"), result.getString("source_system"),
                result.getBigDecimal("segment_length_km")), stationCode, principal.getRoleCode(), principal.getBranchId())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (from == null) {
            to = database.queryForObject("""
                    SELECT GREATEST(
                        (SELECT MAX(window_end) FROM traffic_interval
                            WHERE station_id = (SELECT id FROM traffic_station WHERE station_code = ?)),
                        (SELECT MAX(measured_at) FROM traffic_density_snapshot
                            WHERE station_id = (SELECT id FROM traffic_station WHERE station_code = ?)))
                    """, OffsetDateTime.class, stationCode, stationCode);
            if (to == null) return new TrafficSummary(station, 0, List.of(), List.of(), null, null, null);
            from = to.minusDays(31);
        }
        OffsetDateTime rangeStart = from;
        OffsetDateTime rangeEnd = to;
        List<ClassCount> classes = database.query("""
                SELECT i.vehicle_class, SUM(i.vehicle_count) AS total
                FROM traffic_interval i JOIN traffic_station s ON s.id = i.station_id
                WHERE s.station_code = ? AND i.window_start >= ? AND i.window_end <= ?
                GROUP BY i.vehicle_class ORDER BY total DESC, i.vehicle_class
                """, (result, row) -> new ClassCount(result.getString("vehicle_class"), result.getLong("total")),
                stationCode, rangeStart, rangeEnd);
        List<IntervalCount> intervals = database.query("""
                SELECT date_trunc('hour', i.window_start) AS window_start,
                       date_trunc('hour', i.window_start) + INTERVAL '1 hour' AS window_end,
                       SUM(i.vehicle_count) AS total
                FROM traffic_interval i JOIN traffic_station s ON s.id = i.station_id
                WHERE s.station_code = ? AND i.window_start >= ? AND i.window_end <= ?
                GROUP BY date_trunc('hour', i.window_start)
                ORDER BY window_start
                LIMIT 1000
                """, (result, row) -> new IntervalCount(
                result.getObject("window_start", OffsetDateTime.class),
                result.getObject("window_end", OffsetDateTime.class), result.getLong("total")),
                stationCode, rangeStart, rangeEnd);
        long total = classes.stream().mapToLong(ClassCount::count).sum();
        DensitySnapshot density = station.segmentLengthKm() == null ? null
                : density(station, stationCode, rangeStart, rangeEnd);
        return new TrafficSummary(station, total, classes, intervals, rangeStart, rangeEnd, density);
    }

    private DensitySnapshot density(Station station, String stationCode,
                                    OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime measuredAt = database.queryForObject("""
                SELECT MAX(d.measured_at) FROM traffic_density_snapshot d
                JOIN traffic_station s ON s.id = d.station_id
                WHERE s.station_code = ? AND d.measured_at >= ? AND d.measured_at <= ?
                """, OffsetDateTime.class, stationCode, from, to);
        if (measuredAt == null) return null;
        List<ClassCount> classes = database.query("""
                SELECT d.vehicle_class, SUM(d.present_vehicles) AS total
                FROM traffic_density_snapshot d JOIN traffic_station s ON s.id = d.station_id
                WHERE s.station_code = ? AND d.measured_at = ?
                GROUP BY d.vehicle_class ORDER BY total DESC, d.vehicle_class
                """, (result, row) -> new ClassCount(result.getString("vehicle_class"),
                result.getLong("total")), stationCode, measuredAt);
        long present = classes.stream().mapToLong(ClassCount::count).sum();
        return new DensitySnapshot(measuredAt, present,
                BigDecimal.valueOf(present).divide(station.segmentLengthKm(), 2, RoundingMode.HALF_UP), classes);
    }

    private void requireReader(UserPrincipal principal) {
        if (principal == null || !("ROLE_ADMIN".equals(principal.getRoleCode()) ||
                (principal.getBranchId() != null && ("ROLE_MANAGER".equals(principal.getRoleCode()) ||
                        "ROLE_EDITOR".equals(principal.getRoleCode()))))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    public record Station(String stationCode, String stationName, String routeName, boolean isDemo,
                          String sourceSystem, BigDecimal segmentLengthKm) {}
    public record ClassCount(String vehicleClass, long count) {}
    public record IntervalCount(OffsetDateTime start, OffsetDateTime end, long count) {}
    public record DensitySnapshot(OffsetDateTime measuredAt, long presentVehicles,
                                  BigDecimal vehiclesPerKm, List<ClassCount> classes) {}
    public record TrafficSummary(Station station, long totalVehicles, List<ClassCount> classes,
                                 List<IntervalCount> intervals, OffsetDateTime rangeStart,
                                 OffsetDateTime rangeEnd, DensitySnapshot density) {}
}
