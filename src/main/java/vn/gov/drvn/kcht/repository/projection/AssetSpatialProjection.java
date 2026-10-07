package vn.gov.drvn.kcht.repository.projection;

import java.math.BigDecimal;

/**
 * Spring Data Projection hỗ trợ truy vấn không gian PostGIS hiệu năng cao.
 */
public interface AssetSpatialProjection {
    Long getId();
    Long getAssetId();
    String getRecordId();
    String getName();
    String getDatasetCode();
    String getAssetType();
    String getRouteCode();
    String getLytrinh();
    String getProvinceName();
    String getBranchId();
    String getState();
    String getGeomType();
    String getGeoJson();
    BigDecimal getBboxXmin();
    BigDecimal getBboxYmin();
    BigDecimal getBboxXmax();
    BigDecimal getBboxYmax();
}
