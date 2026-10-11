package vn.gov.drvn.kcht.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.dto.GeoJsonFeatureCollectionDto;
import vn.gov.drvn.kcht.dto.GeoJsonFeatureDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.mapper.AssetMapper;
import vn.gov.drvn.kcht.repository.AssetGeometryRepository;
import vn.gov.drvn.kcht.repository.AssetRecordRepository;
import vn.gov.drvn.kcht.repository.AuditLogRepository;
import vn.gov.drvn.kcht.repository.projection.AssetSpatialProjection;
import vn.gov.drvn.kcht.service.DatasetPermissionService;
import vn.gov.drvn.kcht.service.DatasetQueryService;

import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Hiện thực hóa Adapter truy cập dữ liệu tài sản hai lớp (Dual-Layer Query Bridge Adapter).
 * Đảm bảo các luồng đọc ưu tiên tầng Curated (asset_record/asset_geometry),
 * đồng thời tương thích thông suốt với dữ liệu 1.1 triệu bản ghi thô hiện có.
 * Toàn bộ các thao tác ghi (Save/Update/Delete) CHỈ thực hiện trên tầng Curated và ghi audit_log.
 */
@Component
public class AssetQueryAdapterImpl implements AssetQueryAdapter {

    private static final Logger log = LoggerFactory.getLogger(AssetQueryAdapterImpl.class);

    private final AssetRecordRepository assetRecordRepository;
    private final AssetGeometryRepository assetGeometryRepository;
    private final AuditLogRepository auditLogRepository;
    private final AssetMapper assetMapper;
    private final DatasetQueryService rawQueryService;
    private final DatasetPermissionService permissionService;
    private final ObjectMapper objectMapper;

    public AssetQueryAdapterImpl(AssetRecordRepository assetRecordRepository,
                                 AssetGeometryRepository assetGeometryRepository,
                                 AuditLogRepository auditLogRepository,
                                 AssetMapper assetMapper,
                                 DatasetQueryService rawQueryService,
                                 DatasetPermissionService permissionService,
                                 ObjectMapper objectMapper) {
        this.assetRecordRepository = assetRecordRepository;
        this.assetGeometryRepository = assetGeometryRepository;
        this.auditLogRepository = auditLogRepository;
        this.assetMapper = assetMapper;
        this.rawQueryService = rawQueryService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isCuratedPopulated(String datasetCode) {
        if (datasetCode == null || datasetCode.isBlank()) return false;
        try {
            return assetRecordRepository.countByDatasetCodeAndIsDeletedFalse(datasetCode.trim()) > 0;
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra số lượng bản ghi curated cho {}: {}", datasetCode, e.getMessage());
            return false;
        }
    }

    @Override
    public PagedResponse<RecordItemDto> getRecords(String datasetKey, String keyword, Map<String, String> allParams,
                                                  String sortParam, int page, int size, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), 100);

        if (isCuratedPopulated(datasetKey)) {
            log.debug("Đọc dữ liệu từ tầng Curated (asset_record) cho dataset {}", datasetKey);
            Sort sort = parseSortParam(sortParam);
            Pageable pageable = PageRequest.of(validPage, validSize, sort);

            Page<AssetRecordEntity> entityPage = assetRecordRepository.findAll(
                    buildCuratedSpecification(datasetKey, keyword, allParams), pageable);
            List<RecordItemDto> dtoList = entityPage.getContent().stream()
                    .map(assetMapper::toRecordItemDto)
                    .toList();

            return new PagedResponse<>(dtoList, validPage, validSize, entityPage.getTotalElements());
        }

