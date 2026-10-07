package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.entity.RawDatasetRecordEntity;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ConflictException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.mapper.AssetMapper;
import vn.gov.drvn.kcht.repository.AssetRecordRepository;
import vn.gov.drvn.kcht.repository.AuditLogRepository;
import vn.gov.drvn.kcht.repository.DatasetRegistryRepository;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AssetCrudService {

    private static final Logger log = LoggerFactory.getLogger(AssetCrudService.class);

    private final AssetRecordRepository assetRecordRepository;
    private final RawDatasetRecordRepository rawDatasetRecordRepository;
    private final DatasetRegistryRepository datasetRegistryRepository;
    private final AuditLogRepository auditLogRepository;
    private final DatasetPermissionService permissionService;
    private final AssetMapper assetMapper;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public AssetCrudService(AssetRecordRepository assetRecordRepository,
                            RawDatasetRecordRepository rawDatasetRecordRepository,
                            DatasetRegistryRepository datasetRegistryRepository,
                            AuditLogRepository auditLogRepository,
                            DatasetPermissionService permissionService,
                            AssetMapper assetMapper,
                            ObjectMapper objectMapper,
                            PlatformTransactionManager transactionManager) {
        this.assetRecordRepository = assetRecordRepository;
        this.rawDatasetRecordRepository = rawDatasetRecordRepository;
        this.datasetRegistryRepository = datasetRegistryRepository;
        this.auditLogRepository = auditLogRepository;
        this.permissionService = permissionService;
        this.assetMapper = assetMapper;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    /**
     * Thêm mới một bản ghi tài sản hạ tầng đường bộ vào tầng Curated (asset_record).
     * Bảng raw_dataset_record tuyệt đối không bị chỉnh sửa.
     */
    @Transactional
    public RecordItemDto createRecord(String datasetKey, AssetCreateUpdateDto dto, String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", datasetKey, "CREATE", userRole);
        validateBusinessRules(dto);

        String recordKey = datasetKey + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        AssetRecordEntity entity = new AssetRecordEntity();
        entity.setRecordId(recordKey);
        entity.setDatasetCode(datasetKey);
        entity.setAssetType(resolveAssetType(datasetKey));
        entity.setName(dto.name().trim());
        entity.setRouteCode(dto.routeCode());
        entity.setRouteName(dto.routeName());
        entity.setKmFrom(dto.kmFrom());
        entity.setKmTo(dto.kmTo());
        entity.setLytrinh(dto.lytrinh() != null ? dto.lytrinh() : formatLytrinh(dto.kmFrom(), dto.kmTo()));
        entity.setProvinceId(dto.provinceId());
        entity.setProvinceName(dto.provinceName());
        entity.setDistrictName(dto.districtName());
        entity.setTownName(dto.townName());
        entity.setRoadClass(dto.roadClass());
        entity.setRoadType(dto.roadType() != null ? dto.roadType() : "Quốc lộ");
        entity.setOrganizationId(dto.organizationId() != null ? dto.organizationId() : "moc_dbvn");
        entity.setBranchId(dto.branchId());
        entity.setBranchName(dto.branchName());
        entity.setManagementAgency(dto.managementAgency());
        entity.setState(dto.state() != null ? dto.state() : "Approved");
        entity.setStateName(dto.stateName() != null ? dto.stateName() : "Đã duyệt");
        entity.setLevelState(dto.levelState() != null ? dto.levelState() : "cdb_vn");
        entity.setActiveStatus(dto.activeStatus() != null ? dto.activeStatus() : "Đang khai thác");
        entity.setMaintainValue(dto.maintainValue() != null ? dto.maintainValue() : BigDecimal.ZERO);
        entity.setConstructionYear(dto.constructionYear());
        entity.setParentId(dto.parentId());

        // Custom attributes JSONB
        if (dto.attributes() != null && !dto.attributes().isEmpty()) {
            try {
                entity.setAttributes(objectMapper.writeValueAsString(dto.attributes()));
            } catch (Exception e) {
                log.warn("Lỗi chuyển đổi attributes sang JSON: {}", e.getMessage());
                entity.setAttributes("{}");
            }
        } else {
            entity.setAttributes("{}");
        }

        entity.setIsDeleted(false);
        entity.setVersion(1);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setRawRecordId(null); // Bản ghi curated tạo thủ công

        AssetRecordEntity saved = assetRecordRepository.saveAndFlush(entity);

        // Ghi nhật ký kiểm toán (Audit Log)
        recordAuditLog(username, "ASSET_CREATE", "asset_record", saved.getRecordId(), null,
                serializeEntity(saved), ip, "Tạo mới tài sản trên dataset " + datasetKey);

        return assetMapper.toRecordItemDto(saved);
    }

    /**
     * Cập nhật bản ghi tài sản có kiểm tra Khóa lạc quan (Optimistic Locking).
     * Chỉ thao tác trên bảng curated asset_record, giữ liên kết raw và không sửa bảng raw.
     */
    @Transactional
    public RecordItemDto updateRecord(String datasetKey, String idOrKey, AssetCreateUpdateDto dto,
                                      String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", datasetKey, "UPDATE", userRole);
        validateBusinessRules(dto);

        AssetRecordEntity entity = findOrPromoteAsset(datasetKey, idOrKey);

        // Kiểm tra Khóa lạc quan (Optimistic Locking)
        if (dto.version() != null && !dto.version().equals(entity.getVersion())) {
            throw new ConflictException(String.format(
                    "Xung đột phiên bản: Bản ghi '%s' đã bị cập nhật bởi người dùng khác (phiên bản hiện tại trên CSDL là %d, phiên bản gửi lên là %d). Vui lòng làm mới dữ liệu và thử lại.",
                    idOrKey, entity.getVersion(), dto.version()
            ));
        }

        String oldValues = serializeEntity(entity);

        // Cập nhật các trường được phép sửa (Tuyệt đối không sửa geometry hoặc trường hệ thống)
        entity.setName(dto.name().trim());
        if (dto.routeCode() != null) entity.setRouteCode(dto.routeCode());
        if (dto.routeName() != null) entity.setRouteName(dto.routeName());
        if (dto.kmFrom() != null) entity.setKmFrom(dto.kmFrom());
        if (dto.kmTo() != null) entity.setKmTo(dto.kmTo());
        if (dto.lytrinh() != null) {
            entity.setLytrinh(dto.lytrinh());
        } else if (dto.kmFrom() != null) {
            entity.setLytrinh(formatLytrinh(dto.kmFrom(), dto.kmTo()));
        }
        if (dto.provinceId() != null) entity.setProvinceId(dto.provinceId());
        if (dto.provinceName() != null) entity.setProvinceName(dto.provinceName());
        if (dto.districtName() != null) entity.setDistrictName(dto.districtName());
        if (dto.townName() != null) entity.setTownName(dto.townName());
        if (dto.roadClass() != null) entity.setRoadClass(dto.roadClass());
        if (dto.roadType() != null) entity.setRoadType(dto.roadType());
        if (dto.organizationId() != null) entity.setOrganizationId(dto.organizationId());
        if (dto.branchId() != null) entity.setBranchId(dto.branchId());
        if (dto.branchName() != null) entity.setBranchName(dto.branchName());
        if (dto.managementAgency() != null) entity.setManagementAgency(dto.managementAgency());
        if (dto.state() != null) entity.setState(dto.state());
        if (dto.stateName() != null) entity.setStateName(dto.stateName());
        if (dto.levelState() != null) entity.setLevelState(dto.levelState());
        if (dto.activeStatus() != null) entity.setActiveStatus(dto.activeStatus());
        if (dto.maintainValue() != null) entity.setMaintainValue(dto.maintainValue());
        if (dto.constructionYear() != null) entity.setConstructionYear(dto.constructionYear());
        if (dto.parentId() != null) entity.setParentId(dto.parentId());

        // Cập nhật attributes JSONB
        if (dto.attributes() != null) {
            try {
                entity.setAttributes(objectMapper.writeValueAsString(dto.attributes()));
            } catch (Exception e) {
                log.warn("Lỗi serialize attributes JSONB: {}", e.getMessage());
            }
        }

        entity.setUpdatedAt(OffsetDateTime.now());
        AssetRecordEntity saved = assetRecordRepository.saveAndFlush(entity);

        // Ghi Audit Log
        recordAuditLog(username, "ASSET_UPDATE", "asset_record", saved.getRecordId(),
                oldValues, serializeEntity(saved), ip, "Cập nhật tài sản trên dataset " + datasetKey);

        return assetMapper.toRecordItemDto(saved);
    }

    /**
     * Xóa mềm bản ghi tài sản (Soft Delete: is_deleted = true) kèm kiểm tra Khóa lạc quan và ghi Audit Log.
     * Bảng raw_dataset_record tuyệt đối không bị xóa.
     */
    @Transactional
    public void deleteRecord(String datasetKey, String idOrKey, Integer version, String reason,
                             String userRole, String username, String ip) {
        permissionService.checkPermission("ASSET", datasetKey, "DELETE", userRole);

        AssetRecordEntity entity = findOrPromoteAsset(datasetKey, idOrKey);

        // Kiểm tra Khóa lạc quan
        if (version != null && !version.equals(entity.getVersion())) {
            throw new ConflictException(String.format(
                    "Xung đột phiên bản khi xóa: Bản ghi '%s' đã bị cập nhật bởi người dùng khác (phiên bản hiện tại là %d, phiên bản gửi lên là %d). Vui lòng làm mới dữ liệu.",
                    idOrKey, entity.getVersion(), version
            ));
        }

        String oldValues = serializeEntity(entity);

        // Thực hiện xóa mềm
        entity.setIsDeleted(true);
        entity.setDeletedAt(OffsetDateTime.now());
        entity.setDeletedBy(username != null ? username : "system");
        entity.setUpdatedAt(OffsetDateTime.now());
        AssetRecordEntity saved = assetRecordRepository.saveAndFlush(entity);

        // Ghi Audit Log
        String deleteNotice = "{\"is_deleted\": true, \"reason\": \"" + (reason != null ? reason : "Xóa mềm theo yêu cầu") + "\"}";
        recordAuditLog(username, "ASSET_DELETE", "asset_record", saved.getRecordId(),
                oldValues, deleteNotice, ip, reason != null ? reason : "Xóa mềm tài sản " + datasetKey);
    }

    /**
     * Xem trước (Preview) và kiểm tra dữ liệu nạp (Dry-run validation) từ CSV hoặc JSON.
     * Không ghi bất kỳ dữ liệu nào vào CSDL.
     */
    public ImportPreviewResponseDto previewImport(String datasetKey, String format, String content, String userRole) {
        permissionService.checkPermission("ASSET", datasetKey, "READ", userRole);

        if (content == null || content.isBlank()) {
            throw new BadRequestException("Nội dung tệp nạp không được để trống");
        }

        List<Map<String, Object>> parsedRows = parseRows(format, content);
        List<ImportValidationErrorDto> errors = new ArrayList<>();
        List<Map<String, Object>> previewRows = new ArrayList<>();

        for (int i = 0; i < parsedRows.size(); i++) {
            Map<String, Object> row = parsedRows.get(i);
            int rowNumber = i + 1;

            // Validate row
            validateImportRow(rowNumber, row, errors);

            if (previewRows.size() < 10) {
                previewRows.add(row);
            }
        }

        int total = parsedRows.size();
        int errCount = (int) errors.stream().map(ImportValidationErrorDto::rowNumber).distinct().count();
        int validCount = Math.max(0, total - errCount);
        boolean canProceed = (errCount == 0 && total > 0);

        return new ImportPreviewResponseDto(datasetKey, total, validCount, errCount, previewRows, errors, canProceed);
    }

    /**
     * Thực thi nạp dữ liệu hàng loạt theo lô (Transactional Batch Import).
     * Có cơ chế dry-run, checkpoint và rollback toàn bộ lô khi xảy ra lỗi.
     */
    public ImportExecutionResponseDto executeImport(String datasetKey, ImportExecutionRequestDto req,
                                                    String userRole, String username, String ip) {
        // Yêu cầu quyền IMPORT:CREATE (ROLE_ADMIN hoặc ROLE_MANAGER)
        permissionService.checkPermission("IMPORT", datasetKey, "CREATE", userRole);

        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestException("Nội dung nạp không được để trống");
        }

        List<Map<String, Object>> parsedRows = parseRows(req.format(), req.content());
        if (parsedRows.isEmpty()) {
            throw new BadRequestException("Không tìm thấy dòng dữ liệu nào hợp lệ trong tệp nạp");
        }

        List<ImportValidationErrorDto> validationErrors = new ArrayList<>();
        for (int i = 0; i < parsedRows.size(); i++) {
            validateImportRow(i + 1, parsedRows.get(i), validationErrors);
        }

        if (!validationErrors.isEmpty()) {
            List<String> errMsgs = validationErrors.stream()
                    .limit(10)
                    .map(e -> String.format("Dòng %d: %s (%s)", e.rowNumber(), e.message(), e.field()))
                    .toList();
            return new ImportExecutionResponseDto(
                    datasetKey,
                    req.dryRun(),
                    parsedRows.size(),
                    0, 0,
                    parsedRows.size(),
                    false,
                    "Tệp dữ liệu vi phạm " + validationErrors.size() + " lỗi kiểm tra. Tiến trình nạp đã dừng lại an toàn.",
                    errMsgs
            );
        }

        // Chế độ Dry-run: Không ghi vào CSDL
        if (req.dryRun()) {
            return new ImportExecutionResponseDto(
                    datasetKey,
                    true,
                    parsedRows.size(),
                    parsedRows.size(),
                    0, 0,
                    false,
                    String.format("Chạy thử (Dry-run) thành công: %d/%d dòng hợp lệ sẵn sàng nạp. Chưa có dữ liệu nào được ghi vào CSDL.",
                            parsedRows.size(), parsedRows.size()),
                    List.of()
            );
        }

        // Thực thi nạp chính thức theo từng batch giao dịch
        int batchSize = req.batchSize() > 0 ? req.batchSize() : 500;
        int totalProcessed = 0;
        int insertedCount = 0;
        int updatedCount = 0;

        List<List<Map<String, Object>>> batches = splitIntoBatches(parsedRows, batchSize);

        for (int b = 0; b < batches.size(); b++) {
            List<Map<String, Object>> batch = batches.get(b);
            int batchIndex = b + 1;

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionStatus status = transactionManager.getTransaction(def);

            try {
                for (Map<String, Object> row : batch) {
                    processImportRow(datasetKey, row);
                    insertedCount++;
                    totalProcessed++;
                }
                transactionManager.commit(status);
            } catch (Exception ex) {
                transactionManager.rollback(status);
                log.error("Lỗi khi xử lý batch #{}/{} của dataset {}: {}. Tiến trình đã được rollback an toàn.",
                        batchIndex, batches.size(), datasetKey, ex.getMessage(), ex);

                return new ImportExecutionResponseDto(
                        datasetKey,
                        false,
                        totalProcessed,
                        insertedCount,
                        updatedCount,
                        batch.size(),
                        true, // Cờ rollback bật
                        String.format("Lỗi vi phạm ràng buộc tại lô #%d: %s. Toàn bộ %d bản ghi của lô đã được Rollback an toàn.",
                                batchIndex, ex.getMessage(), batch.size()),
                        List.of(ex.getMessage() != null ? ex.getMessage() : "Lỗi hệ thống trong transaction")
                );
            }
        }

        // Ghi Audit Log phiên nạp
        recordAuditLog(username, "IMPORT_" + req.format().toUpperCase(), "asset_record", datasetKey,
                null,
                String.format("{\"total\": %d, \"inserted\": %d, \"updated\": %d, \"batchSize\": %d}",
                        totalProcessed, insertedCount, updatedCount, batchSize),
                ip, "Nạp dữ liệu hàng loạt thành công cho dataset " + datasetKey);

        return new ImportExecutionResponseDto(
                datasetKey,
                false,
                totalProcessed,
                insertedCount,
                updatedCount,
                0,
                false,
                String.format("Nạp dữ liệu thành công hoàn toàn: Đã xử lý %d bản ghi qua %d lô an toàn.", totalProcessed, batches.size()),
                List.of()
        );
    }

    private void processImportRow(String datasetKey, Map<String, Object> row) {
        String name = String.valueOf(row.getOrDefault("name", ""));
        String routeCode = row.get("route_code") != null ? String.valueOf(row.get("route_code")) : null;
        String routeName = row.get("route_name") != null ? String.valueOf(row.get("route_name")) : null;
        BigDecimal kmFrom = parseBigDecimal(row.get("km_from"));
        BigDecimal kmTo = parseBigDecimal(row.get("km_to"));
        String branchId = row.get("branch_id") != null ? String.valueOf(row.get("branch_id")) : "kqldb_1";

        String recordKey = datasetKey + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        AssetRecordEntity entity = new AssetRecordEntity();
        entity.setRecordId(recordKey);
        entity.setDatasetCode(datasetKey);
        entity.setAssetType(resolveAssetType(datasetKey));
        entity.setName(name.trim());
        entity.setRouteCode(routeCode);
        entity.setRouteName(routeName);
        entity.setKmFrom(kmFrom);
        entity.setKmTo(kmTo);
        entity.setLytrinh(row.get("lytrinh") != null ? String.valueOf(row.get("lytrinh")) : formatLytrinh(kmFrom, kmTo));
        entity.setProvinceId(row.get("province_id") != null ? String.valueOf(row.get("province_id")) : null);
        entity.setProvinceName(row.get("province_name") != null ? String.valueOf(row.get("province_name")) : null);
        entity.setBranchId(branchId);
        entity.setState("Approved");
        entity.setStateName("Đã duyệt");
        entity.setActiveStatus("Đang khai thác");
        entity.setMaintainValue(parseBigDecimal(row.get("maintain_value")));
        entity.setConstructionYear(parseInteger(row.get("construction_year")));
        entity.setIsDeleted(false);
        entity.setVersion(1);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());

        try {
            entity.setAttributes(objectMapper.writeValueAsString(row));
        } catch (Exception e) {
            entity.setAttributes("{}");
        }

        assetRecordRepository.save(entity);
    }

    private AssetRecordEntity findOrPromoteAsset(String datasetKey, String idOrKey) {
        if (idOrKey == null || idOrKey.isBlank()) {
            throw new BadRequestException("Mã định danh bản ghi không được để trống");
        }

        // 1. Tìm trong bảng curated asset_record trước
        Optional<AssetRecordEntity> entityOpt = Optional.empty();
        if (idOrKey.matches("^[0-9]+$")) {
            entityOpt = assetRecordRepository.findById(Long.parseLong(idOrKey))
                    .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()) && datasetKey.equalsIgnoreCase(e.getDatasetCode()));
        }
        if (entityOpt.isEmpty()) {
            entityOpt = assetRecordRepository.findByRecordIdAndIsDeletedFalse(idOrKey.trim())
                    .filter(e -> datasetKey.equalsIgnoreCase(e.getDatasetCode()));
        }

        if (entityOpt.isPresent()) {
            return entityOpt.get();
        }

        // 2. Nếu chưa có trong curated, tìm trong raw_dataset_record để promote lên curated
        // Bảng raw_dataset_record vẫn giữ nguyên 100% bất biến!
        Optional<RawDatasetRecordEntity> rawOpt = rawDatasetRecordRepository.findByDatasetKeyAndRecordKey(datasetKey, idOrKey.trim());
        if (rawOpt.isEmpty() && idOrKey.matches("^[0-9]+$")) {
            rawOpt = rawDatasetRecordRepository.findById(Long.parseLong(idOrKey.trim()))
                    .filter(r -> datasetKey.equalsIgnoreCase(r.getDatasetKey()));
        }

        if (rawOpt.isPresent()) {
            RawDatasetRecordEntity raw = rawOpt.get();
            log.info("Promote bản ghi raw [{}] dataset [{}] lên tầng curated asset_record", raw.getRecordKey(), datasetKey);

            AssetRecordEntity promoted = new AssetRecordEntity();
            promoted.setRawRecordId(raw.getId());
            promoted.setRecordId(raw.getRecordKey());
            promoted.setDatasetCode(datasetKey);
            promoted.setAssetType(resolveAssetType(datasetKey));
            promoted.setName(raw.getRecordKey());
            promoted.setAttributes(raw.getRawPayload() != null ? raw.getRawPayload() : "{}");

            // Trích xuất các trường cơ bản từ payload JSON nếu có
            try {
                JsonNode node = objectMapper.readTree(promoted.getAttributes());
                if (node.hasNonNull("name")) promoted.setName(node.get("name").asText());
                if (node.hasNonNull("fielddisplay")) promoted.setName(node.get("fielddisplay").asText());
                if (node.hasNonNull("route_code")) promoted.setRouteCode(node.get("route_code").asText());
                if (node.hasNonNull("route_name")) promoted.setRouteName(node.get("route_name").asText());
                if (node.hasNonNull("branch_id")) promoted.setBranchId(node.get("branch_id").asText());
                if (node.hasNonNull("province_name")) promoted.setProvinceName(node.get("province_name").asText());
                if (node.hasNonNull("state")) promoted.setState(node.get("state").asText());
                if (node.hasNonNull("state_name")) promoted.setStateName(node.get("state_name").asText());
            } catch (Exception e) {
                log.warn("Không thể đọc payload raw khi promote bản ghi {}: {}", raw.getRecordKey(), e.getMessage());
            }

            promoted.setIsDeleted(false);
            promoted.setVersion(1);
            promoted.setCreatedAt(raw.getImportedAt() != null ? raw.getImportedAt() : OffsetDateTime.now());
            promoted.setUpdatedAt(OffsetDateTime.now());

            return assetRecordRepository.saveAndFlush(promoted);
        }

        throw new ResourceNotFoundException(String.format("Không tìm thấy bản ghi '%s' trong tập dữ liệu '%s'", idOrKey, datasetKey));
    }

    private void validateBusinessRules(AssetCreateUpdateDto dto) {
        if (dto.name() == null || dto.name().isBlank()) {
            throw new BadRequestException("Tên công trình/tài sản không được để trống");
        }
        if (dto.name().length() > 500) {
            throw new BadRequestException("Tên công trình không được vượt quá 500 ký tự");
        }
        if (dto.kmFrom() != null && dto.kmTo() != null) {
            if (dto.kmFrom().compareTo(dto.kmTo()) > 0) {
                throw new BadRequestException(String.format(
                        "Lý trình từ (Km %s) không được lớn hơn lý trình đến (Km %s)", dto.kmFrom(), dto.kmTo()
                ));
            }
        }
    }

    private void validateImportRow(int rowNumber, Map<String, Object> row, List<ImportValidationErrorDto> errors) {
        Object nameObj = row.get("name");
        if (nameObj == null || String.valueOf(nameObj).isBlank()) {
            errors.add(new ImportValidationErrorDto(rowNumber, "name", "Tên công trình/tài sản không được để trống"));
        } else if (String.valueOf(nameObj).length() > 500) {
            errors.add(new ImportValidationErrorDto(rowNumber, "name", "Tên công trình không được vượt quá 500 ký tự"));
        }

        BigDecimal kmFrom = parseBigDecimal(row.get("km_from"));
        BigDecimal kmTo = parseBigDecimal(row.get("km_to"));
        if (kmFrom != null && kmTo != null && kmFrom.compareTo(kmTo) > 0) {
            errors.add(new ImportValidationErrorDto(rowNumber, "km_from", "Lý trình từ không được lớn hơn lý trình đến"));
        }
    }

    private List<Map<String, Object>> parseRows(String format, String content) {
        List<Map<String, Object>> result = new ArrayList<>();
        if ("JSON".equalsIgnoreCase(format)) {
            try {
                JsonNode root = objectMapper.readTree(content);
                if (root.isArray()) {
                    for (JsonNode item : root) {
                        Map<String, Object> map = objectMapper.convertValue(item, new TypeReference<>() {});
                        result.add(map);
                    }
                } else if (root.isObject()) {
                    Map<String, Object> map = objectMapper.convertValue(root, new TypeReference<>() {});
                    result.add(map);
                }
            } catch (Exception e) {
                throw new BadRequestException("Lỗi định dạng JSON không hợp lệ: " + e.getMessage());
            }
        } else {
            // CSV parsing
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    return result;
                }
                String[] headers = parseCsvLine(headerLine);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] values = parseCsvLine(line);
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        String key = headers[i].trim().toLowerCase();
                        String val = i < values.length ? values[i].trim() : "";
                        row.put(key, val);
                    }
                    result.add(row);
                }
            } catch (Exception e) {
                throw new BadRequestException("Lỗi phân tích tệp CSV: " + e.getMessage());
            }
        }
        return result;
    }

    private String[] parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list.toArray(new String[0]);
    }

    private <T> List<List<T>> splitIntoBatches(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    private String resolveAssetType(String datasetKey) {
        if (datasetKey == null) return "GENERAL";
        String lower = datasetKey.toLowerCase();
        if (lower.contains("bridge")) return "BRIDGE";
        if (lower.contains("sign")) return "ROAD_SIGN";
        if (lower.contains("road")) return "ROAD";
        if (lower.contains("drain")) return "DRAINAGE";
        if (lower.contains("guard")) return "GUARDRAIL";
        return "GENERAL";
    }

    private String formatLytrinh(BigDecimal kmFrom, BigDecimal kmTo) {
        if (kmFrom != null && kmTo != null) {
            return String.format("Km %s - Km %s", kmFrom.toPlainString(), kmTo.toPlainString());
        } else if (kmFrom != null) {
            return String.format("Km %s", kmFrom.toPlainString());
        }
        return "—";
    }

    private BigDecimal parseBigDecimal(Object obj) {
        if (obj == null) return null;
        try {
            return new BigDecimal(String.valueOf(obj).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        try {
            return Integer.parseInt(String.valueOf(obj).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeEntity(AssetRecordEntity entity) {
        try {
            return objectMapper.writeValueAsString(assetMapper.toRecordItemDto(entity));
        } catch (Exception e) {
            return "{}";
        }
    }

    private void recordAuditLog(String username, String action, String resource, String entityId,
                                String oldValues, String newValues, String ip, String description) {
        try {
            AuditLogEntity audit = new AuditLogEntity(
                    null,
                    username != null ? username : "system",
                    action,
                    resource,
                    entityId,
                    oldValues,
                    newValues,
                    ip != null ? ip : "127.0.0.1",
                    description
            );
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Không thể ghi nhận audit_log cho {}: {}", entityId, e.getMessage());
        }
    }
}
