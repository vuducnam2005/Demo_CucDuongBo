package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.entity.AppRefreshTokenEntity;

import java.util.Optional;

@Repository
public interface AppRefreshTokenRepository extends JpaRepository<AppRefreshTokenEntity, Long> {

    Optional<AppRefreshTokenEntity> findByTokenHashAndRevokedFalse(String tokenHash);

    Optional<AppRefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Transactional
    @Query("UPDATE AppRefreshTokenEntity t SET t.revoked = true WHERE t.userId = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE AppRefreshTokenEntity t SET t.revoked = true WHERE t.tokenHash = :tokenHash")
    void revokeByTokenHash(@Param("tokenHash") String tokenHash);
}