        // Cầu nối Graceful Fallback: nếu dataset chưa được chuyển nạp vào curated, đọc qua rawQueryService
        return rawQueryService.getRecords(datasetKey, keyword, allParams, sortParam, page, size, userRole);
    }

    @Override
    public RecordItemDto getRecordById(String datasetKey, String idOrKey, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new BadRequestException("Mã định danh bản ghi không được để trống");
        }

        if (isCuratedPopulated(datasetKey)) {
            Optional<AssetRecordEntity> entityOpt = Optional.empty();
            if (idOrKey.matches("^[0-9]+$")) {
                entityOpt = assetRecordRepository.findById(Long.parseLong(idOrKey))
                        .filter(e -> !e.getIsDeleted() && datasetKey.equalsIgnoreCase(e.getDatasetCode()));
            }
            if (entityOpt.isEmpty()) {
                entityOpt = assetRecordRepository.findByRecordIdAndIsDeletedFalse(idOrKey.trim())
                        .filter(e -> datasetKey.equalsIgnoreCase(e.getDatasetCode()));
            }

            if (entityOpt.isPresent()) {
                return assetMapper.toRecordItemDto(entityOpt.get());
            }
            throw new ResourceNotFoundException("Không tìm thấy bản ghi '" + idOrKey + "' trong tập '" + datasetKey + "'");
        }

        return rawQueryService.getRecordById(datasetKey, idOrKey, userRole);
    }

    @Override
    public byte[] exportRecordsToCsv(String datasetKey, String keyword, Map<String, String> allParams,
                                     String sortParam, int limit, String userRole) {
        permissionService.checkDatasetAccess(datasetKey, userRole);
        if (!isCuratedPopulated(datasetKey)) {
            return rawQueryService.exportRecordsToCsv(datasetKey, keyword, allParams, sortParam, limit, userRole);
        }

        int validLimit = Math.min(Math.max(1, limit), 5000);
        Page<AssetRecordEntity> page = assetRecordRepository.findAll(
                buildCuratedSpecification(datasetKey, keyword, allParams),
                PageRequest.of(0, validLimit, parseSortParam(sortParam)));

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("STT,Mã bản ghi,Tên / Nhãn tài sản,Đơn vị quản lý,Trạng thái,Thời điểm cập nhật,Dữ liệu JSON\n");
        int index = 1;
        for (AssetRecordEntity entity : page.getContent()) {
            RecordItemDto record = assetMapper.toRecordItemDto(entity);
            csv.append(index++).append(',')
                    .append(csvEscape(record.getRecordKey())).append(',')
                    .append(csvEscape(entity.getName())).append(',')
                    .append(csvEscape(entity.getBranchId())).append(',')
                    .append(csvEscape(entity.getStateName() != null ? entity.getStateName() : entity.getState())).append(',')
                    .append(csvEscape(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : "")).append(',')
                    .append(csvEscape(record.getPayload() != null ? record.getPayload().toString() : "{}"))
                    .append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                                  Double maxLon, Double maxLat, String bbox,
                                                  Integer limit, String userRole) {
        return getGeoData(datasetKey, minLon, minLat, maxLon, maxLat, bbox, null, null, null, null, limit, userRole);
    }

    @Override
    public GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                                  Double maxLon, Double maxLat, String bbox,
                                                  String branch, String status, String route,
                                                  String q, Integer limit, String userRole) {
        return rawQueryService.getGeoData(datasetKey, minLon, minLat, maxLon, maxLat, bbox, branch, status, route, q, limit, userRole);
    }

    @Override
    public GeoJsonFeatureCollectionDto getSpatialClusters(String datasetKey, Double minLon, Double minLat,
                                                          Double maxLon, Double maxLat, String bbox,
                                                          Double gridSize, Integer zoom, String branch,
                                                          String status, String route, String q, String userRole) {
        return rawQueryService.getSpatialClusters(datasetKey, minLon, minLat, maxLon, maxLat, bbox, gridSize, zoom, branch, status, route, q, userRole);
    }

    @Override
    public vn.gov.drvn.kcht.dto.GisBenchmarkDto runGisBenchmark(String dataset1, String dataset2, String userRole) {
        return rawQueryService.runGisBenchmark(dataset1, dataset2, userRole);
    }

    @Override
    @Transactional
    public AssetRecordEntity saveCuratedAsset(AssetRecordEntity asset, String operatorUsername, String actionReason) {
        if (asset == null) {
            throw new BadRequestException("Dữ liệu tài sản không được để trống");
        }

        String oldValuesJson = null;
        String action = "CREATE";

        if (asset.getId() != null) {
            action = "UPDATE";
            Optional<AssetRecordEntity> existing = assetRecordRepository.findById(asset.getId());
            if (existing.isPresent()) {
                try {
                    oldValuesJson = objectMapper.writeValueAsString(assetMapper.toRecordItemDto(existing.get()));
                } catch (Exception e) {
                    log.warn("Không thể serialize giá trị cũ để ghi audit cho asset {}: {}", asset.getId(), e.getMessage());
                }
            }
        }

        asset.setUpdatedAt(OffsetDateTime.now());
        AssetRecordEntity saved = assetRecordRepository.save(asset);

        // Ghi nhật ký kiểm toán (audit_log)
        try {
            String newValuesJson = objectMapper.writeValueAsString(assetMapper.toRecordItemDto(saved));
            AuditLogEntity audit = new AuditLogEntity(
                    null,
                    operatorUsername != null ? operatorUsername : "system",
                    action,
                    "ASSET",
                    saved.getRecordId(),
                    oldValuesJson,
                    newValuesJson,
                    "127.0.0.1",
                    actionReason != null ? actionReason : "Cập nhật qua AssetQueryAdapter"
            );
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Không thể ghi audit_log cho asset {}: {}", saved.getRecordId(), e.getMessage());
        }

        return saved;
    }

    @Override
    @Transactional
    public void softDeleteCuratedAsset(Long id, String operatorUsername, String actionReason) {
        if (id == null) {
            throw new BadRequestException("Mã tài sản không được để trống");
        }

        Optional<AssetRecordEntity> existing = assetRecordRepository.findById(id);
        if (existing.isEmpty() || Boolean.TRUE.equals(existing.get().getIsDeleted())) {
            throw new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id);
        }

        AssetRecordEntity asset = existing.get();
        String oldValues = null;
        try {
            oldValues = objectMapper.writeValueAsString(assetMapper.toRecordItemDto(asset));
        } catch (Exception e) {
            log.warn("Không thể serialize asset {} trước khi xóa mềm: {}", asset.getRecordId(), e.getMessage());
        }

        assetRecordRepository.softDeleteById(id, OffsetDateTime.now(), operatorUsername);

        // Ghi nhật ký kiểm toán
        AuditLogEntity audit = new AuditLogEntity(
                null,
                operatorUsername != null ? operatorUsername : "system",
                "DELETE",
                "ASSET",
                asset.getRecordId(),
                oldValues,
                "{\"deleted\": true, \"reason\": \"" + (actionReason != null ? actionReason : "Xóa mềm") + "\"}",
                "127.0.0.1",
                actionReason
        );
        auditLogRepository.save(audit);
    }

    private Sort parseSortParam(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "id");
        }
        String[] parts = sortParam.split(",");
        String col = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return switch (col.toLowerCase()) {
            case "name" -> Sort.by(direction, "name").and(Sort.by(Sort.Direction.ASC, "id"));
            case "route_code", "routecode" -> Sort.by(direction, "routeCode").and(Sort.by(Sort.Direction.ASC, "id"));
            case "km_from", "kmfrom" -> Sort.by(direction, "kmFrom").and(Sort.by(Sort.Direction.ASC, "id"));
            case "created_at", "createdat" -> Sort.by(direction, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
            case "updated_at", "updatedat" -> Sort.by(direction, "updatedAt").and(Sort.by(Sort.Direction.ASC, "id"));
            default -> Sort.by(direction, "id");
        };
    }

    private Specification<AssetRecordEntity> buildCuratedSpecification(
            String datasetKey, String keyword, Map<String, String> allParams) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("datasetCode"), datasetKey));
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("recordId")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("routeCode")), pattern),
                        cb.like(cb.lower(root.get("routeName")), pattern),
                        cb.like(cb.lower(root.get("assetType")), pattern)
                ));
            }

            if (allParams != null) {
                for (Map.Entry<String, String> entry : allParams.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (key == null || value == null || value.isBlank()
                            || Set.of("q", "keyword", "page", "size", "sort", "limit", "format")
                            .contains(key.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    if ("exclude_asset_type".equalsIgnoreCase(key)) {
                        List<String> excluded = Arrays.stream(value.split(","))
                                .map(String::trim)
                                .filter(item -> !item.isBlank())
                                .map(item -> item.toLowerCase(Locale.ROOT))
                                .toList();
                        if (!excluded.isEmpty()) {
                            predicates.add(cb.not(cb.lower(root.get("assetType")).in(excluded)));
                        }
                        continue;
                    }

                    String field = key.startsWith("filter_") ? key.substring(7) : key;
                    switch (field) {
                        case "branch_id" -> predicates.add(cb.equal(root.get("branchId"), value.trim()));
                        case "asset_type" -> predicates.add(cb.equal(cb.lower(root.get("assetType")), value.trim().toLowerCase(Locale.ROOT)));
                        case "state", "state_name" -> predicates.add(cb.or(
                                cb.equal(root.get("state"), value.trim()),
                                cb.equal(root.get("stateName"), value.trim())));
                        case "route_code" -> predicates.add(cb.equal(root.get("routeCode"), value.trim()));
                        default -> {
                            // Dynamic JSON attributes remain available in the response but are not interpolated into SQL.
                        }
                    }
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")
                ? "\"" + escaped + "\""
                : escaped;
    }
}
