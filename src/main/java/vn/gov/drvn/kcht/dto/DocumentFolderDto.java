package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Thư mục quản lý hồ sơ, tài liệu")
public class DocumentFolderDto {

    @Schema(description = "Định danh thư mục", example = "root")
    private String id;

    @Schema(description = "Mã thư mục", example = "root")
    private String folderCode;

    @Schema(description = "Tên thư mục", example = "Quản lý tài liệu")
    private String folderName;

    @Schema(description = "Mã thư mục cha", example = "#")
    private String parentId;

    @Schema(description = "Số lượng tài liệu chứa trong thư mục", example = "250")
    private int count;

    public DocumentFolderDto() {
    }

    public DocumentFolderDto(String id, String folderCode, String folderName, String parentId, int count) {
        this.id = id;
        this.folderCode = folderCode;
        this.folderName = folderName;
        this.parentId = parentId;
        this.count = count;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFolderCode() {
        return folderCode;
    }

    public void setFolderCode(String folderCode) {
        this.folderCode = folderCode;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
