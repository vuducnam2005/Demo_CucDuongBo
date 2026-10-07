package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.AppRoleEntity;

import java.util.Optional;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRoleEntity, Long> {
    Optional<AppRoleEntity> findByRoleCode(String roleCode);
    boolean existsByRoleCode(String roleCode);
}
