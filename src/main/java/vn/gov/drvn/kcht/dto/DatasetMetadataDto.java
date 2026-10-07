package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;

@Schema(description = "Chi tiết cấu trúc và siêu dữ liệu (Metadata) của tập dữ liệu")
public class DatasetMetadataDto {

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

    @Schema(description = "Kiểu hình học không gian (nếu có)", example = "POINT")
    private String geometryType;

    @Schema(description = "Danh sách thuộc tính/trường dữ liệu")
    private List<DatasetFieldDto> fields;

    public DatasetMetadataDto() {
        this.fields = Collections.emptyList();
    }

    public DatasetMetadataDto(String datasetKey, String datasetName, String kind, String endpoint,
                              String sourceFile, int totalRecords, String geometryType, List<DatasetFieldDto> fields) {
        this.datasetKey = datasetKey;
        this.datasetName = datasetName;
        this.kind = kind;
        this.endpoint = endpoint;
        this.sourceFile = sourceFile;
        this.totalRecords = totalRecords;
        this.geometryType = geometryType;
        this.fields = fields != null ? fields : Collections.emptyList();
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

    public String getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(String geometryType) {
        this.geometryType = geometryType;
    }

    public List<DatasetFieldDto> getFields() {
        return fields;
    }

    public void setFields(List<DatasetFieldDto> fields) {
        this.fields = fields;
    }
}
