package vn.gov.drvn.kcht.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import vn.gov.drvn.kcht.entity.DocumentMetadataEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadataEntity, Long>, JpaSpecificationExecutor<DocumentMetadataEntity> {

    Optional<DocumentMetadataEntity> findByFileEntryIdAndIsDeletedFalse(String fileEntryId);

    List<DocumentMetadataEntity> findByAssetRecordIdAndIsDeletedFalse(String assetRecordId);

    Page<DocumentMetadataEntity> findByFolderIdAndIsDeletedFalse(Long folderId, Pageable pageable);

    long countByFolderIdAndIsDeletedFalse(Long folderId);

    boolean existsByFileEntryIdAndIsDeletedFalse(String fileEntryId);
}
