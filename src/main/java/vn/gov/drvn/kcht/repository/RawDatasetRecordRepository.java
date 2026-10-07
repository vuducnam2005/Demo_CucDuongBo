package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.RawDatasetRecordEntity;
import java.util.Optional;

@Repository
public interface RawDatasetRecordRepository extends JpaRepository<RawDatasetRecordEntity, Long> {
    Optional<RawDatasetRecordEntity> findByDatasetKeyAndRecordKey(String datasetKey, String recordKey);
    boolean existsByDatasetKeyAndRecordKey(String datasetKey, String recordKey);
    long countByDatasetKey(String datasetKey);
}
