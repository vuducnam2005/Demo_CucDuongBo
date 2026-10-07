package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.ImportErrorEntity;
import java.util.List;

@Repository
public interface ImportErrorRepository extends JpaRepository<ImportErrorEntity, Long> {
    List<ImportErrorEntity> findByJobId(Long jobId);
    long countByJobId(Long jobId);
}
