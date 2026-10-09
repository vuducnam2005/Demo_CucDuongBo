package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/vroad/inbound")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "VroadAI proposed inbound", description = "Local staging only; no official API contract or outbound connection")
@SecurityRequirement(name = "bearerAuth")
public class VroadInboundController {
    private final JdbcTemplate database;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;

    public VroadInboundController(JdbcTemplate database, ObjectMapper mapper, TransactionTemplate transaction) {
        this.database = database;
        this.mapper = mapper;
        this.transaction = transaction;
    }

    public record IngestRequest(String requestKey, String schemaVersion, List<JsonNode> records) {}
    public record Batch(long id, String requestKey, String schemaVersion, String status,
                        long receivedAt, int pending, int invalid) {}
    public record Item(long id, String sourceRecordKey, String recordKind, String status,
                       JsonNode validationErrors) {}
    public record BatchDetail(Batch batch, List<Item> records) {}

    @PostMapping("/batches")
    @Operation(summary = "Stage a proposed VroadAI demo batch, never publish to official assets")
    public BatchDetail stage(@AuthenticationPrincipal UserPrincipal principal, @RequestBody IngestRequest request) {
        if (request == null || request.requestKey() == null
                || !request.requestKey().matches("[A-Za-z0-9][A-Za-z0-9._-]{5,119}")
                || !"demo-proposal-v1".equals(request.schemaVersion())
                || request.records() == null || request.records().isEmpty()
                || request.records().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lô nhập đề xuất không hợp lệ");
        }
        String payload = mapper.valueToTree(request).toString();
        if (payload.getBytes(StandardCharsets.UTF_8).length > 1_000_000) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String hash = sha256(payload);
        return transaction.execute(status -> {
            List<Long> inserted = database.query("""
                    INSERT INTO vroad_ingest_batch(request_key, schema_version, payload_sha256, submitted_by)
                    VALUES (?, ?, ?, ?) ON CONFLICT (request_key) DO NOTHING RETURNING id
                    """, (row, index) -> row.getLong(1), request.requestKey(),
                    request.schemaVersion(), hash, principal.getId());
            if (inserted.isEmpty()) {
                List<Long> matching = database.query("""
                        SELECT id FROM vroad_ingest_batch
                        WHERE request_key = ? AND payload_sha256 = ?
                        """, (row, index) -> row.getLong(1), request.requestKey(), hash);
                if (matching.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Mã lô đã dùng cho nội dung khác");
                return detail(matching.get(0));
            }
            long batchId = inserted.get(0);
            Set<String> seen = new HashSet<>();
            for (JsonNode item : request.records()) {
                List<String> errors = validate(item, seen);
                String source = item != null && item.path("sourceId").isTextual()
                        ? item.path("sourceId").asText().trim() : null;
                String kind = item != null && item.path("kind").isTextual()
                        ? item.path("kind").asText() : null;
                if (source != null && source.length() > 150) source = null;
                if (kind != null && kind.length() > 40) kind = null;
                database.update("""
                        INSERT INTO vroad_ingest_record(batch_id, source_record_key, record_kind,
                            payload, validation_errors, status)
                        VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?)
                        """, batchId, source, kind, item == null ? "null" : item.toString(),
                        mapper.valueToTree(errors).toString(), errors.isEmpty() ? "PENDING" : "INVALID");
            }
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                    VALUES (?, ?, 'INBOUND_STAGED', 'VROAD_BATCH', ?,
                            jsonb_build_object('recordCount', ?, 'schema', ?))
                    """, principal.getId(), principal.getUsername(), String.valueOf(batchId),
                    request.records().size(), request.schemaVersion());
            return detail(batchId);
        });
    }

    List<String> validate(JsonNode item, Set<String> seen) {
        List<String> errors = new ArrayList<>();
        if (item == null || !item.isObject()) {
            errors.add("Bản ghi phải là đối tượng JSON");
            return errors;
        }
        JsonNode source = item.path("sourceId");
        JsonNode kind = item.path("kind");
        if (!source.isTextual() || source.asText().isBlank() || source.asText().length() > 150) {
            errors.add("Cần sourceId dạng chuỗi không quá 150 ký tự");
        }
        if (!kind.isTextual() || !Set.of("ASSET", "DEFECT", "TRAFFIC").contains(kind.asText())) {
            errors.add("kind phải là ASSET, DEFECT hoặc TRAFFIC");
        }
        if (errors.isEmpty() && !seen.add(kind.asText() + "::" + source.asText().trim())) {
            errors.add("Trùng mã nguồn trong cùng lô");
        }
        JsonNode longitude = item.path("longitude");
        JsonNode latitude = item.path("latitude");
        boolean hasLongitude = !longitude.isMissingNode() && !longitude.isNull();
        boolean hasLatitude = !latitude.isMissingNode() && !latitude.isNull();
        if (hasLongitude != hasLatitude || (hasLongitude && (!longitude.isNumber() || !latitude.isNumber()
                || longitude.asDouble() < -180 || longitude.asDouble() > 180
                || latitude.asDouble() < -90 || latitude.asDouble() > 90))) {
            errors.add("Tọa độ mẫu phải đủ cặp longitude/latitude hợp lệ");
        }
        JsonNode confidence = item.path("confidence");
        if (!confidence.isMissingNode() && !confidence.isNull()
                && (!confidence.isNumber() || confidence.asDouble() < 0 || confidence.asDouble() > 1)) {
            errors.add("confidence phải là số từ 0 đến 1");
        }
        if (item.toString().getBytes(StandardCharsets.UTF_8).length > 10000) {
            errors.add("Bản ghi vượt 10 KiB");
        }
        return errors;
    }

    private String sha256(String payload) {
        try {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    @GetMapping("/batches")
    @Operation(summary = "List proposed inbound batches staged locally")
    public List<Batch> batches(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return database.query("""
                SELECT b.id, b.request_key, b.schema_version, b.status,
                       (EXTRACT(EPOCH FROM b.received_at) * 1000)::bigint AS received_epoch,
                       COUNT(r.id) FILTER (WHERE r.status = 'PENDING') AS pending,
                       COUNT(r.id) FILTER (WHERE r.status = 'INVALID') AS invalid
                FROM vroad_ingest_batch b LEFT JOIN vroad_ingest_record r ON b.id = r.batch_id
                GROUP BY b.id ORDER BY b.received_at DESC, b.id DESC LIMIT ? OFFSET ?
                """, (row, index) -> new Batch(row.getLong("id"), row.getString("request_key"),
                row.getString("schema_version"), row.getString("status"),
                row.getLong("received_epoch"), row.getInt("pending"), row.getInt("invalid")),
                size, page * size);
    }

    @GetMapping("/batches/{id}")
    @Operation(summary = "Inspect validation results without exposing raw staging payload")
    public BatchDetail detail(@PathVariable long id) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Batch batch = database.query("""
                SELECT b.id, b.request_key, b.schema_version, b.status,
                       (EXTRACT(EPOCH FROM b.received_at) * 1000)::bigint AS received_epoch,
                       COUNT(r.id) FILTER (WHERE r.status = 'PENDING') AS pending,
                       COUNT(r.id) FILTER (WHERE r.status = 'INVALID') AS invalid
                FROM vroad_ingest_batch b LEFT JOIN vroad_ingest_record r ON b.id = r.batch_id
                WHERE b.id = ? GROUP BY b.id
                """, (row, index) -> new Batch(row.getLong("id"), row.getString("request_key"),
                row.getString("schema_version"), row.getString("status"),
                row.getLong("received_epoch"), row.getInt("pending"), row.getInt("invalid")), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Item> records = database.query("""
                SELECT id, source_record_key, record_kind, status, validation_errors
                FROM vroad_ingest_record WHERE batch_id = ? ORDER BY id
                """, (row, index) -> new Item(row.getLong("id"), row.getString("source_record_key"),
                row.getString("record_kind"), row.getString("status"), parse(row.getString("validation_errors"))), id);
        return new BatchDetail(batch, records);
    }

    private JsonNode parse(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception error) {
            throw new IllegalStateException("Stored validation result is not JSON", error);
        }
    }
}
