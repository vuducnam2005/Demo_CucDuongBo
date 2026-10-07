package vn.gov.drvn.kcht.adapter;

import vn.gov.drvn.kcht.dto.GeoJsonFeatureCollectionDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;

import java.util.Map;

/**
 * Adapter trừu tượng hóa tầng truy cập dữ liệu tài sản hạ tầng đường bộ.
 * Phân định ranh giới giữa tầng Controller/API và Schema CSDL (Curated ODS / Raw Lineage).
 */
public interface AssetQueryAdapter {

    /**
     * Tra cứu danh sách bản ghi có bộ lọc, sắp xếp và phân trang.
     */
    PagedResponse<RecordItemDto> getRecords(String datasetKey, String keyword, Map<String, String> allParams,
                                           String sortParam, int page, int size, String userRole);

    /**
     * Tra cứu một bản ghi đơn lẻ theo ID tự tăng hoặc khóa nghiệp vụ record_id.
     */
    RecordItemDto getRecordById(String datasetKey, String idOrKey, String userRole);

    /**
     * Lấy dữ liệu không gian PostGIS trả về GeoJSON FeatureCollection theo BBOX.
     */
    GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                          Double maxLon, Double maxLat, String bbox,
                                          Integer limit, String userRole);

    /**
     * Lấy dữ liệu không gian PostGIS trả về GeoJSON FeatureCollection có bộ lọc thuộc tính.
     */
    GeoJsonFeatureCollectionDto getGeoData(String datasetKey, Double minLon, Double minLat,
                                          Double maxLon, Double maxLat, String bbox,
                                          String branch, String status, String route,
                                          String q, Integer limit, String userRole);

    /**
     * Lấy cụm không gian PostGIS (Spatial Grid Clustering) cho dữ liệu quy mô lớn.
     */
    GeoJsonFeatureCollectionDto getSpatialClusters(String datasetKey, Double minLon, Double minLat,
                                                  Double maxLon, Double maxLat, String bbox,
                                                  Double gridSize, Integer zoom, String branch,
                                                  String status, String route, String q, String userRole);

    /**
     * Chạy đo kiểm hiệu năng không gian PostGIS Benchmark.
     */
    vn.gov.drvn.kcht.dto.GisBenchmarkDto runGisBenchmark(String dataset1, String dataset2, String userRole);

    /**
     * Lưu trữ hoặc cập nhật tài sản trên tầng Curated, tự động tăng version và ghi nhật ký kiểm toán.
     */
    AssetRecordEntity saveCuratedAsset(AssetRecordEntity asset, String operatorUsername, String actionReason);

    /**
     * Xóa mềm tài sản trên tầng Curated (is_deleted = true) và ghi nhật ký kiểm toán.
     */
    void softDeleteCuratedAsset(Long id, String operatorUsername, String actionReason);

    /**
     * Kiểm tra xem tập dữ liệu đã có bản ghi trong bảng curated asset_record hay chưa.
     */
    boolean isCuratedPopulated(String datasetCode);
}
