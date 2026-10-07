package vn.gov.drvn.kcht.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Bản ghi dữ liệu chi tiết của dataset")
public class RecordItemDto {

    @Schema(description = "ID tự tăng trong cơ sở dữ liệu", example = "10523")
    private Long id;

    @Schema(description = "Mã dataset sở hữu", example = "tbl_bridge")
    private String datasetKey;

    @Schema(description = "Khóa định danh nghiệp vụ của bản ghi", example = "17786806577720671")
    private String recordKey;

    @Schema(description = "Dữ liệu JSON nguyên bản của bản ghi")
    private JsonNode payload;

    @Schema(description = "Trạng thái bản ghi", example = "RAW_STORED")
    private String recordStatus;

    @Schema(description = "Thời điểm nạp vào CSDL")
    private OffsetDateTime createdAt;

    @Schema(description = "Thời điểm cập nhật mới nhất")
    private OffsetDateTime updatedAt;

    public RecordItemDto() {
    }

    public RecordItemDto(Long id, String datasetKey, String recordKey, JsonNode payload,
                         String recordStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.datasetKey = datasetKey;
        this.recordKey = recordKey;
        this.payload = payload;
        this.recordStatus = recordStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public void setDatasetKey(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public void setRecordKey(String recordKey) {
        this.recordKey = recordKey;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
