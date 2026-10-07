package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.AppUserEntity;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
    Optional<AppUserEntity> findByUsername(String username);
    Optional<AppUserEntity> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("""
            SELECT u FROM AppUserEntity u
            WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR u.roleId IN (
                    SELECT r.id FROM AppRoleEntity r
                    WHERE LOWER(r.roleCode) LIKE LOWER(CONCAT('%', :q, '%'))
                       OR LOWER(r.roleName) LIKE LOWER(CONCAT('%', :q, '%'))
               )
            """)
    Page<AppUserEntity> search(@Param("q") String query, Pageable pageable);
}
