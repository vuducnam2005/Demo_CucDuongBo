package vn.gov.drvn.kcht.mapper;

import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.dto.DocumentFolderDto;
import vn.gov.drvn.kcht.dto.DocumentItemDto;
import vn.gov.drvn.kcht.entity.DocumentFolderEntity;
import vn.gov.drvn.kcht.entity.DocumentMetadataEntity;

/**
 * Mapper chuyển đổi giữa DocumentFolder / DocumentMetadata Entity và DTOs.
 */
@Component
public class DocumentMapper {

    public DocumentFolderDto toFolderDto(DocumentFolderEntity entity, int documentCount) {
        if (entity == null) return null;
        String parent = entity.getParentFolderId() != null ? String.valueOf(entity.getParentFolderId()) : "#";
        return new DocumentFolderDto(
                String.valueOf(entity.getId()),
                entity.getFolderCode(),
                entity.getFolderName(),
                parent,
                documentCount
        );
    }

    public DocumentItemDto toItemDto(DocumentMetadataEntity entity) {
        if (entity == null) return null;
        String folderIdStr = entity.getFolderId() != null ? String.valueOf(entity.getFolderId()) : "";
        String createdAtStr = entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "";

        return new DocumentItemDto(
                String.valueOf(entity.getId()),
                entity.getFileEntryId(),
                entity.getOriginalName(),
                entity.getFileExtension(),
                entity.getMimeType(),
                entity.getFileSize(),
                folderIdStr,
                entity.getBranchId() != null ? entity.getBranchId() : entity.getOrganizationId(),
                entity.getAssetRecordId(),
                "asset_record",
                entity.getUploaderUsername(),
                createdAtStr
        );
    }
}
