package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Thông tin tóm tắt tập dữ liệu KCHT")
public class DatasetSummaryDto {

    @Schema(description = "Mã định danh duy nhất của dataset", example = "tbl_bridge")
    private String datasetKey;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Cầu đường bộ")
    private String datasetName;

    @Schema(description = "Phân loại dataset", example = "asset")
    private String kind;

    @Schema(description = "Endpoint API tương ứng", example = "/api/datasets/tbl_bridge")
    private String endpoint;

    @Schema(description = "Tệp nguồn lưu trữ", example = "assets/tbl_bridge.json")
    private String sourceFile;

    @Schema(description = "Tổng số lượng bản ghi hiện có", example = "11631")
    private int totalRecords;

    @Schema(description = "Trạng thái kích hoạt", example = "true")
    private boolean active;

    @Schema(description = "Thời điểm khởi tạo")
    private OffsetDateTime createdAt;

    public DatasetSummaryDto() {
    }

    public DatasetSummaryDto(String datasetKey, String datasetName, String kind, String endpoint,
                             String sourceFile, int totalRecords, boolean active, OffsetDateTime createdAt) {
        this.datasetKey = datasetKey;
        this.datasetName = datasetName;
        this.kind = kind;
        this.endpoint = endpoint;
        this.sourceFile = sourceFile;
        this.totalRecords = totalRecords;
        this.active = active;
        this.createdAt = createdAt;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public void setDatasetKey(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
