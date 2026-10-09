package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.ReferenceCatalogItemDto;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ReferenceCatalogQueryService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceCatalogQueryService.class);
    private static final Pattern SAFE_CATALOG_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,60}$");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReferenceCatalogQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Tra cứu các mục danh mục dùng chung (Reference Catalog).
     */
    public PagedResponse<ReferenceCatalogItemDto> getCatalogItems(String catalogCode, String keyword, int page, int size) {
        if (catalogCode == null || !SAFE_CATALOG_PATTERN.matcher(catalogCode).matches()) {
            throw new BadRequestException("Mã danh mục tham chiếu không hợp lệ: " + catalogCode);
        }

        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), 100);
        int offset = validPage * validSize;

        // 1. Nếu catalogCode thuộc tập dataset gốc trong raw_dataset_record (VD: c_tinhthanhpho, reference_moc_dbvn_...)
        String resolvedDatasetKey = resolveCatalogDatasetKey(catalogCode);
        if (resolvedDatasetKey != null) {
            StringBuilder rawWhere = new StringBuilder(" WHERE dataset_key = ?");
            List<Object> rawParams = new ArrayList<>();
            rawParams.add(resolvedDatasetKey);

            if (keyword != null && !keyword.isBlank()) {
                rawWhere.append(" AND (record_key ILIKE ? OR raw_payload::text ILIKE ?)");
                String kw = "%" + keyword.trim() + "%";
                rawParams.add(kw);
                rawParams.add(kw);
            }

            String rawCountSql = "SELECT count(*) FROM raw_dataset_record" + rawWhere;
            Long rawTotal = jdbcTemplate.queryForObject(rawCountSql, Long.class, rawParams.toArray());

            if (rawTotal != null && rawTotal > 0) {
                String rawSelectSql = "SELECT record_key, raw_payload FROM raw_dataset_record" + rawWhere + " ORDER BY id ASC LIMIT ? OFFSET ?";
                rawParams.add(validSize);
                rawParams.add(offset);

                List<ReferenceCatalogItemDto> rawItems = jdbcTemplate.query(rawSelectSql, (rs, rowNum) -> {
                    String recKey = rs.getString("record_key");
                    String payloadJson = rs.getString("raw_payload");

                    String itemCode = recKey;
                    String itemName = recKey;
                    String parentCode = null;
                    int sortOrder = rowNum + 1;
                    boolean isActive = true;
                    Map<String, Object> extra = new HashMap<>();

                    if (payloadJson != null) {
                        try {
                            JsonNode node = objectMapper.readTree(payloadJson);
                            if (node.hasNonNull("vidagis_fieldvalue")) itemCode = node.get("vidagis_fieldvalue").asText();
                            else if (node.hasNonNull("code")) itemCode = node.get("code").asText();
                            else if (node.hasNonNull("sign_code")) itemCode = node.get("sign_code").asText();

                            if (node.hasNonNull("vidagis_fielddisplay")) itemName = node.get("vidagis_fielddisplay").asText();
                            else if (node.hasNonNull("name")) itemName = node.get("name").asText();
                            else if (node.hasNonNull("sign_name")) itemName = node.get("sign_name").asText();

                            if (node.hasNonNull("vidagis_parent_value")) parentCode = node.get("vidagis_parent_value").asText();
                            if (node.hasNonNull("vidagis_weight")) sortOrder = node.get("vidagis_weight").asInt(sortOrder);
                            if (node.hasNonNull("vidagis_is_use")) isActive = node.get("vidagis_is_use").asBoolean(true);

                            extra = objectMapper.convertValue(node, new TypeReference<>() {});
                        } catch (Exception e) {
                            log.warn("Không thể phân tích payload danh mục {} / {}: {}", catalogCode, recKey, e.getMessage());
                        }
                    }

                    return new ReferenceCatalogItemDto(catalogCode, itemCode, itemName, parentCode, sortOrder, isActive, extra);
                }, rawParams.toArray());

                return new PagedResponse<>(rawItems, validPage, validSize, rawTotal);
            }

            // Nếu catalog tồn tại trong dataset_registry nhưng không có kết quả (lọc từ khóa hoặc bảng trống)
            return new PagedResponse<>(Collections.emptyList(), validPage, validSize, 0L);
        }

        // 2. Tra cứu từ bảng chuẩn reference_catalog
        String refCountSql = "SELECT count(*) FROM reference_catalog WHERE catalog_code = ? AND is_deleted = false";
        Long countInRef = jdbcTemplate.queryForObject(refCountSql, Long.class, catalogCode);

        if (countInRef != null && countInRef > 0) {
            StringBuilder whereSql = new StringBuilder(" WHERE catalog_code = ? AND is_deleted = false");
            List<Object> params = new ArrayList<>();
            params.add(catalogCode);

            if (keyword != null && !keyword.isBlank()) {
                whereSql.append(" AND (item_code ILIKE ? OR item_name ILIKE ?)");
                String kw = "%" + keyword.trim() + "%";
                params.add(kw);
                params.add(kw);
            }

            String countSql = "SELECT count(*) FROM reference_catalog" + whereSql;
            Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

            String selectSql = "SELECT catalog_code, item_code, item_name, parent_code, sort_order, is_active, extra_attributes " +
                    "FROM reference_catalog" + whereSql + " ORDER BY sort_order ASC, item_code ASC LIMIT ? OFFSET ?";
            params.add(validSize);
            params.add(offset);

            List<ReferenceCatalogItemDto> items = jdbcTemplate.query(selectSql, (rs, rowNum) -> {
                String attrsJson = rs.getString("extra_attributes");
                Map<String, Object> extra = Collections.emptyMap();
                if (attrsJson != null) {
                    try {
                        extra = objectMapper.readValue(attrsJson, new TypeReference<>() {});
                    } catch (Exception e) {
                        log.warn("Không thể đọc extra_attributes danh mục {}: {}", catalogCode, e.getMessage());
                    }
                }
                return new ReferenceCatalogItemDto(
                        rs.getString("catalog_code"),
                        rs.getString("item_code"),
                        rs.getString("item_name"),
                        rs.getString("parent_code"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("is_active"),
                        extra
                );
            }, params.toArray());

            return new PagedResponse<>(items, validPage, validSize, total != null ? total : 0);
        }

        // Kiểm tra xem catalogCode có tồn tại trong dataset_registry không
        String registryCheck = "SELECT count(*) FROM dataset_registry WHERE dataset_key = ? OR dataset_key ILIKE ?";
        Long regCount = jdbcTemplate.queryForObject(registryCheck, Long.class, catalogCode, "%" + catalogCode + "%");
        if (regCount != null && regCount > 0) {
            return new PagedResponse<>(Collections.emptyList(), validPage, validSize, 0L);
        }

        throw new ResourceNotFoundException("Không tìm thấy danh mục tham chiếu: " + catalogCode);
    }

    private String resolveCatalogDatasetKey(String catalogCode) {
        List<String> candidates = List.of(
                catalogCode,
                "reference_moc_dbvn_" + catalogCode,
                "reference_moc_dbvn_c_" + catalogCode,
                "reference_" + catalogCode,
                catalogCode + "_catalog"
        );

        for (String c : candidates) {
            String checkSql = "SELECT dataset_key FROM dataset_registry WHERE dataset_key = ? LIMIT 1";
            List<String> found = jdbcTemplate.query(checkSql, (rs, rn) -> rs.getString("dataset_key"), c);
            if (!found.isEmpty()) {
                return found.get(0);
            }
        }
        return null;
    }

    /**
     * Tra cứu một phần tử cụ thể trong danh mục tham chiếu theo mã item_code.
     */
    public ReferenceCatalogItemDto getCatalogItemByCode(String catalogCode, String itemCode) {
        if (catalogCode == null || !SAFE_CATALOG_PATTERN.matcher(catalogCode).matches()) {
            throw new BadRequestException("Mã danh mục tham chiếu không hợp lệ: " + catalogCode);
        }
        if (itemCode == null || itemCode.isBlank()) {
            throw new BadRequestException("Mã phần tử danh mục không được để trống");
        }

        // 1. Thử tra cứu từ bảng chuẩn reference_catalog
        String refSql = "SELECT catalog_code, item_code, item_name, parent_code, sort_order, is_active, extra_attributes " +
                "FROM reference_catalog WHERE catalog_code = ? AND item_code = ? AND is_deleted = false LIMIT 1";
        List<ReferenceCatalogItemDto> items = jdbcTemplate.query(refSql, (rs, rowNum) -> {
            String attrsJson = rs.getString("extra_attributes");
            Map<String, Object> extra = Collections.emptyMap();
            if (attrsJson != null) {
                try {
                    extra = objectMapper.readValue(attrsJson, new TypeReference<>() {});
                } catch (Exception e) {
                    log.warn("Không thể đọc extra_attributes danh mục {} / {}: {}", catalogCode, itemCode, e.getMessage());
                }
            }
            return new ReferenceCatalogItemDto(
                    rs.getString("catalog_code"),
                    rs.getString("item_code"),
                    rs.getString("item_name"),
                    rs.getString("parent_code"),
                    rs.getInt("sort_order"),
                    rs.getBoolean("is_active"),
                    extra
            );
        }, catalogCode, itemCode);

        if (!items.isEmpty()) {
            return items.get(0);
        }

        // 2. Tra cứu từ raw_dataset_record
        String resolvedDatasetKey = resolveCatalogDatasetKey(catalogCode);
        if (resolvedDatasetKey == null) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục tham chiếu: " + catalogCode);
        }

        String rawSql = "SELECT record_key, raw_payload FROM raw_dataset_record " +
                "WHERE dataset_key = ? AND (record_key = ? OR raw_payload->>'vidagis_fieldvalue' = ? OR raw_payload->>'code' = ? OR raw_payload->>'sign_code' = ?) LIMIT 1";

        List<ReferenceCatalogItemDto> rawItems = jdbcTemplate.query(rawSql, (rs, rowNum) -> {
            String recKey = rs.getString("record_key");
            String payloadJson = rs.getString("raw_payload");

            String resolvedCode = recKey;
            String itemName = recKey;
            String parentCode = null;
            int sortOrder = 1;
            boolean isActive = true;
            Map<String, Object> extra = new HashMap<>();

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.hasNonNull("vidagis_fieldvalue")) resolvedCode = node.get("vidagis_fieldvalue").asText();
                    else if (node.hasNonNull("code")) resolvedCode = node.get("code").asText();
                    else if (node.hasNonNull("sign_code")) resolvedCode = node.get("sign_code").asText();

                    if (node.hasNonNull("vidagis_fielddisplay")) itemName = node.get("vidagis_fielddisplay").asText();
                    else if (node.hasNonNull("name")) itemName = node.get("name").asText();
                    else if (node.hasNonNull("sign_name")) itemName = node.get("sign_name").asText();

                    if (node.hasNonNull("vidagis_parent_value")) parentCode = node.get("vidagis_parent_value").asText();
                    if (node.hasNonNull("vidagis_weight")) sortOrder = node.get("vidagis_weight").asInt(1);
                    if (node.hasNonNull("vidagis_is_use")) isActive = node.get("vidagis_is_use").asBoolean(true);

                    extra = objectMapper.convertValue(node, new TypeReference<>() {});
                } catch (Exception e) {
                    log.warn("Không thể phân tích payload danh mục {} / {}: {}", catalogCode, recKey, e.getMessage());
                }
            }

            return new ReferenceCatalogItemDto(catalogCode, resolvedCode, itemName, parentCode, sortOrder, isActive, extra);
        }, resolvedDatasetKey, itemCode, itemCode, itemCode, itemCode);

        if (!rawItems.isEmpty()) {
            return rawItems.get(0);
        }

        throw new ResourceNotFoundException(
                String.format("Không tìm thấy phần tử '%s' trong danh mục '%s'", itemCode, catalogCode)
        );
    }

    /**
     * Lấy danh sách tổng hợp tất cả các danh mục tham chiếu trong hệ thống.
     * Bổ sung thông tin nhóm (groupKey, groupName) và icon cho mỗi danh mục.
     */
    public List<vn.gov.drvn.kcht.dto.CatalogSummaryDto> getAllCatalogsSummary() {
        String sql = """
            SELECT dataset_key, dataset_name, total_records
            FROM dataset_registry
            WHERE dataset_key ILIKE 'reference%' OR dataset_key ILIKE 'c_%'
            ORDER BY total_records DESC, dataset_name ASC
        """;

        List<vn.gov.drvn.kcht.dto.CatalogSummaryDto> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String key = rs.getString("dataset_key");
            String name = rs.getString("dataset_name");
            long count = rs.getLong("total_records");

            String displayName = mapCatalogDisplayName(key, name);
            String description = mapCatalogDescription(key);
            String[] group = resolveCatalogGroup(key);

            return new vn.gov.drvn.kcht.dto.CatalogSummaryDto(
                    key, displayName, description, count, "dataset_registry", true,
                    group[0], group[1], group[2]
            );
        });

        // Bổ sung các danh mục tùy chỉnh từ reference_catalog
        String customCatSql = """
            SELECT catalog_code, count(*) as item_cnt
            FROM reference_catalog
            WHERE is_deleted = false
            GROUP BY catalog_code
        """;

        jdbcTemplate.query(customCatSql, (rs) -> {
            String cCode = rs.getString("catalog_code");
            long cCount = rs.getLong("item_cnt");
            boolean exists = list.stream().anyMatch(c -> c.catalogCode().equalsIgnoreCase(cCode));
            if (!exists) {
                String[] group = resolveCatalogGroup(cCode);
                list.add(new vn.gov.drvn.kcht.dto.CatalogSummaryDto(
                        cCode, mapCatalogDisplayName(cCode, cCode),
                        mapCatalogDescription(cCode), cCount, "reference_catalog", true,
                        group[0], group[1], group[2]
                ));
            }
        });

        return list;
    }

    /**
     * Tạo mới phần tử danh mục (yêu cầu quyền ADMIN hoặc MANAGER).
     */
    public ReferenceCatalogItemDto createCatalogItem(String catalogCode, ReferenceCatalogItemDto dto, String userRole) {
        checkCatalogWritePermission(userRole);

        if (catalogCode == null || !SAFE_CATALOG_PATTERN.matcher(catalogCode).matches()) {
            throw new BadRequestException("Mã danh mục không hợp lệ");
        }
        if (dto.getItemCode() == null || dto.getItemCode().isBlank()) {
            throw new BadRequestException("Mã phần tử (itemCode) không được để trống");
        }
        if (dto.getItemName() == null || dto.getItemName().isBlank()) {
            throw new BadRequestException("Tên phần tử (itemName) không được để trống");
        }

        String extraJson = "{}";
        if (dto.getExtraAttributes() != null && !dto.getExtraAttributes().isEmpty()) {
            try {
                extraJson = objectMapper.writeValueAsString(dto.getExtraAttributes());
            } catch (Exception e) {
                throw new BadRequestException("Thuộc tính mở rộng không thể chuyển đổi sang JSON");
            }
        }

        String upsertSql = """
            INSERT INTO reference_catalog (
                catalog_code, item_code, item_name, parent_code, sort_order, is_active, extra_attributes, is_deleted, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (catalog_code, item_code)
            DO UPDATE SET
                item_name = EXCLUDED.item_name,
                parent_code = EXCLUDED.parent_code,
                sort_order = EXCLUDED.sort_order,
                is_active = EXCLUDED.is_active,
                extra_attributes = EXCLUDED.extra_attributes,
                is_deleted = false,
                updated_at = CURRENT_TIMESTAMP
        """;

        jdbcTemplate.update(upsertSql,
                catalogCode,
                dto.getItemCode().trim(),
                dto.getItemName().trim(),
                dto.getParentCode(),
                dto.getSortOrder() != null ? dto.getSortOrder() : 0,
                dto.getActive() != null ? dto.getActive() : true,
                extraJson
        );

        log.info("Người dùng vai trò {} đã tạo/cập nhật mục danh mục: {}/{}", userRole, catalogCode, dto.getItemCode());
        return dto;
    }

    /**
     * Cập nhật phần tử danh mục (yêu cầu quyền ADMIN hoặc MANAGER).
     */
    public ReferenceCatalogItemDto updateCatalogItem(String catalogCode, String itemCode, ReferenceCatalogItemDto dto, String userRole) {
        checkCatalogWritePermission(userRole);

        if (catalogCode == null || itemCode == null) {
            throw new BadRequestException("Mã danh mục hoặc mã phần tử không hợp lệ");
        }
        if (dto.getItemName() == null || dto.getItemName().isBlank()) {
            throw new BadRequestException("Tên phần tử (itemName) không được để trống");
        }

        String extraJson = "{}";
        if (dto.getExtraAttributes() != null && !dto.getExtraAttributes().isEmpty()) {
            try {
                extraJson = objectMapper.writeValueAsString(dto.getExtraAttributes());
            } catch (Exception e) {
                throw new BadRequestException("Thuộc tính mở rộng không thể chuyển đổi sang JSON");
            }
        }

        String updateSql = """
            UPDATE reference_catalog
            SET item_name = ?, parent_code = ?, sort_order = ?, is_active = ?, extra_attributes = ?::jsonb, updated_at = CURRENT_TIMESTAMP
            WHERE catalog_code = ? AND item_code = ? AND is_deleted = false
        """;

        int rows = jdbcTemplate.update(updateSql,
                dto.getItemName().trim(),
                dto.getParentCode(),
                dto.getSortOrder() != null ? dto.getSortOrder() : 0,
                dto.getActive() != null ? dto.getActive() : true,
                extraJson,
                catalogCode,
                itemCode.trim()
        );

        if (rows == 0) {
            // Nếu chưa có trong reference_catalog, sao chép/tạo mới vào reference_catalog
            createCatalogItem(catalogCode, dto, userRole);
        }

        log.info("Người dùng vai trò {} đã cập nhật mục danh mục: {}/{}", userRole, catalogCode, itemCode);
        return dto;
    }

    /**
     * Xóa mềm phần tử danh mục (yêu cầu quyền ADMIN hoặc MANAGER).
     */
    public void deleteCatalogItem(String catalogCode, String itemCode, String userRole) {
        checkCatalogWritePermission(userRole);

        String softDeleteSql = """
            UPDATE reference_catalog
            SET is_deleted = true, is_active = false, updated_at = CURRENT_TIMESTAMP
            WHERE catalog_code = ? AND item_code = ?
        """;

        int rows = jdbcTemplate.update(softDeleteSql, catalogCode, itemCode);
        if (rows == 0) {
            // Đánh dấu bản ghi bị xóa vào reference_catalog để chặn hiển thị từ raw
            String markDeletedSql = """
                INSERT INTO reference_catalog (catalog_code, item_code, item_name, is_active, is_deleted, created_at, updated_at)
                VALUES (?, ?, 'Đã xóa', false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (catalog_code, item_code)
                DO UPDATE SET is_deleted = true, is_active = false, updated_at = CURRENT_TIMESTAMP
            """;
            jdbcTemplate.update(markDeletedSql, catalogCode, itemCode);
        }

        log.info("Người dùng vai trò {} đã xóa mục danh mục: {}/{}", userRole, catalogCode, itemCode);
    }

    /**
     * Kiểm tra quyền sửa danh mục theo RBAC.
     */
    private void checkCatalogWritePermission(String userRole) {
        if (userRole == null || (!userRole.contains("ADMIN") && !userRole.contains("MANAGER"))) {
            throw new vn.gov.drvn.kcht.exception.AccessDeniedException(
                    "Từ chối quyền truy cập: Chỉ Quản trị viên (ADMIN) hoặc Cán bộ quản lý (MANAGER) mới có quyền chỉnh sửa danh mục tham chiếu"
            );
        }
    }

    private String mapCatalogDisplayName(String key, String fallback) {
        if (key == null) return fallback;
        String lower = key.toLowerCase();

        // === Nhóm Địa giới hành chính ===
        if (lower.contains("tinhthanhpho")) return "Danh mục Tỉnh / Thành phố";
        if (lower.contains("xaphuong")) return "Danh mục Xã / Phường / Thị trấn";
        if (lower.contains("quanhuyen")) return "Danh mục Quận / Huyện / Thị xã";

        // === Nhóm Hạ tầng & Tuyến đường ===
        if (lower.contains("capduong")) return "Danh mục Cấp đường kỹ thuật";
        if (lower.contains("loaimat") || lower.contains("loaimatduong")) return "Danh mục Loại mặt đường";
        if (lower.contains("tuyenduongquocgia") || lower.contains("tuyenquoclo")) return "Danh mục Tuyến quốc lộ toàn quốc";
        if (lower.contains("tuyenduongnhanh")) return "Danh mục Tuyến đường nhanh / Cao tốc";
        if (lower.contains("loaiketcau")) return "Danh mục Loại kết cấu công trình";
        if (lower.contains("loaicau") || lower.contains("kieucau")) return "Danh mục Loại cầu / Kiểu kết cấu cầu";
        if (lower.contains("hamduongbo") || lower.contains("loaiham")) return "Danh mục Hầm đường bộ";
        if (lower.contains("kctcauviahe") || lower.contains("viahe")) return "Danh mục Kết cấu vỉa hè";
        if (lower.contains("ketcaumong") || lower.contains("mongduong")) return "Danh mục Kết cấu móng đường";
        if (lower.contains("loainenduong") || lower.contains("nenduong")) return "Danh mục Loại nền đường";

        // === Nhóm Báo hiệu & An toàn giao thông ===
        if (lower.contains("sohieubienbao")) return "Danh mục Số hiệu biển báo (QCVN 41)";
        if (lower.contains("loaibienbao")) return "Danh mục Loại biển báo đường bộ";
        if (lower.contains("vachson")) return "Danh mục Vạch sơn kẻ đường";
        if (lower.contains("raochan") || lower.contains("hotongiao")) return "Danh mục Hộ lan / Rào chắn";
        if (lower.contains("denthietbi") || lower.contains("dentinhieu")) return "Danh mục Đèn tín hiệu giao thông";
        if (lower.contains("gogiamtoc")) return "Danh mục Gờ giảm tốc";

        // === Nhóm Đơn vị / Tổ chức ===
        if (lower.contains("donviquanly")) return "Danh mục Đơn vị quản lý đường bộ";
        if (lower.contains("khuquanly") || lower.contains("khuqldb")) return "Danh mục Khu Quản lý đường bộ";
        if (lower.contains("nhathaudo") || lower.contains("botduong")) return "Danh mục Nhà thầu / Đơn vị bảo trì";

        // === Nhóm Kỹ thuật / Phụ trợ ===
        if (lower.contains("dinhdangbotro")) return "Danh mục Định dạng bổ trợ";
        if (lower.contains("donvido") || lower.contains("donvitinh")) return "Danh mục Đơn vị đo / Đơn vị tính";
        if (lower.contains("trangthai") || lower.contains("tinhtrang")) return "Danh mục Trạng thái / Tình trạng";
        if (lower.contains("hanhmucbaoduong") || lower.contains("hanmucbaotri")) return "Danh mục Hạng mục bảo trì";
        if (lower.contains("congtrinhphutroi") || lower.contains("phutro")) return "Danh mục Công trình phụ trợ";
        if (lower.contains("thoihan") || lower.contains("chuky")) return "Danh mục Thời hạn / Chu kỳ bảo trì";

        return fallback != null ? fallback : key;
    }

    /**
     * Ghi chú mô tả ngắn gọn cho từng nhóm danh mục.
     */
    private String mapCatalogDescription(String key) {
        if (key == null) return "Danh mục chuẩn hóa hệ thống KCHT ĐB";
        String lower = key.toLowerCase();

        if (lower.contains("tinhthanhpho")) return "63 tỉnh thành phố trực thuộc TW theo GSO";
        if (lower.contains("xaphuong")) return "Đơn vị hành chính cấp xã/phường/thị trấn";
        if (lower.contains("quanhuyen")) return "Đơn vị hành chính cấp quận/huyện/thị xã";
        if (lower.contains("capduong")) return "Cấp đường kỹ thuật theo TCVN 4054:2005";
        if (lower.contains("loaimat")) return "Phân loại mặt đường bê tông, nhựa, cấp phối";
        if (lower.contains("tuyenduongquocgia") || lower.contains("tuyenquoclo")) return "Hệ thống quốc lộ toàn quốc Việt Nam";
        if (lower.contains("sohieubienbao")) return "Ký hiệu biển báo theo QCVN 41:2019/BGTVT";
        if (lower.contains("loaiketcau")) return "Phân loại kết cấu công trình trên tuyến";
        if (lower.contains("donviquanly")) return "Cục ĐBVN, Khu QLĐB, Sở GTVT, doanh nghiệp BOT";
        if (lower.contains("tuyenduongnhanh")) return "Đường cao tốc và đường nhanh toàn quốc";

        return "Danh mục chuẩn hóa hệ thống KCHT ĐB";
    }

    /**
     * Phân nhóm danh mục tự động theo mã khóa.
     * Trả về mảng [groupKey, groupName, icon].
     */
    private String[] resolveCatalogGroup(String key) {
        if (key == null) return new String[]{"other", "Khác", "database"};
        String lower = key.toLowerCase();

        // Nhóm 1: Địa giới hành chính
        if (lower.contains("tinhthanhpho") || lower.contains("xaphuong") || lower.contains("quanhuyen") ||
            lower.contains("diaban") || lower.contains("diachinh") || lower.contains("vung")) {
            return new String[]{"administrative", "Địa giới hành chính", "environment"};
        }

        // Nhóm 2: Hạ tầng & Tuyến đường
        if (lower.contains("capduong") || lower.contains("loaimat") || lower.contains("tuyenduong") ||
            lower.contains("tuyenquoclo") || lower.contains("loaiketcau") || lower.contains("loaicau") ||
            lower.contains("hamduong") || lower.contains("loaiham") || lower.contains("kctcauviahe") ||
            lower.contains("viahe") || lower.contains("ketcaumong") || lower.contains("nenduong") ||
            lower.contains("loainenduong") || lower.contains("mongduong") || lower.contains("kieucau") ||
            lower.contains("tuyenduongnhanh") || lower.contains("rmd_") || lower.contains("ketcau")) {
            return new String[]{"infrastructure", "Hạ tầng & Tuyến đường", "road"};
        }

        // Nhóm 3: Báo hiệu & An toàn giao thông
        if (lower.contains("sohieubienbao") || lower.contains("loaibienbao") || lower.contains("bienbao") ||
            lower.contains("vachson") || lower.contains("raochan") || lower.contains("hotongiao") ||
            lower.contains("denthietbi") || lower.contains("dentinhieu") || lower.contains("gogiamtoc") ||
            lower.contains("sign") || lower.contains("atgt")) {
            return new String[]{"traffic_safety", "Báo hiệu & An toàn GT", "safety"};
        }

        // Nhóm 4: Đơn vị / Tổ chức
        if (lower.contains("donviquanly") || lower.contains("khuquanly") || lower.contains("khuqldb") ||
            lower.contains("nhathaudo") || lower.contains("botduong") || lower.contains("donvi") ||
            lower.contains("tochuc")) {
            return new String[]{"organization", "Tổ chức & Đơn vị quản lý", "team"};
        }

        // Nhóm 5: Kỹ thuật / Phụ trợ
        if (lower.contains("dinhdangbotro") || lower.contains("donvido") || lower.contains("donvitinh") ||
            lower.contains("trangthai") || lower.contains("tinhtrang") || lower.contains("hanhmucbaoduong") ||
            lower.contains("hanmucbaotri") || lower.contains("congtrinhphutroi") || lower.contains("phutro") ||
            lower.contains("thoihan") || lower.contains("chuky")) {
            return new String[]{"technical", "Kỹ thuật & Phụ trợ", "setting"};
        }

        return new String[]{"other", "Khác", "database"};
    }

}
