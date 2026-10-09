package vn.gov.drvn.kcht.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/vroad")
public class VroadSurveyController {
    private static final int ASSET_DEFECT_RADIUS_METERS = 20;
    private static final int ASSET_DEFECT_LIMIT = 30;
    private static final Pattern ROAD_CODE = Pattern.compile(
            "(?<![\\p{L}\\p{N}])QL\\.?([0-9]{1,3}[A-Z]?)(?![\\p{L}\\p{N}])",
            Pattern.CASE_INSENSITIVE);
    private static final String POINTS = """
            WITH located AS (
                SELECT r.id, r.record_key, r.raw_payload,
                    CASE WHEN r.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                        THEN (r.raw_payload->>'latitude')::double precision END AS lat,
                    CASE WHEN r.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                        THEN (r.raw_payload->>'longitude')::double precision END AS lon
                FROM raw_dataset_record r
                WHERE r.dataset_key = 'vroad_defects'
                  AND (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
            )
            SELECT p.id, p.record_key, p.lat, p.lon,
                   p.raw_payload->>'route_name' AS route_name,
                   p.raw_payload->>'route_side' AS route_side,
                   p.raw_payload->>'defect_side' AS defect_side,
                   p.raw_payload->>'chainage' AS chainage,
                   p.raw_payload->>'defect_type' AS defect_type,
                   p.raw_payload->>'area_m2' AS area_m2,
                   p.raw_payload->>'survey_date' AS survey_date,
                   image.source_url, reviewed.resolution_note, reviewed.resolved_at,
                   actor.full_name AS resolved_by, (evidence.defect_record_id IS NOT NULL) AS has_evidence
            FROM located p
            LEFT JOIN vroad_image_reference image
                ON image.dataset_key = 'vroad_defects' AND image.record_key = p.record_key
            LEFT JOIN vroad_defect_case reviewed ON reviewed.defect_record_id = p.id
            LEFT JOIN app_user actor ON actor.id = reviewed.resolved_by
            LEFT JOIN vroad_case_evidence evidence ON evidence.defect_record_id = p.id
            WHERE p.lat BETWEEN -90 AND 90 AND p.lon BETWEEN -180 AND 180
            """;

    private final JdbcTemplate database;

    public VroadSurveyController(JdbcTemplate database) {
        this.database = database;
    }

