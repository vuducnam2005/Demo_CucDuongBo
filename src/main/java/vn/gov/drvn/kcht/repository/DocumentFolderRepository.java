package vn.gov.drvn.kcht.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.DocumentFolderEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFolderRepository extends JpaRepository<DocumentFolderEntity, Long> {

    List<DocumentFolderEntity> findByIsDeletedFalseOrderBySortOrderAsc();

    List<DocumentFolderEntity> findByParentFolderIdAndIsDeletedFalseOrderBySortOrderAsc(Long parentFolderId);

    Optional<DocumentFolderEntity> findByFolderCodeAndIsDeletedFalse(String folderCode);

    boolean existsByFolderCodeAndIsDeletedFalse(String folderCode);
}
