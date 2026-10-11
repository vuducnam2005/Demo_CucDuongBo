package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.dto.AssetCreateUpdateDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.exception.AccessDeniedException;
import vn.gov.drvn.kcht.exception.ConflictException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.repository.AssetGeometryRepository;
import vn.gov.drvn.kcht.repository.AssetRecordRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class LegacyGreenTreeStore {

    private final AssetRecordRepository assetRepository;
    private final AssetGeometryRepository geometryRepository;
    private final JdbcTemplate database;
    private final ObjectMapper objectMapper;

    public LegacyGreenTreeStore(AssetRecordRepository assetRepository,
                                AssetGeometryRepository geometryRepository,
                                JdbcTemplate database,
                                ObjectMapper objectMapper) {
        this.assetRepository = assetRepository;
        this.geometryRepository = geometryRepository;
        this.database = database;
        this.objectMapper = objectMapper;
    }

    public AssetRecordEntity create(AssetCreateUpdateDto dto, GreenTreeAccessService.AccessScope scope) {
        AssetRecordEntity entity = new AssetRecordEntity();
        entity.setRecordId("green_trees_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        entity.setDatasetCode(GreenTreeQueryService.DATASET_KEY);
        entity.setAssetType("TREE");
        entity.setRawRecordId(null);
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setVersion(1);
        Map<String, Object> attributes = GreenTreeAttributeSupport.merge(Map.of(), dto.attributes());
        apply(entity, dto, attributes, scope, true);
        AssetRecordEntity saved = assetRepository.saveAndFlush(entity);
        updateGeometry(saved.getId(), attributes);
        return saved;
    }

    public AssetRecordEntity update(String idOrKey, AssetCreateUpdateDto dto,
                                    GreenTreeAccessService.AccessScope scope) {
        AssetRecordEntity entity = find(idOrKey);
        requireVisible(entity, scope);
        requireVersion(dto.version(), entity.getVersion(), idOrKey);
        Map<String, Object> existing = GreenTreeAttributeSupport.parseObject(objectMapper, entity.getAttributes());
        Map<String, Object> attributes = GreenTreeAttributeSupport.merge(existing, dto.attributes());
        apply(entity, dto, attributes, scope, false);
        AssetRecordEntity saved = assetRepository.saveAndFlush(entity);
        if (GreenTreeAttributeSupport.hasCoordinateIntent(dto.attributes())) {
            updateGeometry(saved.getId(), attributes);
        }
        return saved;
    }

    public AssetRecordEntity softDelete(String idOrKey, Integer version, String username,
                                        GreenTreeAccessService.AccessScope scope) {
        AssetRecordEntity entity = find(idOrKey);
        requireVisible(entity, scope);
        requireVersion(version, entity.getVersion(), idOrKey);
        entity.setIsDeleted(true);
        entity.setDeletedAt(OffsetDateTime.now());
        entity.setDeletedBy(username);
        entity.setUpdatedAt(OffsetDateTime.now());
        return assetRepository.saveAndFlush(entity);
    }

    public AssetRecordEntity find(String idOrKey) {
        Optional<AssetRecordEntity> entity = Optional.empty();
        if (idOrKey != null && idOrKey.matches("^[0-9]+$")) {
            entity = assetRepository.findById(Long.parseLong(idOrKey));
        }
        if (entity.isEmpty() && idOrKey != null) entity = assetRepository.findByRecordId(idOrKey.trim());
        return entity.filter(item -> GreenTreeQueryService.DATASET_KEY.equals(item.getDatasetCode())
                        && "TREE".equals(item.getAssetType()) && !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cây xanh: " + idOrKey));
    }

    private void apply(AssetRecordEntity entity, AssetCreateUpdateDto dto, Map<String, Object> attributes,
                       GreenTreeAccessService.AccessScope scope, boolean create) {
        entity.setName(dto.name().trim());
        if (create || dto.routeCode() != null) entity.setRouteCode(dto.routeCode());
        if (create || dto.routeName() != null) entity.setRouteName(dto.routeName());
        if (create || dto.kmFrom() != null) entity.setKmFrom(dto.kmFrom());
        if (create || dto.kmTo() != null) entity.setKmTo(dto.kmTo());
        if (dto.lytrinh() != null) entity.setLytrinh(dto.lytrinh());
        else if (create || dto.kmFrom() != null) entity.setLytrinh(formatLytrinh(dto.kmFrom(), dto.kmTo()));
        if (create || dto.provinceId() != null) entity.setProvinceId(dto.provinceId());
        if (create || dto.provinceName() != null) entity.setProvinceName(dto.provinceName());
        if (create || dto.districtName() != null) entity.setDistrictName(dto.districtName());
        if (create || dto.townName() != null) entity.setTownName(dto.townName());
        if (create || dto.roadClass() != null) entity.setRoadClass(dto.roadClass());
        if (create || dto.roadType() != null) entity.setRoadType(dto.roadType());
        if (create || dto.organizationId() != null) entity.setOrganizationId(
                dto.organizationId() == null ? "moc_dbvn" : dto.organizationId());
        String branchId = dto.branchId();
        if (!scope.isAdmin()) {
            if (!scope.hasBranch()) throw new AccessDeniedException("Tài khoản chưa được gán phạm vi đơn vị");
            if (branchId != null && !scope.branchId().equals(branchId)) {
                throw new AccessDeniedException("Không được ghi cây xanh ngoài phạm vi đơn vị");
            }
            branchId = scope.branchId();
        }
        if (create || branchId != null) entity.setBranchId(branchId);
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
        entity.setAttributes(GreenTreeAttributeSupport.writeObject(objectMapper, attributes));
        entity.setIsDeleted(false);
        entity.setUpdatedAt(OffsetDateTime.now());
    }

    private void updateGeometry(Long assetId, Map<String, Object> attributes) {
        GreenTreeAttributeSupport.Coordinates coordinates = GreenTreeAttributeSupport.coordinates(attributes);
        if (coordinates.present()) {
            geometryRepository.upsertPointGeometry(assetId, coordinates.longitude(), coordinates.latitude());
        } else {
            geometryRepository.deleteByAssetId(assetId);
        }
    }

    private void requireVisible(AssetRecordEntity entity, GreenTreeAccessService.AccessScope scope) {
        if (scope.isAdmin()) return;
        if (!scope.hasBranch()) throw new ResourceNotFoundException("Không tìm thấy cây xanh trong phạm vi được cấp");
        if (entity.getRawRecordId() != null && scope.userId() != null && scope.userId() > 0) {
            Boolean visible = database.queryForObject("SELECT vroad_record_visible(?, ?)", Boolean.class,
                    entity.getRawRecordId(), scope.userId());
            if (Boolean.TRUE.equals(visible)) return;
        } else if (entity.getRawRecordId() == null && scope.branchId().equals(entity.getBranchId())) {
            return;
        }
        throw new ResourceNotFoundException("Không tìm thấy cây xanh trong phạm vi được cấp");
    }

    private void requireVersion(Integer supplied, Integer current, String idOrKey) {
        if (supplied == null) throw new ConflictException("Bắt buộc cung cấp version khi cập nhật cây xanh");
        if (!supplied.equals(current)) {
            throw new ConflictException("Xung đột phiên bản cây xanh '" + idOrKey + "': hiện tại "
                    + current + ", yêu cầu " + supplied);
        }
    }

    private String formatLytrinh(BigDecimal from, BigDecimal to) {
        if (from != null && to != null) return "Km " + from.toPlainString() + " - Km " + to.toPlainString();
        if (from != null) return "Km " + from.toPlainString();
        return null;
    }
}

