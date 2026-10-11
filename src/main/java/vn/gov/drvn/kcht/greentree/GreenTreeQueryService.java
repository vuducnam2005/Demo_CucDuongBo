package vn.gov.drvn.kcht.greentree;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.mapper.GreenTreeMapper;
import vn.gov.drvn.kcht.service.DatasetPermissionService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GreenTreeQueryService {

    public static final String DATASET_KEY = "green_trees";
    private static final Set<String> IGNORED_PARAMS = Set.of(
            "q", "keyword", "page", "size", "sort", "limit", "format", "exclude_asset_type"
    );

    private final JdbcTemplate database;
    private final GreenTreeStorageProperties storageProperties;
    private final GreenTreeAccessService accessService;
    private final DatasetPermissionService permissionService;
    private final GreenTreeMapper mapper;

    public GreenTreeQueryService(JdbcTemplate database,
                                 GreenTreeStorageProperties storageProperties,
                                 GreenTreeAccessService accessService,
                                 DatasetPermissionService permissionService,
                                 GreenTreeMapper mapper) {
        this.database = database;
        this.storageProperties = storageProperties;
        this.accessService = accessService;
        this.permissionService = permissionService;
        this.mapper = mapper;
    }

    public PagedResponse<RecordItemDto> getRecords(String keyword, Map<String, String> allParams,
                                                    String sortParam, int page, int size, String userRole) {
        return queryRecords(keyword, allParams, sortParam, Math.max(0, page), Math.min(Math.max(1, size), 100), userRole);
    }

    public RecordItemDto getRecordById(String idOrKey, String userRole) {
        permissionService.checkDatasetAccess(DATASET_KEY, userRole);
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new BadRequestException("Mã định danh cây xanh không được để trống");
        }
        QueryContext context = context(userRole);
        StringBuilder where = new StringBuilder(context.baseWhere());
        List<Object> params = new ArrayList<>(context.baseParams());
        if (idOrKey.matches("^[0-9]+$")) {
            where.append(" AND (tree.id = ? OR tree.record_id = ?)");
            params.add(Long.parseLong(idOrKey));
            params.add(idOrKey.trim());
        } else {
            where.append(" AND tree.record_id = ?");
            params.add(idOrKey.trim());
        }
        List<RecordItemDto> rows = database.query(context.selectSql() + where + " LIMIT 1", mapper::mapRow,
                params.toArray());
        return rows.stream().findFirst().orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy cây xanh '" + idOrKey + "' trong phạm vi được cấp"));
    }

    public byte[] exportRecordsToCsv(String keyword, Map<String, String> allParams,
                                     String sortParam, int limit, String userRole) {
        int validLimit = Math.min(Math.max(1, limit), 5000);
        PagedResponse<RecordItemDto> page = queryRecords(keyword, allParams, sortParam, 0, validLimit, userRole);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("STT,Mã bản ghi,Tên cây,Đơn vị quản lý,Trạng thái,Kinh độ,Vĩ độ,Dữ liệu JSON\n");
        int index = 1;
        for (RecordItemDto record : page.getContent()) {
            var payload = record.getPayload();
            csv.append(index++).append(',')
                    .append(csvEscape(record.getRecordKey())).append(',')
                    .append(csvEscape(payload.path("name").asText(""))).append(',')
                    .append(csvEscape(payload.path("branch_id").asText(""))).append(',')
                    .append(csvEscape(payload.path("state_name").asText(record.getRecordStatus()))).append(',')
                    .append(csvEscape(payload.path("longitude").asText(""))).append(',')
                    .append(csvEscape(payload.path("latitude").asText(""))).append(',')
                    .append(csvEscape(payload.toString())).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public long countActive(String userRole) {
        QueryContext context = context(userRole);
        Long count = database.queryForObject("SELECT COUNT(*)" + context.fromSql() + context.baseWhere(),
                Long.class, context.baseParams().toArray());
        return count == null ? 0 : count;
    }

    public long countAllActive() {
        String sql = storageProperties.getMode().readsNewTable()
                ? "SELECT COUNT(*) FROM green_tree_record WHERE is_deleted = FALSE"
                : """
                  SELECT COUNT(*) FROM asset_record
                  WHERE dataset_code = 'green_trees' AND asset_type = 'TREE' AND is_deleted = FALSE
                  """;
        Long count = database.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private PagedResponse<RecordItemDto> queryRecords(String keyword, Map<String, String> allParams,
                                                      String sortParam, int page, int size, String userRole) {
        permissionService.checkDatasetAccess(DATASET_KEY, userRole);
        QueryContext context = context(userRole);
        StringBuilder where = new StringBuilder(context.baseWhere());
        List<Object> params = new ArrayList<>(context.baseParams());

        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim() + "%";
            where.append(" AND (tree.record_id ILIKE ? OR tree.name ILIKE ? OR tree.route_code ILIKE ?")
                    .append(" OR tree.route_name ILIKE ? OR ").append(context.field("tree_code")).append(" ILIKE ?")
                    .append(" OR ").append(context.field("species_name")).append(" ILIKE ?")
                    .append(" OR ").append(context.field("scientific_name")).append(" ILIKE ?)");
            for (int i = 0; i < 7; i++) params.add(pattern);
        }

        if (allParams != null) {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || value.isBlank()
                        || IGNORED_PARAMS.contains(key.toLowerCase(Locale.ROOT))) continue;
                String field = key.startsWith("filter_") ? key.substring(7) : key;
                String expression = switch (field) {
                    case "branch_id" -> "tree.branch_id";
                    case "route_code" -> "tree.route_code";
                    case "province_id" -> "tree.province_id";
                    case "state" -> "tree.state";
                    case "state_name" -> "tree.state_name";
                    case "active_status" -> "tree.active_status";
                    case "species_name", "health_status", "risk_level", "tree_code" -> context.field(field);
                    case "dataset_code" -> null;
                    case "asset_type" -> null;
                    default -> throw new BadRequestException("Trường lọc cây xanh không được hỗ trợ: " + field);
                };
                if (expression == null) {
                    String expected = "asset_type".equals(field) ? "TREE" : DATASET_KEY;
                    if (!expected.equalsIgnoreCase(value.trim())) where.append(" AND FALSE");
                } else {
                    where.append(" AND ").append(expression).append(" = ?");
                    params.add(value.trim());
                }
            }
        }

        Long total = database.queryForObject("SELECT COUNT(*)" + context.fromSql() + where,
                Long.class, params.toArray());
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(size);
        selectParams.add(page * size);
        String sql = context.selectSql() + where + orderClause(sortParam, context) + " LIMIT ? OFFSET ?";
        List<RecordItemDto> content = database.query(sql, mapper::mapRow, selectParams.toArray());
        return new PagedResponse<>(content, page, size, total == null ? 0 : total);
    }

    private QueryContext context(String suppliedRole) {
        boolean newTable = storageProperties.getMode().readsNewTable();
        GreenTreeAccessService.AccessScope scope = accessService.currentScope(suppliedRole);
        String from = newTable
                ? " FROM green_tree_record tree"
                : " FROM asset_record tree LEFT JOIN raw_dataset_record raw ON raw.id = tree.raw_record_id"
                + " LEFT JOIN asset_geometry geometry ON geometry.asset_id = tree.id";
        StringBuilder where = new StringBuilder(" WHERE tree.is_deleted = FALSE");
        List<Object> params = new ArrayList<>();
        if (!newTable) where.append(" AND tree.dataset_code = 'green_trees' AND tree.asset_type = 'TREE'");
        appendVisibility(where, params, scope);
        return new QueryContext(newTable, from, where.toString(), params);
    }

    private void appendVisibility(StringBuilder where, List<Object> params,
                                  GreenTreeAccessService.AccessScope scope) {
        if (scope.isAdmin()) return;
        if (scope.userId() == null || scope.userId() <= 0 || !scope.hasBranch()) {
            where.append(" AND FALSE");
            return;
        }
        where.append(" AND ((tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, ?))")
                .append(" OR (tree.raw_record_id IS NULL AND tree.branch_id = ?))");
        params.add(scope.userId());
        params.add(scope.branchId());
    }

    private String orderClause(String sortParam, QueryContext context) {
        String raw = sortParam == null || sortParam.isBlank() ? "id,asc" : sortParam.trim();
        String[] parts = raw.split(",");
        String direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()) ? "DESC" : "ASC";
        String expression = switch (parts[0].trim().toLowerCase(Locale.ROOT)) {
            case "id" -> "tree.id";
            case "record_id", "record_key" -> "tree.record_id";
            case "name" -> "tree.name";
            case "route_code", "routecode" -> "tree.route_code";
            case "branch_id", "branchid" -> "tree.branch_id";
            case "created_at", "createdat" -> "tree.created_at";
            case "updated_at", "updatedat" -> "tree.updated_at";
            case "species_name" -> context.field("species_name");
            case "health_status" -> context.field("health_status");
            case "risk_level" -> context.field("risk_level");
            default -> throw new BadRequestException("Trường sắp xếp cây xanh không được hỗ trợ: " + parts[0]);
        };
        return " ORDER BY " + expression + " " + direction + " NULLS LAST, tree.id ASC";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")
                ? "\"" + escaped + "\"" : escaped;
    }

    private record QueryContext(boolean newTable, String fromSql, String baseWhere, List<Object> baseParams) {
        String field(String name) {
            if (newTable) return "tree." + name;
            return switch (name) {
                case "health_status" -> "COALESCE(NULLIF(tree.attributes->>'health_status', ''), "
                        + "NULLIF(tree.attributes->>'condition', ''), NULLIF(raw.raw_payload->>'health_status', ''), "
                        + "NULLIF(raw.raw_payload->>'condition', ''))";
                default -> "COALESCE(NULLIF(tree.attributes->>'" + name + "', ''), "
                        + "NULLIF(raw.raw_payload->>'" + name + "', ''))";
            };
        }

        String selectSql() {
            String decimalPattern = "^[+]?[0-9]+([.][0-9]+)?$";
            String treeCode = field("tree_code");
            String species = field("species_name");
            String scientific = field("scientific_name");
            String health = field("health_status");
            String risk = field("risk_level");
            String notes = field("notes");
            String diameter = newTable ? "tree.diameter_cm" : numericField("diameter_cm", decimalPattern);
            String height = newTable ? "tree.height_m" : numericField("height_m", decimalPattern);
            String canopy = newTable ? "tree.canopy_diameter_m" : numericField("canopy_diameter_m", decimalPattern);
            String planting = newTable ? "tree.planting_year" : integerField("planting_year");
            String longitude = newTable ? "ST_X(tree.geom)::numeric" : legacyCoordinate(true);
            String latitude = newTable ? "ST_Y(tree.geom)::numeric" : legacyCoordinate(false);
            return "SELECT tree.id, tree.raw_record_id, tree.record_id, tree.gid, tree.name, "
                    + treeCode + " AS tree_code, " + species + " AS species_name, "
                    + scientific + " AS scientific_name, tree.route_code, tree.route_name, tree.km_from, tree.km_to, "
                    + "tree.lytrinh, tree.province_id, tree.province_name, tree.district_name, tree.town_name, "
                    + "tree.road_class, tree.road_type, tree.organization_id, tree.branch_id, tree.branch_name, "
                    + "tree.management_agency, tree.state, tree.state_name, tree.level_state, tree.active_status, "
                    + "tree.maintain_value, tree.construction_year, tree.parent_id, " + diameter + " AS diameter_cm, "
                    + height + " AS height_m, " + canopy + " AS canopy_diameter_m, " + planting
                    + " AS planting_year, " + health + " AS health_status, " + risk + " AS risk_level, "
                    + notes + " AS notes, " + longitude + " AS longitude, " + latitude + " AS latitude, "
                    + "tree.attributes::text AS attributes, tree.version, tree.created_at, tree.updated_at" + fromSql;
        }

        private String numericField(String name, String pattern) {
            String value = "COALESCE(tree.attributes->>'" + name + "', raw.raw_payload->>'" + name + "')";
            return "CASE WHEN " + value + " ~ '" + pattern + "' THEN CASE WHEN (" + value
                    + ")::numeric >= 0 THEN (" + value + ")::numeric END END";
        }

        private String integerField(String name) {
            String value = "COALESCE(tree.attributes->>'" + name + "', raw.raw_payload->>'" + name + "')";
            return "CASE WHEN " + value + " ~ '^-?[0-9]+$' THEN CASE WHEN (" + value
                    + ")::numeric BETWEEN -2147483648 AND 2147483647 THEN (" + value + ")::integer END END";
        }

        private String legacyCoordinate(boolean longitude) {
            String function = longitude ? "ST_X" : "ST_Y";
            String key = longitude ? "longitude" : "latitude";
            return "CASE WHEN geometry.point_geom IS NOT NULL AND ST_IsValid(geometry.point_geom) "
                    + "AND ST_X(geometry.point_geom) BETWEEN -180 AND 180 "
                    + "AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90 THEN "
                    + function + "(geometry.point_geom)::numeric WHEN (CASE WHEN geometry.geom IS NOT NULL "
                    + "AND GeometryType(geometry.geom) = 'POINT' AND ST_IsValid(geometry.geom) "
                    + "THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180 "
                    + "AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT' "
                    + "AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90 THEN " + function
                    + "(geometry.geom)::numeric WHEN tree.attributes ? 'longitude' OR tree.attributes ? 'latitude' "
                    + "THEN CASE WHEN (CASE WHEN tree.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$' "
                    + "THEN (tree.attributes->>'longitude')::numeric END) BETWEEN -180 AND 180 "
                    + "AND (CASE WHEN tree.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$' "
                    + "THEN (tree.attributes->>'latitude')::numeric END) BETWEEN -90 AND 90 "
                    + "THEN (tree.attributes->>'" + key + "')::numeric END "
                    + "WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$' "
                    + "THEN (raw.raw_payload->>'longitude')::numeric END) BETWEEN -180 AND 180 "
                    + "AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$' "
                    + "THEN (raw.raw_payload->>'latitude')::numeric END) BETWEEN -90 AND 90 "
                    + "THEN (raw.raw_payload->>'" + key + "')::numeric END";
        }
    }
}

