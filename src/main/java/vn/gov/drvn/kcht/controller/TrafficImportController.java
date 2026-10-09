package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/vroad/traffic/imports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Traffic import (proposed)", description = "Local imports only; VroadAI contract pending")
public class TrafficImportController {
    private final JdbcTemplate database;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public TrafficImportController(JdbcTemplate database, ObjectMapper mapper, TransactionTemplate transaction) {
        this.database = database;
        this.mapper = mapper;
        this.transaction = transaction;
    }

    public record StationSpec(String code, String name, String routeName, String branchId,
                              BigDecimal segmentLengthKm) {}
    public record CountEvent(String eventKey, String direction, Integer lane, OffsetDateTime windowStart,
                             OffsetDateTime windowEnd, String vehicleClass, Integer vehicleCount) {}
    public record DensityEvent(String eventKey, String direction, OffsetDateTime measuredAt,
                               String vehicleClass, Integer presentVehicles) {}
    public record ImportRequest(String requestKey, String sourceSystem, StationSpec station,
                                List<CountEvent> counts, List<DensityEvent> snapshots) {}
    public record ImportResult(long batchId, String requestKey, String stationCode,
                               int importedCounts, int importedSnapshots) {}
    private record StoredStation(long id, String name, String routeName, String branchId,
                                 String sourceSystem, BigDecimal segmentLengthKm, boolean demo) {}

