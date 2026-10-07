package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Thực thể ánh xạ bảng thư mục hồ sơ (document_folder).
 */
@Entity
@Table(name = "document_folder")
public class DocumentFolderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "folder_code", nullable = false, unique = true, length = 100)
    private String folderCode;

    @Column(name = "folder_name", nullable = false, length = 255)
    private String folderName;

    @Column(name = "parent_folder_id")
    private Long parentFolderId;

    @Column(name = "organization_id", length = 100)
    private String organizationId = "moc_dbvn";

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DocumentFolderEntity() {}

    public DocumentFolderEntity(String folderCode, String folderName, Long parentFolderId, String organizationId, Integer sortOrder) {
        this.folderCode = folderCode;
        this.folderName = folderName;
        this.parentFolderId = parentFolderId;
        this.organizationId = organizationId != null ? organizationId : "moc_dbvn";
        this.sortOrder = sortOrder != null ? sortOrder : 0;
        this.isDeleted = false;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFolderCode() { return folderCode; }
    public void setFolderCode(String folderCode) { this.folderCode = folderCode; }

    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }

    public Long getParentFolderId() { return parentFolderId; }
    public void setParentFolderId(Long parentFolderId) { this.parentFolderId = parentFolderId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean deleted) { isDeleted = deleted; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
