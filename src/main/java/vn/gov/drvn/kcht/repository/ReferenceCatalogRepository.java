package vn.gov.drvn.kcht.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.ReferenceCatalogEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferenceCatalogRepository extends JpaRepository<ReferenceCatalogEntity, Long>, JpaSpecificationExecutor<ReferenceCatalogEntity> {

    List<ReferenceCatalogEntity> findByCatalogCodeAndIsDeletedFalseOrderBySortOrderAsc(String catalogCode);

    Page<ReferenceCatalogEntity> findByCatalogCodeAndIsDeletedFalse(String catalogCode, Pageable pageable);

    Optional<ReferenceCatalogEntity> findByCatalogCodeAndItemCodeAndIsDeletedFalse(String catalogCode, String itemCode);

    long countByCatalogCodeAndIsDeletedFalse(String catalogCode);

    boolean existsByCatalogCodeAndItemCodeAndIsDeletedFalse(String catalogCode, String itemCode);
}
