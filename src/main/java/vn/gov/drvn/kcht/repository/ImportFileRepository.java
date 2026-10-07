package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.ImportFileEntity;
import java.util.Optional;
import java.util.List;

@Repository
public interface ImportFileRepository extends JpaRepository<ImportFileEntity, Long> {
    Optional<ImportFileEntity> findByJobIdAndFilePath(Long jobId, String filePath);
    List<ImportFileEntity> findByJobId(Long jobId);
    List<ImportFileEntity> findByJobIdAndStatus(Long jobId, String status);
    Optional<ImportFileEntity> findFirstByFilePathAndStatusOrderByStartedAtDesc(String filePath, String status);
}
