package vn.gov.drvn.kcht.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface AssetRecordRepository extends JpaRepository<AssetRecordEntity, Long>, JpaSpecificationExecutor<AssetRecordEntity> {

    Optional<AssetRecordEntity> findByRecordIdAndIsDeletedFalse(String recordId);

    Optional<AssetRecordEntity> findByRecordId(String recordId);

    boolean existsByRecordId(String recordId);

    Page<AssetRecordEntity> findByDatasetCodeAndIsDeletedFalse(String datasetCode, Pageable pageable);

    Page<AssetRecordEntity> findByDatasetCodeAndRouteCodeAndIsDeletedFalse(String datasetCode, String routeCode, Pageable pageable);

    long countByDatasetCodeAndIsDeletedFalse(String datasetCode);

    long countByIsDeletedFalse();

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE AssetRecordEntity a SET a.isDeleted = true, a.deletedAt = :deletedAt, a.deletedBy = :deletedBy WHERE a.id = :id")
    int softDeleteById(@Param("id") Long id, @Param("deletedAt") OffsetDateTime deletedAt, @Param("deletedBy") String deletedBy);

    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("UPDATE AssetRecordEntity a SET a.isDeleted = true, a.deletedAt = :deletedAt, a.deletedBy = :deletedBy WHERE a.recordId = :recordId")
    int softDeleteByRecordId(@Param("recordId") String recordId, @Param("deletedAt") OffsetDateTime deletedAt, @Param("deletedBy") String deletedBy);
}