    @GetMapping("/dashboard")
    public SurveyOverview overview(@AuthenticationPrincipal UserPrincipal principal) {
        requireReader(principal);
        long[] counts = database.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE dataset_key = 'vroad_assets') AS assets,
                       COUNT(*) FILTER (WHERE dataset_key = 'vroad_defects') AS defects,
                       COUNT(*) FILTER (WHERE dataset_key = 'vroad_iri') AS iri
                FROM raw_dataset_record
                WHERE dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri')
                  AND (? = 'ROLE_ADMIN' OR vroad_record_visible(id, ?))
                """, (result, row) -> new long[]{result.getLong("assets"), result.getLong("defects"),
                        result.getLong("iri")}, principal.getRoleCode(), principal.getId());
        Long resolved = database.queryForObject("""
                SELECT COUNT(*) FROM vroad_defect_case c
                JOIN raw_dataset_record r ON r.id = c.defect_record_id
                WHERE r.dataset_key = 'vroad_defects'
                  AND (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
                """, Long.class, principal.getRoleCode(), principal.getId());
        return new SurveyOverview(counts[0], counts[1], counts[2], resolved == null ? 0 : resolved,
                breakdown(principal, "vroad_defects", "defect_type"),
                breakdown(principal, "vroad_assets", "category"));
    }

    private List<CategoryCount> breakdown(UserPrincipal principal, String dataset, String field) {
        return database.query("""
                SELECT COALESCE(NULLIF(raw_payload->>?, ''), 'Chưa phân loại') AS label,
                       COUNT(*) AS count
                FROM raw_dataset_record
                WHERE dataset_key = ? AND (? = 'ROLE_ADMIN' OR vroad_record_visible(id, ?))
                GROUP BY 1 ORDER BY count DESC, label LIMIT 8
                """, (result, row) -> new CategoryCount(result.getString("label"), result.getLong("count")),
                field, dataset, principal.getRoleCode(), principal.getId());
    }

    @GetMapping("/map/points")
    public MapPoints points(@AuthenticationPrincipal UserPrincipal principal,
                            @RequestParam double minLon, @RequestParam double minLat,
                            @RequestParam double maxLon, @RequestParam double maxLat,
                            @RequestParam(defaultValue = "500") int limit) {
        requireReader(principal);
        coordinates(minLon, minLat);
        coordinates(maxLon, maxLat);
        if (minLon >= maxLon || minLat >= maxLat || maxLon - minLon > 30 || maxLat - minLat > 30
                || limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khung bản đồ hoặc giới hạn không hợp lệ");
        }
        List<DefectPoint> rows = database.query(POINTS + """
                AND p.lon BETWEEN ? AND ? AND p.lat BETWEEN ? AND ?
                ORDER BY p.id LIMIT ?
                """, this::mapPoint, principal.getRoleCode(), principal.getId(), minLon, maxLon, minLat, maxLat, limit + 1);
        return new MapPoints(rows.subList(0, Math.min(rows.size(), limit)), rows.size() > limit);
    }

    @GetMapping("/map/assets")
    public MapAssets mapAssets(@AuthenticationPrincipal UserPrincipal principal,
                               @RequestParam double minLon, @RequestParam double minLat,
                               @RequestParam double maxLon, @RequestParam double maxLat,
                               @RequestParam(defaultValue = "500") int limit) {
        requireReader(principal);
        coordinates(minLon, minLat);
        coordinates(maxLon, maxLat);
        if (minLon >= maxLon || minLat >= maxLat || maxLon - minLon > 30 || maxLat - minLat > 30
                || limit < 1 || limit > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khung bản đồ hoặc giới hạn không hợp lệ");
        }
        List<AssetMarker> rows = database.query("""
                WITH located AS (
                    SELECT r.id, r.record_key,
                        CASE WHEN r.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                            THEN (r.raw_payload->>'latitude')::double precision END AS lat,
                        CASE WHEN r.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                            THEN (r.raw_payload->>'longitude')::double precision END AS lon
                    FROM raw_dataset_record r
                    WHERE r.dataset_key = 'vroad_assets'
                      AND (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
                )
                SELECT id, record_key, lon, lat FROM located
                WHERE lon BETWEEN ? AND ? AND lat BETWEEN ? AND ?
                ORDER BY id LIMIT ?
                """, (result, row) -> new AssetMarker(result.getLong("id"), result.getString("record_key"),
                result.getDouble("lon"), result.getDouble("lat")), principal.getRoleCode(),
                principal.getId(), minLon, maxLon, minLat, maxLat, limit + 1);
        return new MapAssets(rows.subList(0, Math.min(rows.size(), limit)), rows.size() > limit);
    }

    @GetMapping("/defects/{id}/map")
    public DefectPoint defectPoint(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        requireReader(principal);
        if (id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã hư hỏng không hợp lệ");
        return database.query(POINTS + " AND p.id = ?", this::mapPoint,
                principal.getRoleCode(), principal.getId(), id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hư hỏng có tọa độ trong phạm vi được cấp"));
    }

    @GetMapping("/assets/{id}/map")
    public AssetPoint assetPoint(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id,
                                 @RequestParam(defaultValue = "false") boolean includeRelated) {
        requireReader(principal);
        if (id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã tài sản không hợp lệ");
        AssetPoint asset = database.query("""
                WITH located AS (
                    SELECT r.id, r.record_key, r.raw_payload,
                        CASE WHEN r.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                            THEN (r.raw_payload->>'latitude')::double precision END AS lat,
                        CASE WHEN r.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                            THEN (r.raw_payload->>'longitude')::double precision END AS lon
                    FROM raw_dataset_record r
                    WHERE r.id = ? AND r.dataset_key = 'vroad_assets'
                      AND (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
                )
                SELECT item.id, item.record_key, item.lat, item.lon,
                       item.raw_payload->>'route_name' AS route_name,
                       item.raw_payload->>'chainage' AS chainage,
                       item.raw_payload->>'route_side' AS route_side,
                       item.raw_payload->>'category' AS category,
                       item.raw_payload->>'asset_type' AS asset_type,
                       item.raw_payload->>'condition' AS condition,
                       item.raw_payload->>'asset_side' AS asset_side,
                       item.raw_payload->>'survey_date' AS survey_date,
                       photo.source_url AS source_image_url
                FROM located item LEFT JOIN vroad_image_reference photo
                  ON photo.dataset_key = 'vroad_assets' AND photo.record_key = item.record_key
                WHERE item.lat BETWEEN -90 AND 90 AND item.lon BETWEEN -180 AND 180
                """, (result, row) -> new AssetPoint(result.getLong("id"), result.getString("record_key"),
                result.getDouble("lon"), result.getDouble("lat"), result.getString("route_name"),
                result.getString("chainage"), result.getString("route_side"),
                result.getString("category"),
                result.getString("asset_type"), result.getString("condition"),
                result.getString("asset_side"), result.getString("survey_date"),
                result.getString("source_image_url"), null), id, principal.getRoleCode(), principal.getId())
                .stream().findFirst().orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Không thấy tài sản có tọa độ trong phạm vi"));
        List<DefectPoint> relatedDefects = includeRelated ? relatedDefects(principal, asset) : List.of();
        RoadCatalog catalog = catalogFor(principal, asset.routeName());
        boolean truncated = relatedDefects.size() > ASSET_DEFECT_LIMIT;
        return new AssetPoint(asset.recordId(), asset.recordKey(), asset.longitude(), asset.latitude(),
                asset.routeName(), asset.chainage(), asset.routeSide(),
                asset.category(), asset.assetType(), asset.condition(),
                asset.assetSide(), asset.surveyDate(), asset.sourceImageUrl(), catalog,
                truncated ? relatedDefects.subList(0, ASSET_DEFECT_LIMIT) : relatedDefects, truncated);
    }

    private List<DefectPoint> relatedDefects(UserPrincipal principal, AssetPoint asset) {
        if (asset.routeName() == null || asset.routeName().isBlank() || asset.chainage() == null
                || asset.chainage().isBlank() || asset.routeSide() == null || asset.routeSide().isBlank()
                || asset.assetSide() == null || asset.assetSide().isBlank()) {
            return List.of();
        }
        return database.query(POINTS + """
                AND p.raw_payload->>'route_name' = ?
                AND p.raw_payload->>'chainage' = ?
                AND p.raw_payload->>'route_side' = ?
                AND p.raw_payload->>'defect_side' = ?
                AND p.lat BETWEEN -90 AND 90 AND p.lon BETWEEN -180 AND 180
                AND ST_DistanceSphere(ST_MakePoint(p.lon, p.lat), ST_MakePoint(?, ?)) <= ?
                ORDER BY ST_DistanceSphere(ST_MakePoint(p.lon, p.lat), ST_MakePoint(?, ?)), p.id
                LIMIT ?
                """, this::mapPoint, principal.getRoleCode(), principal.getId(),
                asset.routeName(), asset.chainage(), asset.routeSide(), asset.assetSide(),
                asset.longitude(), asset.latitude(), ASSET_DEFECT_RADIUS_METERS,
                asset.longitude(), asset.latitude(), ASSET_DEFECT_LIMIT + 1);
    }

    private RoadCatalog catalogFor(UserPrincipal principal, String routeName) {
        if (!"ROLE_ADMIN".equals(principal.getRoleCode()) || routeName == null) return null;
        Matcher codeMatcher = ROAD_CODE.matcher(routeName);
        if (!codeMatcher.find()) return null;
        String code = codeMatcher.group(1).toUpperCase(Locale.ROOT);
        if (codeMatcher.find()) return null;
        List<RoadCatalog> matches = database.query("""
                SELECT record_key, raw_payload->>'name_vi' AS name,
                       raw_payload->>'actual_length' AS length_km,
                       raw_payload->>'vitridiemdau-kmlytrinh' AS start_chainage,
                       raw_payload->>'vitridiemcuoi-kmlytrinh' AS end_chainage
                FROM raw_dataset_record
                WHERE dataset_key = 'mst_national_road' AND UPPER(raw_payload->>'name_vi') = ?
                ORDER BY id LIMIT 2
                """, (result, row) -> new RoadCatalog(result.getString("record_key"), result.getString("name"),
                result.getString("length_km"), result.getString("start_chainage"),
                result.getString("end_chainage")), "QL." + code);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    @GetMapping("/map/near")
    public RoadContext near(@AuthenticationPrincipal UserPrincipal principal,
                            @RequestParam double lon, @RequestParam double lat,
                            @RequestParam(defaultValue = "200") int radiusMeters,
                            @RequestParam(required = false) Long recordId) {
        requireReader(principal);
        coordinates(lon, lat);
        if (radiusMeters < 20 || radiusMeters > 2000 || (recordId != null && recordId < 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bán kính phải từ 20 đến 2.000 m");
        }
        List<DefectPoint> rows = database.query(POINTS + """
                AND (? IS NULL OR p.id = ?)
                AND ST_DistanceSphere(ST_MakePoint(p.lon, p.lat), ST_MakePoint(?, ?)) <= ?
                ORDER BY ST_DistanceSphere(ST_MakePoint(p.lon, p.lat), ST_MakePoint(?, ?)), p.id
                LIMIT 1
                """, this::mapPoint, principal.getRoleCode(), principal.getId(), recordId, recordId,
                lon, lat, radiusMeters, lon, lat);
        if (rows.isEmpty()) {
            return new RoadContext(lon, lat, radiusMeters, null, null, 0L, List.of(), null);
        }
        DefectPoint nearest = rows.get(0);
        Long assetCount = database.queryForObject("""
                SELECT COUNT(*) FROM raw_dataset_record r
                WHERE r.dataset_key = 'vroad_assets' AND r.raw_payload->>'route_name' = ?
                  AND (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
                  AND r.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                  AND r.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                  AND ST_DistanceSphere(
                      ST_MakePoint((r.raw_payload->>'longitude')::double precision,
                                   (r.raw_payload->>'latitude')::double precision),
                      ST_MakePoint(?, ?)) <= ?
                """, Long.class, nearest.routeName(), principal.getRoleCode(), principal.getId(),
                lon, lat, radiusMeters);
        return new RoadContext(lon, lat, radiusMeters, nearest.routeName(), nearest.chainage(),
                assetCount == null ? 0 : assetCount, List.of(nearest), catalogFor(principal, nearest.routeName()));
    }

    @GetMapping("/cases")
    public CasePage cases(@AuthenticationPrincipal UserPrincipal principal,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        requireReader(principal);
        if (page < 0 || page > 10000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        }
        String visible = """
                FROM vroad_defect_case c
                JOIN raw_dataset_record r ON r.id = c.defect_record_id AND r.dataset_key = 'vroad_defects'
                JOIN app_user actor ON actor.id = c.resolved_by
                LEFT JOIN vroad_case_evidence image ON image.defect_record_id = c.defect_record_id
                WHERE (? = 'ROLE_ADMIN' OR vroad_record_visible(r.id, ?))
                """;
        Long total = database.queryForObject("SELECT COUNT(*) " + visible, Long.class,
                principal.getRoleCode(), principal.getId());
        List<CaseItem> items = database.query("""
                SELECT c.defect_record_id, r.record_key, r.raw_payload->>'route_name' AS route_name,
                       r.raw_payload->>'chainage' AS chainage,
                       r.raw_payload->>'defect_type' AS defect_type,
                       c.resolution_note, c.resolved_at, actor.full_name AS resolved_by,
                       (image.defect_record_id IS NOT NULL) AS has_evidence
                """ + visible + " ORDER BY c.resolved_at DESC, c.defect_record_id DESC LIMIT ? OFFSET ?",
                (result, row) -> new CaseItem(result.getLong("defect_record_id"),
                    result.getString("record_key"), result.getString("route_name"),
                    result.getString("chainage"), result.getString("defect_type"),
                    result.getString("resolution_note"), result.getString("resolved_by"),
                    result.getObject("resolved_at", OffsetDateTime.class), result.getBoolean("has_evidence")),
                principal.getRoleCode(), principal.getId(), size, page * size);
        return new CasePage(items, total == null ? 0 : total, page, size);
    }

    @PostMapping(value = "/defects/{id}/resolve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public CaseItem resolve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id,
                            @RequestParam String note, @RequestPart(required = false) MultipartFile evidence)
            throws IOException {
        requireManager(principal);
        requireVisibleDefect(principal, id);
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.length() < 5 || trimmed.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần nội dung xử lý từ 5 đến 2.000 ký tự");
        }
        if (evidence != null && evidence.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh minh chứng vượt quá 5 MiB");
        }
        byte[] image = evidence == null || evidence.isEmpty() ? null : evidence.getBytes();
        String mediaType = null;
        if (image != null) {
            if (image.length > 5 * 1024 * 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ảnh minh chứng vượt quá 5 MiB");
            }
            mediaType = imageType(image);
            if (mediaType == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ nhận ảnh JPEG, PNG hoặc WebP hợp lệ");
            }
        }
        List<Long> created = database.query("""
                INSERT INTO vroad_defect_case(defect_record_id, resolution_note, resolved_by)
                VALUES (?, ?, ?) ON CONFLICT (defect_record_id) DO NOTHING RETURNING defect_record_id
                """, (result, row) -> result.getLong(1), id, trimmed, principal.getId());
        if (created.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hư hỏng đã có hồ sơ xử lý");
        }
        if (image != null) {
            database.update("""
                    INSERT INTO vroad_case_evidence(defect_record_id, media_type, content, sha256)
                    VALUES (?, ?, ?, ?)
                    """, id, mediaType, image, sha256(image));
        }
        database.update("""
                INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                VALUES (?, ?, 'VROAD_DEFECT_RESOLVED', 'VROAD_DEFECT', ?,
                        jsonb_build_object('status', 'RESOLVED', 'evidence', ?))
                """, principal.getId(), principal.getUsername(), Long.toString(id), image != null);
        List<CaseItem> cases = database.query("""
                SELECT c.defect_record_id, r.record_key, r.raw_payload->>'route_name' AS route_name,
                       r.raw_payload->>'chainage' AS chainage, r.raw_payload->>'defect_type' AS defect_type,
                       c.resolution_note, c.resolved_at, actor.full_name AS resolved_by,
                       (image.defect_record_id IS NOT NULL) AS has_evidence
                FROM vroad_defect_case c JOIN raw_dataset_record r ON r.id = c.defect_record_id
                JOIN app_user actor ON actor.id = c.resolved_by
                LEFT JOIN vroad_case_evidence image ON image.defect_record_id = c.defect_record_id
                WHERE c.defect_record_id = ?
                """, (result, row) -> new CaseItem(result.getLong("defect_record_id"),
                result.getString("record_key"), result.getString("route_name"),
                result.getString("chainage"), result.getString("defect_type"),
                result.getString("resolution_note"), result.getString("resolved_by"),
                result.getObject("resolved_at", OffsetDateTime.class), result.getBoolean("has_evidence")), id);
        return cases.get(0);
    }

    @GetMapping("/defects/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@AuthenticationPrincipal UserPrincipal principal, @PathVariable long id) {
        requireReader(principal);
        requireVisibleDefect(principal, id);
        List<StoredImage> images = database.query("""
                SELECT media_type, content FROM vroad_case_evidence WHERE defect_record_id = ?
                """, (result, row) -> new StoredImage(result.getString("media_type"), result.getBytes("content")), id);
        if (images.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không có ảnh minh chứng");
        }
        StoredImage image = images.get(0);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.mediaType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }

    private DefectPoint mapPoint(ResultSet row, int index) throws SQLException {
        return new DefectPoint(row.getLong("id"), row.getString("record_key"),
                row.getDouble("lon"), row.getDouble("lat"),
                row.getString("source_url"), row.getString("resolved_by"),
                row.getObject("resolved_at", OffsetDateTime.class), row.getString("resolution_note"),
                row.getBoolean("has_evidence"), row.getString("route_name"),
                row.getString("route_side"), row.getString("chainage"),
                row.getString("defect_side"),
                row.getString("defect_type"), row.getString("area_m2"),
                row.getString("survey_date"));
    }

    private void requireVisibleDefect(UserPrincipal principal, long id) {
        Long count = database.queryForObject("""
                SELECT COUNT(*) FROM raw_dataset_record
                WHERE id = ? AND dataset_key = 'vroad_defects'
                  AND (? = 'ROLE_ADMIN' OR vroad_record_visible(id, ?))
                """, Long.class, id, principal.getRoleCode(), principal.getId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hư hỏng trong phạm vi");
        }
    }

    private void requireReader(UserPrincipal principal) {
        if (principal == null || !("ROLE_ADMIN".equals(principal.getRoleCode()) ||
                (branch(principal) != null && ("ROLE_MANAGER".equals(principal.getRoleCode()) ||
                        "ROLE_EDITOR".equals(principal.getRoleCode()) || "ROLE_VIEWER".equals(principal.getRoleCode()))))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền xem khảo sát");
        }
    }

    private void requireManager(UserPrincipal principal) {
        requireReader(principal);
        if (!"ROLE_ADMIN".equals(principal.getRoleCode()) && !"ROLE_MANAGER".equals(principal.getRoleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản lý được xác nhận xử lý");
        }
    }

    private String branch(UserPrincipal principal) {
        return principal == null ? null : principal.getBranchId();
    }

    private void coordinates(double lon, double lat) {
        if (!Double.isFinite(lon) || !Double.isFinite(lat) || lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tọa độ không hợp lệ");
        }
    }

    private String imageType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) return "image/jpeg";
        if (content.length >= 8 && Arrays.equals(Arrays.copyOf(content, 8),
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (content.length >= 12 && Arrays.equals(Arrays.copyOf(content, 4), new byte[]{'R', 'I', 'F', 'F'})
                && Arrays.equals(Arrays.copyOfRange(content, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) return "image/webp";
        return null;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record MapPoints(List<DefectPoint> content, boolean truncated) {}
    public record AssetMarker(long recordId, String recordKey, double longitude, double latitude) {}
    public record MapAssets(List<AssetMarker> content, boolean truncated) {}
    public record CategoryCount(String label, long count) {}
    public record SurveyOverview(long assets, long defects, long iriSegments, long resolvedCases,
                                 List<CategoryCount> defectTypes, List<CategoryCount> assetCategories) {}
    public record RoadContext(double longitude, double latitude, int radiusMeters, String routeName,
                              String nearestChainage, long nearbyAssets, List<DefectPoint> defects,
                              RoadCatalog roadCatalog) {}
    public record CasePage(List<CaseItem> content, long totalElements, int page, int size) {}
    public record CaseItem(long recordId, String recordKey, String routeName, String chainage,
                           String defectType, String resolutionNote, String resolvedBy,
                           OffsetDateTime resolvedAt, boolean hasEvidence) {}
    public record StoredImage(String mediaType, byte[] content) {}
    public record DefectPoint(long recordId, String recordKey, double longitude, double latitude,
                              String sourceImageUrl, String resolvedBy, OffsetDateTime resolvedAt,
                              String resolutionNote, boolean hasEvidence, String routeName,
                              String routeSide, String chainage, String defectSide, String defectType,
                              String areaM2, String surveyDate) {}
    public record AssetPoint(long recordId, String recordKey, double longitude, double latitude,
                             String routeName, String chainage, String routeSide, String category, String assetType,
                             String condition, String assetSide, String surveyDate, String sourceImageUrl,
                             RoadCatalog roadCatalog, List<DefectPoint> relatedDefects,
                             boolean relatedDefectsTruncated) {
        public AssetPoint(long recordId, String recordKey, double longitude, double latitude,
                          String routeName, String chainage, String routeSide, String category, String assetType,
                          String condition, String assetSide, String surveyDate, String sourceImageUrl,
                          RoadCatalog roadCatalog) {
            this(recordId, recordKey, longitude, latitude, routeName, chainage, routeSide, category,
                    assetType, condition, assetSide, surveyDate, sourceImageUrl, roadCatalog, List.of(), false);
        }
    }
    public record RoadCatalog(String recordKey, String name, String lengthKm,
                              String startChainage, String endChainage) {}
}
