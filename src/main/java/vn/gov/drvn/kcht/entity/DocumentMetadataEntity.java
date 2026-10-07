package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Thực thể ánh xạ bảng siêu dữ liệu hồ sơ tài liệu (document_metadata).
 */
@Entity
@Table(name = "document_metadata", indexes = {
        @Index(name = "idx_doc_meta_asset", columnList = "asset_record_id"),
        @Index(name = "idx_doc_meta_folder", columnList = "folder_id"),
        @Index(name = "idx_doc_meta_sha256", columnList = "sha256")
})
public class DocumentMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_entry_id", nullable = false, unique = true, length = 150)
    private String fileEntryId;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "file_extension", length = 50)
    private String fileExtension;

    @Column(name = "mime_type", length = 150)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "local_path", nullable = false, length = 500)
    private String localPath;

    @Column(name = "sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "asset_record_id", length = 150)
    private String assetRecordId;

    @Column(name = "organization_id", length = 100)
    private String organizationId = "moc_dbvn";

    @Column(name = "branch_id", length = 100)
    private String branchId;

    @Column(name = "uploader_username", length = 150)
    private String uploaderUsername;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DocumentMetadataEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileEntryId() { return fileEntryId; }
    public void setFileEntryId(String fileEntryId) { this.fileEntryId = fileEntryId; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getFileExtension() { return fileExtension; }
    public void setFileExtension(String fileExtension) { this.fileExtension = fileExtension; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }

    public String getAssetRecordId() { return assetRecordId; }
    public void setAssetRecordId(String assetRecordId) { this.assetRecordId = assetRecordId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getUploaderUsername() { return uploaderUsername; }
    public void setUploaderUsername(String uploaderUsername) { this.uploaderUsername = uploaderUsername; }

    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
