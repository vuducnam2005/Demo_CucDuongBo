package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.gov.drvn.kcht.dto.AssetCreateUpdateDto;
import vn.gov.drvn.kcht.dto.ImportExecutionRequestDto;
import vn.gov.drvn.kcht.dto.ImportExecutionResponseDto;
import vn.gov.drvn.kcht.dto.ImportPreviewResponseDto;
import vn.gov.drvn.kcht.dto.ImportValidationErrorDto;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.entity.GreenTreeRecordEntity;
import vn.gov.drvn.kcht.exception.AccessDeniedException;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ConflictException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.repository.AuditLogRepository;
import vn.gov.drvn.kcht.repository.DatasetRegistryRepository;
import vn.gov.drvn.kcht.repository.GreenTreeRecordRepository;
import vn.gov.drvn.kcht.service.DatasetPermissionService;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GreenTreeCrudService {

    private final GreenTreeStorageProperties storageProperties;
    private final GreenTreeAccessService accessService;
    private final DatasetPermissionService permissionService;
    private final LegacyGreenTreeStore legacyStore;
    private final GreenTreeDualWriteCoordinator dualWriteCoordinator;
    private final GreenTreeRecordRepository greenTreeRepository;
    private final DatasetRegistryRepository datasetRegistryRepository;
    private final AuditLogRepository auditLogRepository;
    private final GreenTreeQueryService queryService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public GreenTreeCrudService(GreenTreeStorageProperties storageProperties,
                                GreenTreeAccessService accessService,
                                DatasetPermissionService permissionService,
                                LegacyGreenTreeStore legacyStore,
                                GreenTreeDualWriteCoordinator dualWriteCoordinator,
                                GreenTreeRecordRepository greenTreeRepository,
                                DatasetRegistryRepository datasetRegistryRepository,
                                AuditLogRepository auditLogRepository,
                                GreenTreeQueryService queryService,
                                ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager) {
        this.storageProperties = storageProperties;
        this.accessService = accessService;
        this.permissionService = permissionService;
        this.legacyStore = legacyStore;
        this.dualWriteCoordinator = dualWriteCoordinator;
        this.greenTreeRepository = greenTreeRepository;
        this.datasetRegistryRepository = datasetRegistryRepository;
        this.auditLogRepository = auditLogRepository;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    @Transactional
    public RecordItemDto createRecord(AssetCreateUpdateDto dto, String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", GreenTreeQueryService.DATASET_KEY, "CREATE", userRole);
        RecordItemDto created = createInternal(dto, userRole, username, ip, true);
        refreshDatasetCount();
        return created;
    }

    @Transactional
    public RecordItemDto updateRecord(String idOrKey, AssetCreateUpdateDto dto,
                                      String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", GreenTreeQueryService.DATASET_KEY, "UPDATE", userRole);
        validate(dto, false);
        RecordItemDto before = queryService.getRecordById(idOrKey, userRole);
        GreenTreeStorageMode mode = storageProperties.getMode();
        GreenTreeAccessService.AccessScope scope = accessService.currentScope(userRole);
        if (mode.writesLegacyTable()) {
            AssetRecordEntity legacy = legacyStore.update(idOrKey, dto, scope);
            if (mode.writesNewTable()) dualWriteCoordinator.mirror(legacy);
        } else {
            updateNew(idOrKey, dto, scope);
        }
        RecordItemDto updated = queryService.getRecordById(idOrKey, userRole);
        recordAudit(username, "GREEN_TREE_UPDATE", before.getRecordKey(), serialize(before), serialize(updated), ip,
                "Cập nhật cây xanh");
        refreshDatasetCount();
        return updated;
    }

    @Transactional
    public void deleteRecord(String idOrKey, Integer version, String reason,
                             String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", GreenTreeQueryService.DATASET_KEY, "DELETE", userRole);
        RecordItemDto before = queryService.getRecordById(idOrKey, userRole);
        GreenTreeStorageMode mode = storageProperties.getMode();
        GreenTreeAccessService.AccessScope scope = accessService.currentScope(userRole);
        if (mode.writesLegacyTable()) {
            AssetRecordEntity legacy = legacyStore.softDelete(idOrKey, version, username, scope);
            if (mode.writesNewTable()) dualWriteCoordinator.mirror(legacy);
        } else {
            softDeleteNew(idOrKey, version, username, scope);
        }
        String notice = GreenTreeAttributeSupport.writeObject(objectMapper, Map.of(
                "is_deleted", true,
                "reason", reason == null || reason.isBlank() ? "Xóa mềm theo yêu cầu" : reason
        ));
        recordAudit(username, "GREEN_TREE_DELETE", before.getRecordKey(), serialize(before), notice, ip,
                reason == null ? "Xóa mềm cây xanh" : reason);
        refreshDatasetCount();
    }

    public ImportPreviewResponseDto previewImport(String format, String content, String userRole) {
        permissionService.checkPermission("ASSET", GreenTreeQueryService.DATASET_KEY, "READ", userRole);
        List<Map<String, Object>> rows = parseRows(format, content);
        List<ImportValidationErrorDto> errors = validateRows(rows);
        int invalidRows = (int) errors.stream().map(ImportValidationErrorDto::rowNumber).distinct().count();
        return new ImportPreviewResponseDto(GreenTreeQueryService.DATASET_KEY, rows.size(),
                rows.size() - invalidRows, invalidRows, rows.stream().limit(10).toList(), errors,
                !rows.isEmpty() && invalidRows == 0);
    }

    public ImportExecutionResponseDto executeImport(ImportExecutionRequestDto request,
                                                    String userRole, String username, String ip) {
        permissionService.checkPermission("IMPORT", GreenTreeQueryService.DATASET_KEY, "CREATE", userRole);
        List<Map<String, Object>> rows = parseRows(request.format(), request.content());
        List<ImportValidationErrorDto> errors = validateRows(rows);
        if (!errors.isEmpty()) {
            return new ImportExecutionResponseDto(GreenTreeQueryService.DATASET_KEY, request.dryRun(), rows.size(),
                    0, 0, rows.size(), false, "Dữ liệu import cây xanh không hợp lệ",
                    errors.stream().limit(20).map(error -> "Dòng " + error.rowNumber() + ": " + error.message()).toList());
        }
        if (request.dryRun()) {
            return new ImportExecutionResponseDto(GreenTreeQueryService.DATASET_KEY, true, rows.size(), rows.size(),
                    0, 0, false, "Dry-run thành công; chưa có dữ liệu nào được ghi", List.of());
        }

        int processed = 0;
        int batchSize = request.batchSize();
        for (int start = 0; start < rows.size(); start += batchSize) {
            List<Map<String, Object>> batch = rows.subList(start, Math.min(rows.size(), start + batchSize));
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            try {
                transaction.executeWithoutResult(status -> {
                    for (Map<String, Object> row : batch) {
                        createInternal(toDto(row), userRole, username, ip, true);
                    }
                });
                processed += batch.size();
            } catch (Exception exception) {
                return new ImportExecutionResponseDto(GreenTreeQueryService.DATASET_KEY, false, processed,
                        processed, 0, batch.size(), true,
                        "Batch import cây xanh đã rollback: " + safeMessage(exception), List.of(safeMessage(exception)));
            }
        }
        refreshDatasetCount();
        recordAudit(username, "GREEN_TREE_IMPORT", GreenTreeQueryService.DATASET_KEY, null,
                "{\"inserted\":" + processed + "}", ip, "Import cây xanh");
        return new ImportExecutionResponseDto(GreenTreeQueryService.DATASET_KEY, false, processed, processed,
                0, 0, false, "Nạp thành công " + processed + " cây xanh", List.of());
    }

    private RecordItemDto createInternal(AssetCreateUpdateDto dto, String userRole, String username,
                                         String ip, boolean audit) {
        validate(dto, true);
        GreenTreeStorageMode mode = storageProperties.getMode();
        GreenTreeAccessService.AccessScope scope = accessService.currentScope(userRole);
        Long id;
        String recordId;
        if (mode.writesLegacyTable()) {
            AssetRecordEntity legacy = legacyStore.create(dto, scope);
            if (mode.writesNewTable()) dualWriteCoordinator.mirror(legacy);
            id = legacy.getId();
            recordId = legacy.getRecordId();
        } else {
            GreenTreeRecordEntity created = createNew(dto, scope);
            id = created.getId();
            recordId = created.getRecordId();
        }
        RecordItemDto result = queryService.getRecordById(String.valueOf(id), userRole);
        if (audit) recordAudit(username, "GREEN_TREE_CREATE", recordId, null, serialize(result), ip,
                "Tạo mới cây xanh");
        return result;
    }

    private GreenTreeRecordEntity createNew(AssetCreateUpdateDto dto, GreenTreeAccessService.AccessScope scope) {
        Map<String, Object> attributes = GreenTreeAttributeSupport.merge(Map.of(), dto.attributes());
        GreenTreeRecordEntity entity = new GreenTreeRecordEntity();
        entity.setRecordId("green_trees_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        entity.setRawRecordId(null);
        entity.setCreatedAt(OffsetDateTime.now());
        applyNew(entity, dto, attributes, scope, true);
        GreenTreeRecordEntity saved = greenTreeRepository.saveAndFlush(entity);
        updateNewGeometry(saved.getId(), attributes);
        return saved;
    }

    private GreenTreeRecordEntity updateNew(String idOrKey, AssetCreateUpdateDto dto,
                                            GreenTreeAccessService.AccessScope scope) {
        GreenTreeRecordEntity entity = findNew(idOrKey);
        requireVisible(entity, scope);
        requireVersion(dto.version(), entity.getVersion(), idOrKey);
        Map<String, Object> attributes = GreenTreeAttributeSupport.merge(
                GreenTreeAttributeSupport.parseObject(objectMapper, entity.getAttributes()), dto.attributes());
        applyNew(entity, dto, attributes, scope, false);
        GreenTreeRecordEntity saved = greenTreeRepository.saveAndFlush(entity);
        if (GreenTreeAttributeSupport.hasCoordinateIntent(dto.attributes())) {
            updateNewGeometry(saved.getId(), attributes);
        }
        return saved;
    }

    private void softDeleteNew(String idOrKey, Integer version, String username,
                               GreenTreeAccessService.AccessScope scope) {
        GreenTreeRecordEntity entity = findNew(idOrKey);
        requireVisible(entity, scope);
        requireVersion(version, entity.getVersion(), idOrKey);
        entity.setIsDeleted(true);
        entity.setDeletedAt(OffsetDateTime.now());
        entity.setDeletedBy(username);
        entity.setUpdatedAt(OffsetDateTime.now());
        greenTreeRepository.saveAndFlush(entity);
    }

    private GreenTreeRecordEntity findNew(String idOrKey) {
        GreenTreeRecordEntity entity = null;
        if (idOrKey.matches("^[0-9]+$")) entity = greenTreeRepository.findById(Long.parseLong(idOrKey)).orElse(null);
        if (entity == null) entity = greenTreeRepository.findByRecordId(idOrKey.trim()).orElse(null);
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            throw new ResourceNotFoundException("Không tìm thấy cây xanh: " + idOrKey);
        }
        return entity;
    }

    private void applyNew(GreenTreeRecordEntity entity, AssetCreateUpdateDto dto,
                          Map<String, Object> attributes, GreenTreeAccessService.AccessScope scope, boolean create) {
        entity.setName(dto.name().trim());
        if (create || dto.routeCode() != null) entity.setRouteCode(dto.routeCode());
        if (create || dto.routeName() != null) entity.setRouteName(dto.routeName());
        if (create || dto.kmFrom() != null) entity.setKmFrom(dto.kmFrom());
        if (create || dto.kmTo() != null) entity.setKmTo(dto.kmTo());
        if (create || dto.lytrinh() != null) entity.setLytrinh(dto.lytrinh());
        if (create || dto.provinceId() != null) entity.setProvinceId(dto.provinceId());
        if (create || dto.provinceName() != null) entity.setProvinceName(dto.provinceName());
        if (create || dto.districtName() != null) entity.setDistrictName(dto.districtName());
        if (create || dto.townName() != null) entity.setTownName(dto.townName());
        if (create || dto.roadClass() != null) entity.setRoadClass(dto.roadClass());
        if (create || dto.roadType() != null) entity.setRoadType(dto.roadType());
        if (create || dto.organizationId() != null) entity.setOrganizationId(
                dto.organizationId() == null ? "moc_dbvn" : dto.organizationId());
        String branch = scopedBranch(dto.branchId(), scope);
        if (create || branch != null) entity.setBranchId(branch);
        if (create || dto.branchName() != null) entity.setBranchName(dto.branchName());
        if (create || dto.managementAgency() != null) entity.setManagementAgency(dto.managementAgency());
        if (create || dto.state() != null) entity.setState(dto.state() == null ? "Approved" : dto.state());
        if (create || dto.stateName() != null) entity.setStateName(dto.stateName() == null ? "Đã duyệt" : dto.stateName());
        if (create || dto.levelState() != null) entity.setLevelState(dto.levelState() == null ? "cdb_vn" : dto.levelState());
        if (create || dto.activeStatus() != null) entity.setActiveStatus(
                dto.activeStatus() == null ? "Đang quản lý" : dto.activeStatus());
        if (create || dto.maintainValue() != null) entity.setMaintainValue(
                dto.maintainValue() == null ? BigDecimal.ZERO : dto.maintainValue());
        if (create || dto.constructionYear() != null) entity.setConstructionYear(dto.constructionYear());
        if (create || dto.parentId() != null) entity.setParentId(dto.parentId());
        entity.setTreeCode(GreenTreeAttributeSupport.text(attributes, Map.of(), "tree_code"));
        entity.setSpeciesName(GreenTreeAttributeSupport.text(attributes, Map.of(), "species_name"));
        entity.setScientificName(GreenTreeAttributeSupport.text(attributes, Map.of(), "scientific_name"));
        entity.setDiameterCm(GreenTreeAttributeSupport.nonNegativeDecimal(attributes, Map.of(), "diameter_cm"));
        entity.setHeightM(GreenTreeAttributeSupport.nonNegativeDecimal(attributes, Map.of(), "height_m"));
        entity.setCanopyDiameterM(GreenTreeAttributeSupport.nonNegativeDecimal(attributes, Map.of(), "canopy_diameter_m"));
        entity.setPlantingYear(GreenTreeAttributeSupport.integer(attributes, Map.of(), "planting_year"));
        entity.setHealthStatus(firstNonBlank(
                GreenTreeAttributeSupport.text(attributes, Map.of(), "health_status"),
                GreenTreeAttributeSupport.text(attributes, Map.of(), "condition")));
        entity.setRiskLevel(GreenTreeAttributeSupport.text(attributes, Map.of(), "risk_level"));
        entity.setNotes(GreenTreeAttributeSupport.text(attributes, Map.of(), "notes"));
        entity.setAttributes(GreenTreeAttributeSupport.writeObject(objectMapper, attributes));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(OffsetDateTime.now());
    }

    private void updateNewGeometry(Long id, Map<String, Object> attributes) {
        GreenTreeAttributeSupport.Coordinates coordinates = GreenTreeAttributeSupport.coordinates(attributes);
        if (coordinates.present()) greenTreeRepository.updateGeometry(id, coordinates.longitude(), coordinates.latitude());
        else greenTreeRepository.clearGeometry(id);
    }

    private void validate(AssetCreateUpdateDto dto, boolean create) {
        if (dto == null || dto.name() == null || dto.name().isBlank()) {
            throw new BadRequestException("Tên cây xanh không được để trống");
        }
        if (dto.kmFrom() != null && dto.kmTo() != null && dto.kmFrom().compareTo(dto.kmTo()) > 0) {
            throw new BadRequestException("Lý trình đầu không được lớn hơn lý trình cuối");
        }
        Map<String, Object> attributes = dto.attributes() == null ? Map.of() : dto.attributes();
        for (String field : List.of("diameter_cm", "height_m", "canopy_diameter_m")) {
            BigDecimal value = GreenTreeAttributeSupport.decimal(attributes.get(field));
            if (value != null && value.signum() < 0) throw new BadRequestException(field + " không được âm");
        }
        if (create || attributes.containsKey("longitude") || attributes.containsKey("latitude")) {
            GreenTreeAttributeSupport.coordinates(attributes);
        }
        if (!create && dto.version() == null) throw new ConflictException("Bắt buộc cung cấp version khi cập nhật cây xanh");
    }

    private String scopedBranch(String requested, GreenTreeAccessService.AccessScope scope) {
        if (scope.isAdmin()) return requested;
        if (!scope.hasBranch()) throw new AccessDeniedException("Tài khoản chưa được gán phạm vi đơn vị");
        if (requested != null && !scope.branchId().equals(requested)) {
            throw new AccessDeniedException("Không được ghi cây xanh ngoài phạm vi đơn vị");
        }
        return scope.branchId();
    }

    private void requireVisible(GreenTreeRecordEntity entity, GreenTreeAccessService.AccessScope scope) {
        if (scope.isAdmin()) return;
        if (entity.getRawRecordId() == null && scope.hasBranch() && scope.branchId().equals(entity.getBranchId())) return;
        if (entity.getRawRecordId() != null && scope.userId() != null && scope.userId() > 0) {
            // The read service already enforces vroad_record_visible; a successful scoped lookup is sufficient here.
            queryService.getRecordById(String.valueOf(entity.getId()), scope.role());
            return;
        }
        throw new ResourceNotFoundException("Không tìm thấy cây xanh trong phạm vi được cấp");
    }

    private void requireVersion(Integer supplied, Integer current, String idOrKey) {
        if (supplied == null || !supplied.equals(current)) {
            throw new ConflictException("Xung đột phiên bản cây xanh '" + idOrKey + "': hiện tại "
                    + current + ", yêu cầu " + supplied);
        }
    }

    private void refreshDatasetCount() {
        datasetRegistryRepository.findByDatasetKey(GreenTreeQueryService.DATASET_KEY).ifPresent(dataset -> {
            long count = queryService.countAllActive();
            dataset.setTotalRecords(Math.toIntExact(count));
            dataset.setUpdatedAt(OffsetDateTime.now());
            datasetRegistryRepository.save(dataset);
        });
    }

    private List<ImportValidationErrorDto> validateRows(List<Map<String, Object>> rows) {
        List<ImportValidationErrorDto> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            int rowNumber = index + 1;
            if (row.get("name") == null || String.valueOf(row.get("name")).isBlank()) {
                errors.add(new ImportValidationErrorDto(rowNumber, "name", "Tên cây không được để trống"));
            }
            for (String field : List.of("diameter_cm", "height_m", "canopy_diameter_m")) {
                BigDecimal value = GreenTreeAttributeSupport.decimal(row.get(field));
                if (value != null && value.signum() < 0) {
                    errors.add(new ImportValidationErrorDto(rowNumber, field, field + " không được âm"));
                }
            }
            try {
                if (row.containsKey("longitude") || row.containsKey("latitude")) {
                    GreenTreeAttributeSupport.coordinates(row);
                }
            } catch (BadRequestException exception) {
                errors.add(new ImportValidationErrorDto(rowNumber, "coordinates", exception.getMessage()));
            }
        }
        return errors;
    }

    private AssetCreateUpdateDto toDto(Map<String, Object> row) {
        return new AssetCreateUpdateDto(
                string(row, "name"), string(row, "route_code"), string(row, "route_name"),
                GreenTreeAttributeSupport.decimal(row.get("km_from")), GreenTreeAttributeSupport.decimal(row.get("km_to")),
                string(row, "lytrinh"), string(row, "province_id"), string(row, "province_name"),
                string(row, "district_name"), string(row, "town_name"),
                string(row, "road_class"), string(row, "road_type"),
                string(row, "organization_id"), string(row, "branch_id"), string(row, "branch_name"),
                string(row, "management_agency"), string(row, "state"), string(row, "state_name"),
                string(row, "level_state"), string(row, "active_status"),
                GreenTreeAttributeSupport.decimal(row.get("maintain_value")),
                GreenTreeAttributeSupport.integer(row, Map.of(), "construction_year"),
                string(row, "parent_id"),
                new LinkedHashMap<>(row), null
        );
    }

    private List<Map<String, Object>> parseRows(String format, String content) {
        if (content == null || content.isBlank()) throw new BadRequestException("Nội dung import không được để trống");
        if ("JSON".equalsIgnoreCase(format)) {
            try {
                JsonNode root = objectMapper.readTree(content);
                List<Map<String, Object>> rows = new ArrayList<>();
                if (root.isArray()) {
                    for (JsonNode item : root) rows.add(objectMapper.convertValue(item, new TypeReference<>() {}));
                } else if (root.isObject()) {
                    rows.add(objectMapper.convertValue(root, new TypeReference<>() {}));
                } else throw new BadRequestException("JSON import phải là object hoặc mảng object");
                return rows;
            } catch (BadRequestException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BadRequestException("JSON import không hợp lệ: " + exception.getMessage());
            }
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) return List.of();
            String[] headers = parseCsvLine(headerLine);
            List<Map<String, Object>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.length; index++) {
                    row.put(headers[index].trim().toLowerCase(), index < values.length ? values[index].trim() : "");
                }
                rows.add(row);
            }
            return rows;
        } catch (Exception exception) {
            throw new BadRequestException("CSV import không hợp lệ: " + exception.getMessage());
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else value.append(character);
        }
        values.add(value.toString());
        return values.toArray(String[]::new);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private void recordAudit(String username, String action, String entityId, String oldValues,
                             String newValues, String ip, String description) {
        auditLogRepository.save(new AuditLogEntity(null, username == null ? "system" : username, action,
                "green_tree_record", entityId, oldValues, newValues, ip == null ? "127.0.0.1" : ip, description));
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể ghi audit cây xanh", exception);
        }
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}

