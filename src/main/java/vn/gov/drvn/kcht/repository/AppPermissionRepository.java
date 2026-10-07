package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.AppPermissionEntity;

import java.util.List;

@Repository
public interface AppPermissionRepository extends JpaRepository<AppPermissionEntity, Long> {
    List<AppPermissionEntity> findByRoleId(Long roleId);
}