    @PostMapping
    @Operation(summary = "Import counted traffic and point-in-time road occupancy into the local database")
    public ImportResult ingest(@AuthenticationPrincipal UserPrincipal principal, @RequestBody ImportRequest request) {
        validate(request);
        String payload = mapper.valueToTree(request).toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String hash = digest(payload);
        try {
            return Objects.requireNonNull(transaction.execute(status -> persist(principal, request, hash)));
        } catch (DataIntegrityViolationException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    private ImportResult persist(UserPrincipal principal, ImportRequest request, String hash) {
        StationSpec spec = request.station();
        List<StoredStation> existing = database.query("""
                SELECT id, station_name, route_name, branch_id, source_system, segment_length_km, is_demo
                FROM traffic_station WHERE station_code = ?
                """, (row, index) -> new StoredStation(row.getLong("id"), row.getString("station_name"),
                row.getString("route_name"), row.getString("branch_id"), row.getString("source_system"),
                row.getBigDecimal("segment_length_km"), row.getBoolean("is_demo")), spec.code());
        long stationId;
        if (existing.isEmpty()) {
            stationId = database.queryForObject("""
                    INSERT INTO traffic_station(station_code, station_name, route_name, branch_id, segment_length_km, source_system, is_demo)
                    VALUES (?, ?, ?, ?, ?, ?, FALSE) RETURNING id
                    """, Long.class, spec.code(), spec.name(), spec.routeName(), spec.branchId(), spec.segmentLengthKm(), request.sourceSystem());
        } else {
            StoredStation station = existing.get(0);
            if (station.demo() || !station.name().equals(spec.name())
                    || !station.routeName().equals(spec.routeName())
                    || !station.branchId().equals(spec.branchId())
                    || !station.sourceSystem().equals(request.sourceSystem())
                    || (station.segmentLengthKm() == null) != (spec.segmentLengthKm() == null)
                    || station.segmentLengthKm() != null
                       && station.segmentLengthKm().compareTo(spec.segmentLengthKm()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            stationId = station.id();
        }
        List<Long> batchIds = database.query("""
                INSERT INTO traffic_import_batch(request_key, payload_sha256, source_system, station_id,
                    submitted_by, interval_count, snapshot_count)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (request_key) DO NOTHING RETURNING id
                """, (row, index) -> row.getLong(1), request.requestKey(), hash,
                request.sourceSystem(), stationId, principal.getId(),
                request.counts().size(), request.snapshots().size());
        if (batchIds.isEmpty()) {
            List<Long> matching = database.query("""
                    SELECT id FROM traffic_import_batch WHERE request_key = ? AND payload_sha256 = ?
                    """, (row, index) -> row.getLong(1), request.requestKey(), hash);
            if (matching.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return new ImportResult(matching.get(0), request.requestKey(), spec.code(),
                    request.counts().size(), request.snapshots().size());
        }
        long batchId = batchIds.get(0);
        for (CountEvent event : request.counts()) {
            String eventId = registerEvent(request.sourceSystem(), event.eventKey(), "COUNT", batchId);
            database.update("""
                    INSERT INTO traffic_interval(station_id, direction, lane, window_start, window_end,
                        vehicle_class, vehicle_count, source_event_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, stationId, event.direction(), event.lane(), event.windowStart(), event.windowEnd(),
                    event.vehicleClass(), event.vehicleCount(), eventId);
        }
        for (DensityEvent event : request.snapshots()) {
            String eventId = registerEvent(request.sourceSystem(), event.eventKey(), "DENSITY", batchId);
            database.update("""
                    INSERT INTO traffic_density_snapshot(station_id, direction, measured_at,
                        vehicle_class, present_vehicles, source_event_key)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, stationId, event.direction(), event.measuredAt(), event.vehicleClass(),
                    event.presentVehicles(), eventId);
        }
        database.update("""
                INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                VALUES (?, ?, 'TRAFFIC_IMPORT', 'TRAFFIC_BATCH', ?,
                    jsonb_build_object('sourceSystem', ?, 'stationCode', ?, 'counts', ?, 'snapshots', ?))
                """, principal.getId(), principal.getUsername(), String.valueOf(batchId),
                request.sourceSystem(), spec.code(), request.counts().size(), request.snapshots().size());
        return new ImportResult(batchId, request.requestKey(), spec.code(),
                request.counts().size(), request.snapshots().size());
    }

    void validate(ImportRequest request) {
        if (request == null || !matches(request.requestKey(), "[A-Za-z0-9][A-Za-z0-9._-]{5,119}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        StationSpec station = request.station();
        if (!matches(request.sourceSystem(), "[A-Za-z][A-Za-z0-9_-]{1,79}")
                || station == null || !matches(station.code(), "[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                || (station.code().startsWith("DEMO-") || station.code().startsWith("EXAMPLE-"))
                || !label(station.name(), 180)
                || !label(station.routeName(), 180) || !matches(station.branchId(), "[A-Za-z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        if (request.counts() == null || request.snapshots() == null
                || request.counts().size() + request.snapshots().size() < 1
                || request.counts().size() + request.snapshots().size() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        BigDecimal length = station.segmentLengthKm();
        if (length == null && !request.snapshots().isEmpty()
                || length != null && (length.compareTo(BigDecimal.ZERO) <= 0
                || length.compareTo(new BigDecimal("1000")) > 0 || length.scale() > 3)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        Set<String> eventKeys = new HashSet<>();
        for (CountEvent event : request.counts()) {
            if (event == null || !eventKey(event.eventKey(), eventKeys)
                    || !label(event.direction(), 50) || event.lane() == null
                    || event.lane() < 1 || event.lane() > 12
                    || event.windowStart() == null || event.windowEnd() == null
                    || !event.windowStart().isBefore(event.windowEnd())
                    || Duration.between(event.windowStart(), event.windowEnd()).compareTo(Duration.ofHours(24)) > 0
                    || !label(event.vehicleClass(), 40) || event.vehicleCount() == null
                    || event.vehicleCount() < 0 || event.vehicleCount() > 1_000_000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        }
        for (DensityEvent event : request.snapshots()) {
            if (event == null || !eventKey(event.eventKey(), eventKeys)
                    || !label(event.direction(), 50) || event.measuredAt() == null
                    || !label(event.vehicleClass(), 40) || event.presentVehicles() == null
                    || event.presentVehicles() < 0 || event.presentVehicles() > 1_000_000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
        }
    }

    private boolean eventKey(String key, Set<String> keys) {
        return matches(key, "[A-Za-z0-9][A-Za-z0-9._:-]{0,149}") && keys.add(key);
    }

    private String registerEvent(String sourceSystem, String sourceKey, String kind, long batchId) {
        String eventId = "INGEST-" + digest(sourceSystem + ":" + sourceKey);
        database.update("""
                INSERT INTO traffic_import_event(source_event_key, source_record_key, event_kind, batch_id)
                VALUES (?, ?, ?, ?)
                """, eventId, sourceKey, kind, batchId);
        return eventId;
    }

    private boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private boolean label(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private String digest(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
