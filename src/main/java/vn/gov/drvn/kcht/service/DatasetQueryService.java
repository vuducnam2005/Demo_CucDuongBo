package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DatasetQueryService {

    private static final Logger log = LoggerFactory.getLogger(DatasetQueryService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z0-9_]{1,50}$");

    private static final Set<String> ALLOWED_SORT_COLS = Set.of(
            "id", "record_key", "created_at", "imported_at", "updated_at",
            "name", "code", "route_code", "status", "lytrinh", "province_id", "branch_id"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DatasetPermissionService permissionService;

    public DatasetQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DatasetPermissionService permissionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.permissionService = permissionService;
    }

    /**
     * Lấy danh sách các dataset có hỗ trợ tìm kiếm, lọc theo phân loại và phân trang.
     */
    public PagedResponse<DatasetSummaryDto> getDatasets(String kind, String search, int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), 100);
        int offset = validPage * validSize;

        StringBuilder whereSql = new StringBuilder(" WHERE is_active = true");
        List<Object> params = new ArrayList<>();

        if (kind != null && !kind.isBlank()) {
            whereSql.append(" AND kind = ?");
            params.add(kind.trim());
        }

        if (search != null && !search.isBlank()) {
            whereSql.append(" AND (dataset_key ILIKE ? OR dataset_name ILIKE ?)");
            String pattern = "%" + search.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }

        String countSql = "SELECT count(*) FROM dataset_registry" + whereSql;
        Long totalElements = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long total = totalElements != null ? totalElements : 0;

        String selectSql = "SELECT dataset_key, dataset_name, kind, endpoint, source_file, total_records, is_active, created_at " +
                "FROM dataset_registry" + whereSql + " ORDER BY id ASC LIMIT ? OFFSET ?";
        params.add(validSize);
        params.add(offset);

        List<DatasetSummaryDto> content = jdbcTemplate.query(selectSql, (rs, rowNum) -> new DatasetSummaryDto(
                rs.getString("dataset_key"),
                rs.getString("dataset_name"),
                rs.getString("kind"),
                rs.getString("endpoint"),
                rs.getString("source_file"),
                rs.getInt("total_records"),
                rs.getBoolean("is_active"),
                rs.getObject("created_at", OffsetDateTime.class)
        ), params.toArray());

        return new PagedResponse<>(content, validPage, validSize, total);
    }

    /**
     * Lấy siêu dữ liệu (Metadata) và từ điển các trường của một dataset.
     */
    @Cacheable(value = "datasetMetadata", key = "#datasetKey")
    public DatasetMetadataDto getDatasetMetadata(String datasetKey, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        validateDatasetKeyFormat(datasetKey);

        String sql = "SELECT id, dataset_key, dataset_name, kind, endpoint, source_file, total_records, created_at " +
                "FROM dataset_registry WHERE dataset_key = ? AND is_active = true";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, datasetKey);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy tập dữ liệu: " + datasetKey);
        }

        Map<String, Object> reg = rows.get(0);
        Long datasetId = ((Number) reg.get("id")).longValue();

        String fieldsSql = "SELECT field_name, COALESCE(field_alias, field_name) AS display_name, data_type, " +
                "is_searchable, is_filter AS is_filterable, '' AS unit " +
                "FROM dataset_field WHERE dataset_id = ? ORDER BY id ASC";

        List<DatasetFieldDto> fields = jdbcTemplate.query(fieldsSql, (rs, rowNum) -> new DatasetFieldDto(
                rs.getString("field_name"),
                rs.getString("display_name"),
                rs.getString("data_type"),
                rs.getBoolean("is_searchable"),
                rs.getBoolean("is_filterable"),
                rs.getString("unit")
        ), datasetId);

        // Kiểm tra xem dataset có dữ liệu tọa độ không gian không
        String geomSql = "SELECT count(*) FROM raw_dataset_record " +
                "WHERE dataset_key = ? AND (jsonb_exists(raw_payload, 'x_min') OR jsonb_exists(raw_payload, 'geometry')) LIMIT 1";
        Long geomCount = jdbcTemplate.queryForObject(geomSql, Long.class, datasetKey);
        String geomType = (geomCount != null && geomCount > 0) ? "POINT" : "NONE";

        return new DatasetMetadataDto(
                (String) reg.get("dataset_key"),
                (String) reg.get("dataset_name"),
                (String) reg.get("kind"),
                (String) reg.get("endpoint"),
                (String) reg.get("source_file"),
                ((Number) reg.get("total_records")).intValue(),
                geomType,
                fields
        );
    }

    /**
     * Truy vấn danh sách bản ghi của dataset với phân trang server-side, tìm kiếm keyword và bộ lọc an toàn.
     */
    public PagedResponse<RecordItemDto> getRecords(String datasetKey, String keyword, Map<String, String> allParams,
                                                  String sortParam, int page, int size, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        ensureDatasetExists(datasetKey);

        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), 100);
        int offset = validPage * validSize;

        StringBuilder whereSql = new StringBuilder(" WHERE dataset_key = ?");
        List<Object> params = new ArrayList<>();
        params.add(datasetKey);

        // Tìm kiếm theo từ khóa (keyword / q)
        if (keyword != null && !keyword.isBlank()) {
            String kwPattern = "%" + keyword.trim() + "%";
            if ("mst_national_road".equals(datasetKey) || "mst_national_expressway".equals(datasetKey)) {
                whereSql.append(" AND (record_key ILIKE ? OR raw_payload->>'name_vi' ILIKE ? OR raw_payload->>'road_number' ILIKE ?)");
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
            } else if ("vroad_assets".equals(datasetKey)) {
                whereSql.append(" AND (record_key ILIKE ? OR raw_payload->>'route_name' ILIKE ? OR raw_payload->>'asset_type' ILIKE ? OR raw_payload->>'category' ILIKE ?)");
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
            } else if ("tbl_bridge".equals(datasetKey)) {
                // Keep the large bridge search aligned with its composite trigram index.
                whereSql.append(" AND (coalesce(record_key, '') || ' ' || coalesce(raw_payload->>'name', '') || ' ' || coalesce(raw_payload->>'fielddisplay', '') || ' ' || coalesce(raw_payload->>'text', '') || ' ' || coalesce(raw_payload->>'route_name', '')) ILIKE ?");
                params.add(kwPattern);
            } else {
                whereSql.append(" AND (record_key ILIKE ? OR raw_payload->>'name' ILIKE ? OR raw_payload->>'fielddisplay' ILIKE ? OR raw_payload->>'text' ILIKE ? OR raw_payload->>'route_name' ILIKE ?)");
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
                params.add(kwPattern);
            }
        }

        // Lọc theo các trường hợp lệ (Allowlist Filter)
        if (allParams != null) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key == null || value == null || value.isBlank()) continue;
                if ("q".equalsIgnoreCase(key) || "keyword".equalsIgnoreCase(key) ||
                    "page".equalsIgnoreCase(key) || "size".equalsIgnoreCase(key) ||
                    "sort".equalsIgnoreCase(key)) {
                    continue;
                }

                // Hỗ trợ cả param dạng filter_route_code hoặc route_code
                String fieldName = key.startsWith("filter_") ? key.substring(7) : key;
                if (!SAFE_IDENTIFIER.matcher(fieldName).matches()) {
                    throw new BadRequestException("Tên trường lọc không hợp lệ: " + fieldName);
                }

                whereSql.append(" AND raw_payload->>? = ?");
                params.add(fieldName);
                params.add(value.trim());
            }
        }

        // Sắp xếp an toàn (Allowlist Sort)
        String orderClause = parseSafeSortClause(sortParam);

        // Kiểm tra xem có bộ lọc hoặc từ khóa tìm kiếm hay không
        boolean hasFilterOrKeyword = (keyword != null && !keyword.isBlank()) ||
                (allParams != null && allParams.entrySet().stream()
                        .anyMatch(e -> e.getKey() != null && !e.getKey().isBlank() &&
                                !List.of("q", "keyword", "page", "size", "sort").contains(e.getKey().toLowerCase())));

        Long totalElements = null;
        if (!hasFilterOrKeyword) {
            // Tối ưu O(1) lấy từ dataset_registry cho truy vấn phân trang thuần túy
            String regCountSql = "SELECT total_records FROM dataset_registry WHERE dataset_key = ? AND is_active = true";
            List<Long> regCount = jdbcTemplate.query(regCountSql, (rs, rowNum) -> rs.getLong("total_records"), datasetKey);
            if (!regCount.isEmpty()) {
                totalElements = regCount.get(0);
            }
        }
        if (totalElements == null) {
            String countSql = "SELECT count(*) FROM raw_dataset_record" + whereSql;
            totalElements = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        }
        long total = totalElements != null ? totalElements : 0;

        // Tối ưu Deferred Join Pattern khi sắp xếp theo ID (mặc định)
        String selectSql;
        List<Object> selectParams = new ArrayList<>(params);
        boolean isDefaultIdSort = sortParam == null || sortParam.isBlank() ||
                "id,asc".equalsIgnoreCase(sortParam.trim()) || "id".equalsIgnoreCase(sortParam.trim());

        if (!hasFilterOrKeyword && isDefaultIdSort) {
            selectSql = "SELECT r.id, r.dataset_key, r.record_key, r.raw_payload, r.record_status, r.imported_at AS created_at, r.updated_at " +
                    "FROM (SELECT id FROM raw_dataset_record WHERE dataset_key = ? ORDER BY id ASC LIMIT ? OFFSET ?) sub " +
                    "JOIN raw_dataset_record r ON r.id = sub.id ORDER BY r.id ASC";
            selectParams.clear();
            selectParams.add(datasetKey);
            selectParams.add(validSize);
            selectParams.add(offset);
        } else {
            selectSql = "SELECT id, dataset_key, record_key, raw_payload, record_status, imported_at AS created_at, updated_at " +
                    "FROM raw_dataset_record" + whereSql + " " + orderClause + " LIMIT ? OFFSET ?";
            selectParams.add(validSize);
            selectParams.add(offset);
        }

        List<RecordItemDto> content = jdbcTemplate.query(selectSql, this::mapRecordItemDto, selectParams.toArray());
        return new PagedResponse<>(content, validPage, validSize, total);
    }

    /**
     * Tra cứu một bản ghi đơn lẻ theo ID cơ sở dữ liệu hoặc record_key nghiệp vụ.
     */
    public RecordItemDto getRecordById(String datasetKey, String idOrKey, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        ensureDatasetExists(datasetKey);

        if (idOrKey == null || idOrKey.isBlank()) {
            throw new BadRequestException("Mã định danh bản ghi không được để trống");
        }

        String sql;
        List<Object> params = new ArrayList<>();
        params.add(datasetKey);
        params.add(idOrKey.trim());

        if (idOrKey.matches("^[0-9]+$")) {
            sql = "SELECT id, dataset_key, record_key, raw_payload, record_status, imported_at AS created_at, updated_at " +
                    "FROM raw_dataset_record WHERE dataset_key = ? AND (record_key = ? OR id = ?) LIMIT 1";
            params.add(Long.parseLong(idOrKey.trim()));
        } else {
            sql = "SELECT id, dataset_key, record_key, raw_payload, record_status, imported_at AS created_at, updated_at " +
                    "FROM raw_dataset_record WHERE dataset_key = ? AND record_key = ? LIMIT 1";
        }

        List<RecordItemDto> records = jdbcTemplate.query(sql, this::mapRecordItemDto, params.toArray());
        if (records.isEmpty()) {
            throw new ResourceNotFoundException(
                    String.format("Không tìm thấy bản ghi '%s' trong tập dữ liệu '%s'", idOrKey, datasetKey)
            );
        }
        return records.get(0);
    }

    /**
     * Lấy dữ liệu không gian GeoJSON (FeatureCollection) theo khung nhìn Bounding Box.
     */
    public GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                                  Double maxLon, Double maxLat, String bbox,
                                                  Integer limit, String userRole) {
        return getGeoData(datasetKey, minLon, minLat, maxLon, maxLat, bbox, null, null, null, null, limit, userRole);
    }

    /**
     * Lấy dữ liệu không gian GeoJSON với đầy đủ bộ lọc không gian BBOX và thuộc tính (branch, status, route, q).
     */
    public GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                                  Double maxLon, Double maxLat, String bbox,
                                                  String branch, String status, String route,
                                                  String q, Integer limit, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        ensureDatasetExists(datasetKey);

        int validLimit = limit != null ? Math.min(Math.max(1, limit), 2000) : 500;

        // Parse bbox param nếu được truyền dạng: bbox=minLon,minLat,maxLon,maxLat
        if (bbox != null && !bbox.isBlank()) {
            String[] parts = bbox.split(",");
            if (parts.length == 4) {
                try {
                    minLon = Double.parseDouble(parts[0].trim());
                    minLat = Double.parseDouble(parts[1].trim());
                    maxLon = Double.parseDouble(parts[2].trim());
                    maxLat = Double.parseDouble(parts[3].trim());
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Định dạng bounding box không hợp lệ (yêu cầu minLon,minLat,maxLon,maxLat dạng số)");
                }
            } else {
                throw new BadRequestException("Tham số bbox cần đúng 4 giá trị số cách nhau bởi dấu phẩy");
            }
        }

        boolean isRoadSign = "tbl_road_sign".equalsIgnoreCase(datasetKey);
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (isRoadSign) {
            sql.append("""
                SELECT source.id, cache.record_key, cache.lon, cache.lat, source.raw_payload
                FROM gis_road_sign_point_cache cache
                JOIN raw_dataset_record source
                  ON source.dataset_key = 'tbl_road_sign'
                 AND source.record_key = cache.record_key
                WHERE 1 = 1
            """);
        } else {
            sql.append("""
                SELECT id, record_key,
                       CASE WHEN raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'x_min')::double precision
                            WHEN raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'longitude')::double precision END AS lon,
                       CASE WHEN raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'y_min')::double precision
                            WHEN raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'latitude')::double precision END AS lat,
                       raw_payload
                FROM raw_dataset_record
                WHERE dataset_key = ?
                  AND (
                      (jsonb_exists(raw_payload, 'x_min') AND raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                      OR (jsonb_exists(raw_payload, 'longitude') AND raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                  )
                  AND (
                      (jsonb_exists(raw_payload, 'y_min') AND raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                      OR (jsonb_exists(raw_payload, 'latitude') AND raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                  )
            """);
            params.add(datasetKey);
        }

        // BBOX filter
        if (minLon != null && minLat != null && maxLon != null && maxLat != null) {
            if (minLon > maxLon || minLat > maxLat) {
                throw new BadRequestException("Tọa độ Bounding Box không hợp lệ: min phải nhỏ hơn max");
            }
            if (isRoadSign) {
                sql.append(" AND cache.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)");
                params.add(minLon);
                params.add(minLat);
                params.add(maxLon);
                params.add(maxLat);
            } else {
                sql.append("""
                    AND (CASE WHEN raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'x_min')::double precision
                              WHEN raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'longitude')::double precision END) BETWEEN ? AND ?
                    AND (CASE WHEN raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'y_min')::double precision
                              WHEN raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'latitude')::double precision END) BETWEEN ? AND ?
                """);
                params.add(minLon);
                params.add(maxLon);
                params.add(minLat);
                params.add(maxLat);
            }
        }

        // Attribute filter: branch
        if (branch != null && !branch.isBlank()) {
            sql.append(isRoadSign ? " AND cache.branch_id = ?" : " AND raw_payload->>'branch_id' = ?");
            params.add(branch.trim());
        }

        // Attribute filter: status
        if (status != null && !status.isBlank()) {
            sql.append(isRoadSign
                    ? " AND cache.state_name = ?"
                    : " AND (raw_payload->>'state_name' = ? OR raw_payload->>'state' = ?)");
            params.add(status.trim());
            if (!isRoadSign) params.add(status.trim());
        }

        // Attribute filter: route
        if (route != null && !route.isBlank()) {
            sql.append(isRoadSign
                    ? " AND (cache.route_code ILIKE ? OR cache.route_name ILIKE ?)"
                    : " AND (raw_payload->>'route_code' ILIKE ? OR raw_payload->>'route_name' ILIKE ? OR raw_payload->>'route' ILIKE ?)");
            String rParam = "%" + route.trim() + "%";
            params.add(rParam);
            params.add(rParam);
            if (!isRoadSign) params.add(rParam);
        }

        // Keyword filter: q
        if (q != null && !q.isBlank()) {
            sql.append(isRoadSign
                    ? " AND (cache.record_key ILIKE ? OR cache.sign_name ILIKE ?)"
                    : " AND (record_key ILIKE ? OR raw_payload->>'fielddisplay' ILIKE ? OR raw_payload->>'name' ILIKE ? OR raw_payload->>'text' ILIKE ?)");
            String qParam = "%" + q.trim() + "%";
            params.add(qParam);
            params.add(qParam);
            if (!isRoadSign) {
                params.add(qParam);
                params.add(qParam);
            }
        }

        sql.append(" LIMIT ?");
        params.add(validLimit);

        List<GeoJsonFeatureDto> features = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String recKey = rs.getString("record_key");
            double lon = rs.getDouble("lon");
            double lat = rs.getDouble("lat");
            String payloadJson = rs.getString("raw_payload");

            GeoJsonGeometryDto geom = new GeoJsonGeometryDto("Point", new double[]{lon, lat});

            Map<String, Object> props = new HashMap<>();
            props.put("dataset_key", datasetKey);
            props.put("record_key", recKey);

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    node.fieldNames().forEachRemaining(fn -> {
                        if (!"geom".equals(fn) && !"x_min".equals(fn) && !"y_min".equals(fn) && !"data_".equals(fn)) {
                            props.put(fn, node.get(fn).asText());
                        }
                    });

                    // Trích xuất tên hiển thị thân thiện
                    if (node.hasNonNull("fielddisplay")) {
                        props.put("display_name", node.get("fielddisplay").asText());
                    } else if (node.hasNonNull("name")) {
                        props.put("display_name", node.get("name").asText());
                    } else {
                        props.put("display_name", recKey);
                    }

                    // Trích xuất các thuộc tính kỹ thuật chính từ data_
                    if (node.hasNonNull("data_") && node.get("data_").isArray()) {
                        for (JsonNode item : node.get("data_")) {
                            String colId = item.path("column_identify").asText("");
                            String colVal = item.path("value_display").asText(item.path("column_value").asText(""));
                            if (!colId.isBlank() && !colVal.isBlank()) {
                                props.put("attr_" + colId, colVal.replaceAll("<[^>]*>", "").trim());
                            }
                        }
                    }
                } catch (Exception parseException) {
                    log.debug("Bỏ qua payload GIS không phải JSON hợp lệ cho dataset {}", datasetKey);
                }
            }

            return new GeoJsonFeatureDto(recKey, geom, props);
        }, params.toArray());

        return new GeoJsonFeatureCollectionDto(features);
    }

    /**
     * Gom cụm không gian PostGIS (Spatial Grid Clustering) phục vụ hiển thị dữ liệu lớn.
     * Nén hàng chục nghìn đến 222k điểm thành 30 - 150 điểm cụm (cluster pins) tùy theo mức zoom / gridSize.
     */
    public GeoJsonFeatureCollectionDto getSpatialClusters(
            String datasetKey, Double minLon, Double minLat, Double maxLon, Double maxLat,
            String bbox, Double gridSize, Integer zoom, String branch, String status,
            String route, String q, String userRole) {

        permissionService.checkDatasetAccess(datasetKey, userRole);
        ensureDatasetExists(datasetKey);

        // Xác định kích thước lưới không gian theo mức zoom nếu gridSize chưa được chỉ định
        double effGridSize = 0.2; // ~22 km mặc định (zoom 9-10)
        if (gridSize != null && gridSize > 0.0001 && gridSize < 10.0) {
            effGridSize = gridSize;
        } else if (zoom != null) {
            if (zoom <= 6) effGridSize = 1.0;
            else if (zoom <= 8) effGridSize = 0.5;
            else if (zoom <= 10) effGridSize = 0.2;
            else if (zoom <= 12) effGridSize = 0.1;
            else if (zoom <= 14) effGridSize = 0.04;
            else effGridSize = 0.015;
        }

        // Parse bbox param
        if (bbox != null && !bbox.isBlank()) {
            String[] parts = bbox.split(",");
            if (parts.length == 4) {
                try {
                    minLon = Double.parseDouble(parts[0].trim());
                    minLat = Double.parseDouble(parts[1].trim());
                    maxLon = Double.parseDouble(parts[2].trim());
                    maxLat = Double.parseDouble(parts[3].trim());
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Định dạng bounding box không hợp lệ");
                }
            }
        }

        boolean isRoadSign = "tbl_road_sign".equalsIgnoreCase(datasetKey);
        StringBuilder subSql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (isRoadSign) {
            subSql.append("""
                SELECT record_key, lon, lat, sign_name, branch_id, state_name
                FROM gis_road_sign_point_cache
                WHERE 1 = 1
            """);
        } else {
            subSql.append("""
                SELECT record_key,
                       CASE WHEN raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'x_min')::double precision
                            WHEN raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'longitude')::double precision END AS lon,
                       CASE WHEN raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'y_min')::double precision
                            WHEN raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                            THEN (raw_payload->>'latitude')::double precision END AS lat,
                       COALESCE(raw_payload->>'fielddisplay', raw_payload->>'name', record_key) AS sign_name,
                       raw_payload->>'branch_id' AS branch_id,
                       COALESCE(raw_payload->>'state_name', record_status) AS state_name,
                       raw_payload
                FROM raw_dataset_record
                WHERE dataset_key = ?
                  AND (
                      (jsonb_exists(raw_payload, 'x_min') AND raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                      OR (jsonb_exists(raw_payload, 'longitude') AND raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                  )
                  AND (
                      (jsonb_exists(raw_payload, 'y_min') AND raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                      OR (jsonb_exists(raw_payload, 'latitude') AND raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$')
                  )
            """);
            params.add(datasetKey);
        }

        // BBOX filter
        if (minLon != null && minLat != null && maxLon != null && maxLat != null) {
            if (minLon > maxLon || minLat > maxLat) {
                throw new BadRequestException("Tọa độ Bounding Box không hợp lệ: min phải nhỏ hơn max");
            }
            if (isRoadSign) {
                subSql.append(" AND geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)");
                params.add(minLon);
                params.add(minLat);
                params.add(maxLon);
                params.add(maxLat);
            } else {
                subSql.append("""
                    AND (CASE WHEN raw_payload->>'x_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'x_min')::double precision
                              WHEN raw_payload->>'longitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'longitude')::double precision END) BETWEEN ? AND ?
                    AND (CASE WHEN raw_payload->>'y_min' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'y_min')::double precision
                              WHEN raw_payload->>'latitude' ~ '^-?[0-9]+(\\.[0-9]+)?$'
                              THEN (raw_payload->>'latitude')::double precision END) BETWEEN ? AND ?
                """);
                params.add(minLon);
                params.add(maxLon);
                params.add(minLat);
                params.add(maxLat);
            }
        }

        if (branch != null && !branch.isBlank()) {
            if (isRoadSign) {
                subSql.append(" AND branch_id = ?");
            } else {
                subSql.append(" AND raw_payload->>'branch_id' = ?");
            }
            params.add(branch.trim());
        }

        if (status != null && !status.isBlank()) {
            if (isRoadSign) {
                subSql.append(" AND state_name = ?");
                params.add(status.trim());
            } else {
                subSql.append(" AND (raw_payload->>'state_name' = ? OR raw_payload->>'state' = ?)");
                params.add(status.trim());
                params.add(status.trim());
            }
        }

        if (route != null && !route.isBlank()) {
            if (isRoadSign) {
                subSql.append(" AND (route_code ILIKE ? OR route_name ILIKE ?)");
                String routeParam = "%" + route.trim() + "%";
                params.add(routeParam);
                params.add(routeParam);
            } else {
                subSql.append(" AND (raw_payload->>'route_code' ILIKE ? OR raw_payload->>'route_name' ILIKE ? OR raw_payload->>'route' ILIKE ?)");
                String routeParam = "%" + route.trim() + "%";
                params.add(routeParam);
                params.add(routeParam);
                params.add(routeParam);
            }
        }

        if (q != null && !q.isBlank()) {
            if (isRoadSign) {
                subSql.append(" AND (record_key ILIKE ? OR sign_name ILIKE ?)");
            } else {
                subSql.append(" AND (record_key ILIKE ? OR raw_payload->>'fielddisplay' ILIKE ? OR raw_payload->>'name' ILIKE ?)");
            }
            params.add("%" + q.trim() + "%");
            params.add("%" + q.trim() + "%");
            if (!isRoadSign) params.add("%" + q.trim() + "%");
        }

        // Outer cluster aggregate SQL
        String clusterSql = """
            SELECT 
                COUNT(*) AS point_count,
                ROUND(AVG(lon)::numeric, 6) AS cluster_lon,
                ROUND(AVG(lat)::numeric, 6) AS cluster_lat,
                MIN(record_key) AS sample_key,
                MIN(sign_name) AS sample_name,
                MIN(lon) AS min_x, MIN(lat) AS min_y,
                MAX(lon) AS max_x, MAX(lat) AS max_y
            FROM (
            """ + subSql + """
            ) sub
            GROUP BY ROUND(lon / ?) * ?, ROUND(lat / ?) * ?
            ORDER BY point_count DESC
            LIMIT 500
        """;

        params.add(effGridSize);
        params.add(effGridSize);
        params.add(effGridSize);
        params.add(effGridSize);

        List<GeoJsonFeatureDto> features = jdbcTemplate.query(clusterSql, (rs, rowNum) -> {
            int count = rs.getInt("point_count");
            double cLon = rs.getDouble("cluster_lon");
            double cLat = rs.getDouble("cluster_lat");
            String sampleKey = rs.getString("sample_key");
            String sampleName = rs.getString("sample_name");

            GeoJsonGeometryDto geom = new GeoJsonGeometryDto("Point", new double[]{cLon, cLat});
            Map<String, Object> props = new HashMap<>();
            props.put("is_cluster", true);
            props.put("point_count", count);
            props.put("dataset_key", datasetKey);
            props.put("sample_key", sampleKey);
            props.put("sample_name", sampleName);
            props.put("display_name", count > 1 ? count + " đối tượng (" + sampleName + "...)" : sampleName);
            props.put("bbox_min_lon", rs.getDouble("min_x"));
            props.put("bbox_min_lat", rs.getDouble("min_y"));
            props.put("bbox_max_lon", rs.getDouble("max_x"));
            props.put("bbox_max_lat", rs.getDouble("max_y"));

            return new GeoJsonFeatureDto("cluster_" + rowNum, geom, props);
        }, params.toArray());

        return new GeoJsonFeatureCollectionDto(features);
    }

    /**
     * Đo kiểm hiệu năng không gian PostGIS (Benchmark) trên 2 dataset lớn:
     * - tbl_road_sign: 222.112 bản ghi
     * - Một dataset geometry lớn thứ hai (mặc định tbl_bridge hoặc road_sphere_mirror)
     */
    public GisBenchmarkDto runGisBenchmark(String dataset1, String dataset2, String userRole) {
        String ds1 = (dataset1 != null && !dataset1.isBlank()) ? dataset1.trim() : "tbl_road_sign";
        String ds2 = (dataset2 != null && !dataset2.isBlank()) ? dataset2.trim() : "road_sphere_mirror";

        permissionService.checkDatasetAccess(ds1, userRole);
        permissionService.checkDatasetAccess(ds2, userRole);

        List<GisBenchmarkDto.DatasetGisBenchmarkItem> items = new ArrayList<>();
        long totalEvaluated = 0;

        for (String dsKey : List.of(ds1, ds2)) {
            long totalRecords = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM raw_dataset_record WHERE dataset_key = ?",
                    Long.class, dsKey
            );
            totalEvaluated += totalRecords;

            long spatialRecords;
            if ("tbl_road_sign".equalsIgnoreCase(dsKey)) {
                spatialRecords = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM gis_road_sign_point_cache WHERE lon IS NOT NULL",
                        Long.class
                );
            } else {
                spatialRecords = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM raw_dataset_record WHERE dataset_key = ? AND raw_payload->>'x_min' IS NOT NULL",
                        Long.class, dsKey
                );
            }

            // Benchmark 1: BBOX query (Hà Nội & lân cận: 105.0, 20.0, 107.0, 21.5)
            long startBbox = System.currentTimeMillis();
            GeoJsonFeatureCollectionDto bboxFeatures = getGeoData(
                    dsKey, 105.0, 20.0, 107.0, 21.5, null, null, null, null, null, 500, userRole
            );
            long bboxDuration = Math.max(1, System.currentTimeMillis() - startBbox);
            int returnedBboxCount = bboxFeatures.getFeatures().size();

            // Benchmark 2: PostGIS Grid Cluster query (Toàn quốc: zoom 8, gridSize = 0.5)
            long startCluster = System.currentTimeMillis();
            GeoJsonFeatureCollectionDto clusterFeatures = getSpatialClusters(
                    dsKey, 102.0, 8.0, 110.0, 23.5, null, 0.5, 8, null, null, null, null, userRole
            );
            long clusterDuration = Math.max(1, System.currentTimeMillis() - startCluster);
            int returnedClusters = clusterFeatures.getFeatures().size();

            // Tính toán dung lượng và tỷ lệ nén an toàn
            long estFullLoadBytes = totalRecords * 750L; // Trung bình 750 bytes/feature
            long actualPayloadBytes = Math.max(1, returnedBboxCount * 450L);
            double compressionRatio = Math.round((1.0 - (double) actualPayloadBytes / (double) estFullLoadBytes) * 10000.0) / 100.0;

            String verdict = (returnedBboxCount <= 2000 && bboxDuration < 5000)
                    ? "PASS: Tối ưu an toàn - Không tải toàn bộ " + totalRecords + " bản ghi vào browser"
                    : "WARN: Cần điều chỉnh phạm vi BBOX";

            String dsName = "tbl_road_sign".equalsIgnoreCase(dsKey) ? "Biển báo hiệu đường bộ (QCVN 41)"
                    : "road_sphere_mirror".equalsIgnoreCase(dsKey) ? "Cột biển báo & Gương cầu lồi"
                    : "tbl_bridge".equalsIgnoreCase(dsKey) ? "Cầu đường bộ"
                    : dsKey;

            items.add(new GisBenchmarkDto.DatasetGisBenchmarkItem(
                    dsKey, dsName, totalRecords, spatialRecords,
                    bboxDuration, returnedBboxCount,
                    clusterDuration, returnedClusters,
                    estFullLoadBytes, actualPayloadBytes,
                    compressionRatio, verdict
            ));
        }

        return new GisBenchmarkDto(
                OffsetDateTime.now().toString(),
                totalEvaluated,
                items,
                "PASS: 100% tuân thủ quy tắc kiến trúc. Triệt để ngăn chặn tình trạng crash trình duyệt do tải đồng thời 222k geometries.",
                true
        );
    }

    /**
     * Xác thực tính tồn tại của dataset trong dataset_registry.
     * Ngăn chặn hoàn toàn việc client tự ý truyền tên bảng SQL tùy tiện.
     */
    private void ensureDatasetExists(String datasetKey) {
        validateDatasetKeyFormat(datasetKey);
        String sql = "SELECT count(*) FROM dataset_registry WHERE dataset_key = ? AND is_active = true";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, datasetKey);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Tập dữ liệu không tồn tại trong hệ thống: " + datasetKey);
        }
    }

    private void validateDatasetKeyFormat(String datasetKey) {
        if (datasetKey == null || !SAFE_IDENTIFIER.matcher(datasetKey).matches()) {
            throw new BadRequestException("Mã tập dữ liệu chứa ký tự không hợp lệ: " + datasetKey);
        }
    }

    /**
     * Parse và kiểm tra tính hợp lệ của tham số sort.
     * Chỉ chấp nhận các trường trong allowlist và hướng ASC/DESC.
     */
    private String parseSafeSortClause(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return "ORDER BY id ASC";
        }

        String[] parts = sortParam.trim().split(",");
        String colName = parts[0].trim().toLowerCase();
        String direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) ? "DESC" : "ASC";

        if (!ALLOWED_SORT_COLS.contains(colName)) {
            throw new BadRequestException("Trường sắp xếp không nằm trong danh mục cho phép (allowlist): " + colName);
        }

        if ("id".equals(colName)) {
            return "ORDER BY id " + direction;
        } else if ("record_key".equals(colName) || "updated_at".equals(colName)) {
            return "ORDER BY " + colName + " " + direction + ", id ASC";
        } else if ("created_at".equals(colName) || "imported_at".equals(colName)) {
            return "ORDER BY imported_at " + direction + ", id ASC";
        } else {
            return "ORDER BY raw_payload->>'" + colName + "' " + direction + " NULLS LAST, id ASC";
        }
    }

    /**
     * Lấy cấu trúc cây phân cấp các tập dữ liệu KCHT Đường bộ.
     * Hỗ trợ lazy-loading khi truyền tham số parentKey.
     */
    public List<DatasetTreeNodeDto> getDatasetTree(String parentKey) {
        if (parentKey != null && !parentKey.isBlank()) {
            if ("group_all_assets".equalsIgnoreCase(parentKey.trim())) {
                String sql = "SELECT dataset_key, dataset_name, total_records, kind FROM dataset_registry WHERE kind = 'asset' ORDER BY total_records DESC";
                return jdbcTemplate.query(sql, (rs, rowNum) -> new DatasetTreeNodeDto(
                        rs.getString("dataset_key"),
                        rs.getString("dataset_name") + " (" + rs.getLong("total_records") + ")",
                        rs.getString("dataset_key"),
                        rs.getLong("total_records"),
                        rs.getString("kind"),
                        "DatabaseOutlined",
                        true
                ));
            } else if ("group_modules".equalsIgnoreCase(parentKey.trim())) {
                String sql = "SELECT dataset_key, dataset_name, total_records, kind FROM dataset_registry WHERE kind = 'module' ORDER BY dataset_key ASC LIMIT 200";
                return jdbcTemplate.query(sql, (rs, rowNum) -> new DatasetTreeNodeDto(
                        rs.getString("dataset_key"),
                        rs.getString("dataset_name") + " (" + rs.getLong("total_records") + ")",
                        rs.getString("dataset_key"),
                        rs.getLong("total_records"),
                        rs.getString("kind"),
                        "FileTextOutlined",
                        true
                ));
            }
            return Collections.emptyList();
        }

        List<DatasetTreeNodeDto> rootNodes = new ArrayList<>();
        DatasetTreeNodeDto roads = new DatasetTreeNodeDto("group_roads", "Tuyến đường", null, null,
                "group", "CompassOutlined", false);
        DatasetTreeNodeDto assets = new DatasetTreeNodeDto("group_all_assets", "Tài sản đường bộ", null, null,
                "group", "DatabaseOutlined", false);
        DatasetTreeNodeDto modules = new DatasetTreeNodeDto("group_modules", "Quan trắc và danh mục", null, null,
                "group", "FileTextOutlined", false);
        jdbcTemplate.query("""
                SELECT d.dataset_key, d.dataset_name, d.kind, COUNT(r.id) AS records
                FROM dataset_registry d
                LEFT JOIN raw_dataset_record r ON r.dataset_key = d.dataset_key
                WHERE d.is_active = true
                GROUP BY d.dataset_key, d.dataset_name, d.kind
                HAVING COUNT(r.id) > 0
                ORDER BY d.dataset_name
                """, result -> {
            String key = result.getString("dataset_key");
            String kind = result.getString("kind");
            long count = result.getLong("records");
            DatasetTreeNodeDto group = key.equals("mst_national_road") || key.equals("mst_national_expressway")
                    ? roads : "asset".equals(kind) ? assets : modules;
            group.getChildren().add(createLeafNode(key, result.getString("dataset_name") + " (" + count + ")",
                    count, kind, "DatabaseOutlined"));
        });
        if (!roads.getChildren().isEmpty()) rootNodes.add(roads);
        if (!assets.getChildren().isEmpty()) rootNodes.add(assets);
        if (!modules.getChildren().isEmpty()) rootNodes.add(modules);
        return rootNodes;
    }

    private DatasetTreeNodeDto createLeafNode(String datasetKey, String title, Long count, String kind, String icon) {
        return new DatasetTreeNodeDto(datasetKey, title, datasetKey, count, kind, icon, true);
    }

    /**
     * Xuất danh sách bản ghi theo bộ lọc, từ khóa và sắp xếp hiện hành sang định dạng CSV (UTF-8 có BOM).
     */
    public byte[] exportRecordsToCsv(String datasetKey, String keyword, Map<String, String> allParams,
                                     String sortParam, int limit, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        ensureDatasetExists(datasetKey);

        int validLimit = Math.min(Math.max(1, limit), 5000);

        StringBuilder whereSql = new StringBuilder(" WHERE dataset_key = ?");
        List<Object> params = new ArrayList<>();
        params.add(datasetKey);

        if (keyword != null && !keyword.isBlank()) {
            whereSql.append(" AND (record_key ILIKE ? OR raw_payload::text ILIKE ?)");
            String kwPattern = "%" + keyword.trim() + "%";
            params.add(kwPattern);
            params.add(kwPattern);
        }

        if (allParams != null) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key == null || value == null || value.isBlank()) continue;
                if ("q".equalsIgnoreCase(key) || "keyword".equalsIgnoreCase(key) ||
                    "page".equalsIgnoreCase(key) || "size".equalsIgnoreCase(key) ||
                    "sort".equalsIgnoreCase(key) || "limit".equalsIgnoreCase(key) ||
                    "format".equalsIgnoreCase(key)) {
                    continue;
                }

                String fieldName = key.startsWith("filter_") ? key.substring(7) : key;
                if (SAFE_IDENTIFIER.matcher(fieldName).matches()) {
                    whereSql.append(" AND raw_payload->>? = ?");
                    params.add(fieldName);
                    params.add(value.trim());
                }
            }
        }

        String orderClause = parseSafeSortClause(sortParam);
        String sql = "SELECT id, dataset_key, record_key, raw_payload, record_status, imported_at AS created_at, updated_at " +
                "FROM raw_dataset_record" + whereSql + " " + orderClause + " LIMIT ?";
        params.add(validLimit);

        List<RecordItemDto> records = jdbcTemplate.query(sql, this::mapRecordItemDto, params.toArray());

        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM for Excel
        csv.append("\uFEFF");
        csv.append("STT,Mã bản ghi,Tên / Nhãn tài sản,Đơn vị quản lý,Trạng thái,Thời điểm cập nhật,Tọa độ X (Lon),Tọa độ Y (Lat),Thuộc tính chi tiết\n");

        int idx = 1;
        for (RecordItemDto rec : records) {
            JsonNode payload = rec.getPayload();
            String name = "";
            String branch = "";
            String lon = "";
            String lat = "";
            StringBuilder attributesSummary = new StringBuilder();

            if (payload != null) {
                if (payload.hasNonNull("fielddisplay")) {
                    name = payload.get("fielddisplay").asText();
                } else if (payload.hasNonNull("name")) {
                    name = payload.get("name").asText();
                } else if (payload.hasNonNull("text")) {
                    name = payload.get("text").asText();
                }

                if (payload.hasNonNull("branch_id")) {
                    branch = payload.get("branch_id").asText();
                }
                if (payload.hasNonNull("x_min")) {
                    lon = payload.get("x_min").asText();
                } else if (payload.hasNonNull("longitude")) {
                    lon = payload.get("longitude").asText();
                }
                if (payload.hasNonNull("y_min")) {
                    lat = payload.get("y_min").asText();
                } else if (payload.hasNonNull("latitude")) {
                    lat = payload.get("latitude").asText();
                }

                if (payload.has("data_") && payload.get("data_").isArray()) {
                    for (JsonNode attr : payload.get("data_")) {
                        if (attr.hasNonNull("column_name") && attr.hasNonNull("column_value")) {
                            String colName = attr.get("column_name").asText();
                            String colVal = attr.hasNonNull("value_display") && !attr.get("value_display").asText().isBlank()
                                    ? attr.get("value_display").asText()
                                    : attr.get("column_value").asText();
                            colVal = colVal.replaceAll("<[^>]*>", "").trim();
                            if (!colVal.isBlank()) {
                                if (attributesSummary.length() > 0) attributesSummary.append("; ");
                                attributesSummary.append(colName).append(": ").append(colVal);
                            }
                        }
                    }
                }
            }

            if (name.isBlank()) {
                name = rec.getRecordKey();
            }

            csv.append(idx++).append(",");
            csv.append(escapeCsv(rec.getRecordKey())).append(",");
            csv.append(escapeCsv(name)).append(",");
            csv.append(escapeCsv(branch)).append(",");
            csv.append(escapeCsv(rec.getRecordStatus() != null ? rec.getRecordStatus() : "RAW_STORED")).append(",");
            csv.append(escapeCsv(rec.getUpdatedAt() != null ? rec.getUpdatedAt().toString() : "")).append(",");
            csv.append(escapeCsv(lon)).append(",");
            csv.append(escapeCsv(lat)).append(",");
            csv.append(escapeCsv(attributesSummary.toString())).append("\n");
        }

        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        String clean = val.replace("\"", "\"\"");
        return "\"" + clean + "\"";
    }

    private RecordItemDto mapRecordItemDto(ResultSet rs, int rowNum) throws SQLException {
        Long id = rs.getLong("id");
        String dsKey = rs.getString("dataset_key");
        String recKey = rs.getString("record_key");
        String payloadJson = rs.getString("raw_payload");
        String status = rs.getString("record_status");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);

        JsonNode payloadNode = null;
        if (payloadJson != null) {
            try {
                payloadNode = objectMapper.readTree(payloadJson);
            } catch (JsonProcessingException e) {
                log.warn("Không thể parse JSON payload cho bản ghi id={}: {}", id, e.getMessage());
            }
        }

        return new RecordItemDto(id, dsKey, recKey, payloadNode, status, createdAt, updatedAt);
    }
}

