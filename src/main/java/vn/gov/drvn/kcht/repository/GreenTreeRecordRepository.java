package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.GreenTreeRecordEntity;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface GreenTreeRecordRepository extends JpaRepository<GreenTreeRecordEntity, Long>,
        JpaSpecificationExecutor<GreenTreeRecordEntity> {

    Optional<GreenTreeRecordEntity> findByRecordIdAndIsDeletedFalse(String recordId);

    Optional<GreenTreeRecordEntity> findByRecordId(String recordId);

    boolean existsByRecordId(String recordId);

    long countByIsDeletedFalse();

    @Modifying
    @Query(value = """
            UPDATE green_tree_record
            SET geom = ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
            """, nativeQuery = true)
    int updateGeometry(@Param("id") Long id,
                       @Param("longitude") BigDecimal longitude,
                       @Param("latitude") BigDecimal latitude);

    @Modifying
    @Query(value = "UPDATE green_tree_record SET geom = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = :id",
            nativeQuery = true)
    int clearGeometry(@Param("id") Long id);
}

