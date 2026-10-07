package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Thực thể ánh xạ bảng hình học không gian PostGIS (asset_geometry).
 * Liên kết 1-1 với asset_record theo asset_id.
 */
@Entity
@Table(name = "asset_geometry", indexes = {
        @Index(name = "idx_asset_geom_bbox", columnList = "bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax")
})
public class AssetGeometryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, unique = true)
    private Long assetId;

    @Column(name = "geom_type", nullable = false, length = 50)
    private String geomType;

    @Column(name = "bbox_xmin", precision = 12, scale = 8)
    private BigDecimal bboxXmin;

    @Column(name = "bbox_ymin", precision = 12, scale = 8)
    private BigDecimal bboxYmin;

    @Column(name = "bbox_xmax", precision = 12, scale = 8)
    private BigDecimal bboxXmax;

    @Column(name = "bbox_ymax", precision = 12, scale = 8)
    private BigDecimal bboxYmax;

    @Column(name = "srid", nullable = false)
    private Integer srid = 4326;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid = true;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public AssetGeometryEntity() {}

    public AssetGeometryEntity(Long assetId, String geomType,
                               BigDecimal bboxXmin, BigDecimal bboxYmin,
                               BigDecimal bboxXmax, BigDecimal bboxYmax,
                               Integer srid, Boolean isValid) {
        this.assetId = assetId;
        this.geomType = geomType;
        this.bboxXmin = bboxXmin;
        this.bboxYmin = bboxYmin;
        this.bboxXmax = bboxXmax;
        this.bboxYmax = bboxYmax;
        this.srid = srid != null ? srid : 4326;
        this.isValid = isValid != null ? isValid : true;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public String getGeomType() { return geomType; }
    public void setGeomType(String geomType) { this.geomType = geomType; }

    public BigDecimal getBboxXmin() { return bboxXmin; }
    public void setBboxXmin(BigDecimal bboxXmin) { this.bboxXmin = bboxXmin; }

    public BigDecimal getBboxYmin() { return bboxYmin; }
    public void setBboxYmin(BigDecimal bboxYmin) { this.bboxYmin = bboxYmin; }

    public BigDecimal getBboxXmax() { return bboxXmax; }
    public void setBboxXmax(BigDecimal bboxXmax) { this.bboxXmax = bboxXmax; }

    public BigDecimal getBboxYmax() { return bboxYmax; }
    public void setBboxYmax(BigDecimal bboxYmax) { this.bboxYmax = bboxYmax; }

    public Integer getSrid() { return srid; }
    public void setSrid(Integer srid) { this.srid = srid; }

    public Boolean getIsValid() { return isValid; }
    public void setIsValid(Boolean valid) { isValid = valid; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
