package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.DatasetRegistryEntity;
import java.util.Optional;

@Repository
public interface DatasetRegistryRepository extends JpaRepository<DatasetRegistryEntity, Long> {
    Optional<DatasetRegistryEntity> findByDatasetKey(String datasetKey);
    boolean existsByDatasetKey(String datasetKey);
}
