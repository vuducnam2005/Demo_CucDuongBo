package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.ImportJobEntity;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJobEntity, Long> {
    Optional<ImportJobEntity> findFirstByStatusOrderByStartedAtDesc(String status);
}
