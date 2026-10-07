package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Thực thể ánh xạ bảng chuẩn hóa tài sản hạ tầng đường bộ (asset_record).
 * Đại diện cho tầng Curated / Operational Data Store (ODS).
 */
@Entity
@Table(name = "asset_record", indexes = {
        @Index(name = "idx_asset_record_lookup", columnList = "dataset_code, is_deleted"),
        @Index(name = "idx_asset_record_type", columnList = "asset_type, is_deleted")
})
public class AssetRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_record_id")
    private Long rawRecordId;

    @Column(name = "record_id", nullable = false, unique = true, length = 150)
    private String recordId;

    @Column(name = "gid")
    private Long gid;

    @Column(name = "dataset_code", nullable = false, length = 100)
    private String datasetCode;

    @Column(name = "asset_type", nullable = false, length = 100)
    private String assetType;

    @Column(name = "name", nullable = false, length = 500)
    private String name;

    @Column(name = "route_code", length = 100)
    private String routeCode;

    @Column(name = "route_name", length = 255)
    private String routeName;

    @Column(name = "km_from", precision = 10, scale = 3)
    private BigDecimal kmFrom;

    @Column(name = "km_to", precision = 10, scale = 3)
    private BigDecimal kmTo;

    @Column(name = "lytrinh", length = 100)
    private String lytrinh;

    @Column(name = "province_id", length = 100)
    private String provinceId;

    @Column(name = "province_name", length = 150)
    private String provinceName;

    @Column(name = "district_name", length = 150)
    private String districtName;

    @Column(name = "town_name", length = 150)
    private String townName;

    @Column(name = "road_class", length = 100)
    private String roadClass;

    @Column(name = "road_type", length = 100)
    private String roadType;

    @Column(name = "organization_id", length = 100)
    private String organizationId = "moc_dbvn";

    @Column(name = "branch_id", length = 100)
    private String branchId;

    @Column(name = "branch_name", length = 255)
    private String branchName;

    @Column(name = "management_agency", length = 255)
    private String managementAgency;

    @Column(name = "state", length = 50)
    private String state = "Approved";

    @Column(name = "state_name", length = 100)
    private String stateName = "Đã duyệt";

    @Column(name = "level_state", length = 50)
    private String levelState = "cdb_vn";

    @Column(name = "active_status", length = 100)
    private String activeStatus;

    @Column(name = "maintain_value", precision = 18, scale = 2)
    private BigDecimal maintainValue = BigDecimal.ZERO;

    @Column(name = "construction_year")
    private Integer constructionYear;

    @Column(name = "parent_id", length = 150)
    private String parentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false, columnDefinition = "JSONB")
    private String attributes = "{}";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public AssetRecordEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRawRecordId() { return rawRecordId; }
    public void setRawRecordId(Long rawRecordId) { this.rawRecordId = rawRecordId; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public Long getGid() { return gid; }
    public void setGid(Long gid) { this.gid = gid; }

    public String getDatasetCode() { return datasetCode; }
    public void setDatasetCode(String datasetCode) { this.datasetCode = datasetCode; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRouteCode() { return routeCode; }
    public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }

    public BigDecimal getKmFrom() { return kmFrom; }
    public void setKmFrom(BigDecimal kmFrom) { this.kmFrom = kmFrom; }

    public BigDecimal getKmTo() { return kmTo; }
    public void setKmTo(BigDecimal kmTo) { this.kmTo = kmTo; }

    public String getLytrinh() { return lytrinh; }
    public void setLytrinh(String lytrinh) { this.lytrinh = lytrinh; }

    public String getProvinceId() { return provinceId; }
    public void setProvinceId(String provinceId) { this.provinceId = provinceId; }

    public String getProvinceName() { return provinceName; }
    public void setProvinceName(String provinceName) { this.provinceName = provinceName; }

    public String getDistrictName() { return districtName; }
    public void setDistrictName(String districtName) { this.districtName = districtName; }

    public String getTownName() { return townName; }
    public void setTownName(String townName) { this.townName = townName; }

    public String getRoadClass() { return roadClass; }
    public void setRoadClass(String roadClass) { this.roadClass = roadClass; }

    public String getRoadType() { return roadType; }
    public void setRoadType(String roadType) { this.roadType = roadType; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public String getManagementAgency() { return managementAgency; }
    public void setManagementAgency(String managementAgency) { this.managementAgency = managementAgency; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }

    public String getLevelState() { return levelState; }
    public void setLevelState(String levelState) { this.levelState = levelState; }

    public String getActiveStatus() { return activeStatus; }
    public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }

    public BigDecimal getMaintainValue() { return maintainValue; }
    public void setMaintainValue(BigDecimal maintainValue) { this.maintainValue = maintainValue; }

    public Integer getConstructionYear() { return constructionYear; }
    public void setConstructionYear(Integer constructionYear) { this.constructionYear = constructionYear; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean deleted) { isDeleted = deleted; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
