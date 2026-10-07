package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.DatasetFieldEntity;
import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetFieldRepository extends JpaRepository<DatasetFieldEntity, Long> {
    List<DatasetFieldEntity> findByDatasetId(Long datasetId);
    Optional<DatasetFieldEntity> findByDatasetIdAndFieldName(Long datasetId, String fieldName);
}
