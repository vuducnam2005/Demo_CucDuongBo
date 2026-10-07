package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thông tin chi tiết tài liệu hồ sơ")
public class DocumentItemDto {

    @Schema(description = "ID bản ghi", example = "1785752096858455")
    private String id;

    @Schema(description = "Mã định danh file hệ thống", example = "cf57f1de-39fe-40d7-a4f9-08877d48701b")
    private String fileEntryId;

    @Schema(description = "Tên tệp gốc", example = "VI TRI CONG TRINH.pdf")
    private String fileName;

    @Schema(description = "Phần mở rộng tệp", example = "pdf")
    private String fileExtension;

    @Schema(description = "Kiểu MIME", example = "application/pdf")
    private String mimeType;

    @Schema(description = "Kích thước tệp (bytes)", example = "772921")
    private Long fileSize;

    @Schema(description = "Mã nhóm/thư mục", example = "sxd_kh")
    private String groupId;

    @Schema(description = "Tên nhóm/đơn vị sở hữu", example = "Sở Xây dựng tỉnh Khánh Hòa")
    private String groupName;

    @Schema(description = "Tên đối tượng/công trình liên kết", example = "NGUYỄN THỊ ĐỊNH")
    private String objectName;

    @Schema(description = "Bảng tài sản liên kết", example = "Đường đô thị")
    private String tableName;

    @Schema(description = "Người tải lên", example = "Nguyễn Linh Ngọc")
    private String uploader;

    @Schema(description = "Thời điểm tạo hồ sơ", example = "2026-08-04T14:46:55")
    private String createdAt;

    public DocumentItemDto() {
    }

    public DocumentItemDto(String id, String fileEntryId, String fileName, String fileExtension,
                           String mimeType, Long fileSize, String groupId, String groupName,
                           String objectName, String tableName, String uploader, String createdAt) {
        this.id = id;
        this.fileEntryId = fileEntryId;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.groupId = groupId;
        this.groupName = groupName;
        this.objectName = objectName;
        this.tableName = tableName;
        this.uploader = uploader;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileEntryId() {
        return fileEntryId;
    }

    public void setFileEntryId(String fileEntryId) {
        this.fileEntryId = fileEntryId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getUploader() {
        return uploader;
    }

    public void setUploader(String uploader) {
        this.uploader = uploader;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
