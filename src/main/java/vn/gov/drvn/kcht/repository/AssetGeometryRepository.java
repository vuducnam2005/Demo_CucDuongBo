package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.AssetGeometryEntity;
import vn.gov.drvn.kcht.repository.projection.AssetSpatialProjection;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetGeometryRepository extends JpaRepository<AssetGeometryEntity, Long> {

    Optional<AssetGeometryEntity> findByAssetId(Long assetId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    void deleteByAssetId(Long assetId);

    /**
     * Chèn hoặc cập nhật hình học dạng điểm Point trong PostGIS.
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO asset_geometry (asset_id, geom_type, geom, point_geom, bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax, srid, is_valid, updated_at)
        VALUES (:assetId, 'POINT', ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), :lon, :lat, :lon, :lat, 4326, true, CURRENT_TIMESTAMP)
        ON CONFLICT (asset_id) DO UPDATE SET
            geom_type = EXCLUDED.geom_type,
            geom = EXCLUDED.geom,
            point_geom = EXCLUDED.point_geom,
            bbox_xmin = EXCLUDED.bbox_xmin,
            bbox_ymin = EXCLUDED.bbox_ymin,
            bbox_xmax = EXCLUDED.bbox_xmax,
            bbox_ymax = EXCLUDED.bbox_ymax,
            updated_at = CURRENT_TIMESTAMP
    """, nativeQuery = true)
    int upsertPointGeometry(@Param("assetId") Long assetId, @Param("lon") java.math.BigDecimal lon, @Param("lat") java.math.BigDecimal lat);

    /**
     * Chèn hoặc cập nhật hình học dạng đoạn thẳng LineString trong PostGIS.
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
        INSERT INTO asset_geometry (asset_id, geom_type, geom, line_geom, bbox_xmin, bbox_ymin, bbox_xmax, bbox_ymax, srid, is_valid, updated_at)
        VALUES (:assetId, 'LINESTRING', ST_SetSRID(ST_MakeLine(ST_MakePoint(:x1, :y1), ST_MakePoint(:x2, :y2)), 4326), ST_SetSRID(ST_MakeLine(ST_MakePoint(:x1, :y1), ST_MakePoint(:x2, :y2)), 4326), LEAST(:x1, :x2), LEAST(:y1, :y2), GREATEST(:x1, :x2), GREATEST(:y1, :y2), 4326, true, CURRENT_TIMESTAMP)
        ON CONFLICT (asset_id) DO UPDATE SET
            geom_type = EXCLUDED.geom_type,
            geom = EXCLUDED.geom,
            line_geom = EXCLUDED.line_geom,
            bbox_xmin = EXCLUDED.bbox_xmin,
            bbox_ymin = EXCLUDED.bbox_ymin,
            bbox_xmax = EXCLUDED.bbox_xmax,
            bbox_ymax = EXCLUDED.bbox_ymax,
            updated_at = CURRENT_TIMESTAMP
    """, nativeQuery = true)
    int upsertLineGeometry(@Param("assetId") Long assetId,
                           @Param("x1") java.math.BigDecimal x1, @Param("y1") java.math.BigDecimal y1,
                           @Param("x2") java.math.BigDecimal x2, @Param("y2") java.math.BigDecimal y2);

    /**
     * Truy vấn không gian PostGIS trả về GeoJSON theo khung nhìn Bounding Box (BBOX).
     */
    @Query(value = """
        SELECT 
            g.id AS id,
            g.asset_id AS assetId,
            a.record_id AS recordId,
            a.name AS name,
            a.dataset_code AS datasetCode,
            a.asset_type AS assetType,
            a.route_code AS routeCode,
            a.lytrinh AS lytrinh,
            a.province_name AS provinceName,
            a.branch_id AS branchId,
            a.state AS state,
            g.geom_type AS geomType,
            ST_AsGeoJSON(g.geom) AS geoJson,
            g.bbox_xmin AS bboxXmin,
            g.bbox_ymin AS bboxYmin,
            g.bbox_xmax AS bboxXmax,
            g.bbox_ymax AS bboxYmax
        FROM asset_geometry g
        JOIN asset_record a ON g.asset_id = a.id
        WHERE a.is_deleted = false
          AND a.dataset_code = :datasetCode
          AND (g.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        LIMIT :limit
        """, nativeQuery = true)
    List<AssetSpatialProjection> findByDatasetAndBoundingBox(
            @Param("datasetCode") String datasetCode,
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat,
            @Param("limit") int limit
    );

    /**
     * Truy vấn không gian toàn cục xuyên suốt các loại tài sản theo BBOX.
     */
    @Query(value = """
        SELECT 
            g.id AS id,
            g.asset_id AS assetId,
            a.record_id AS recordId,
            a.name AS name,
            a.dataset_code AS datasetCode,
            a.asset_type AS assetType,
            a.route_code AS routeCode,
            a.lytrinh AS lytrinh,
            a.province_name AS provinceName,
            a.branch_id AS branchId,
            a.state AS state,
            g.geom_type AS geomType,
            ST_AsGeoJSON(g.geom) AS geoJson,
            g.bbox_xmin AS bboxXmin,
            g.bbox_ymin AS bboxYmin,
            g.bbox_xmax AS bboxXmax,
            g.bbox_ymax AS bboxYmax
        FROM asset_geometry g
        JOIN asset_record a ON g.asset_id = a.id
        WHERE a.is_deleted = false
          AND (g.geom && ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326))
        LIMIT :limit
        """, nativeQuery = true)
    List<AssetSpatialProjection> findAllInBoundingBox(
            @Param("minLon") double minLon,
            @Param("minLat") double minLat,
            @Param("maxLon") double maxLon,
            @Param("maxLat") double maxLat,
            @Param("limit") int limit
    );
}
