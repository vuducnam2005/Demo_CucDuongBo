package vn.gov.drvn.kcht.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Cấu trúc phản hồi lỗi chuẩn của API")
public class ErrorResponseDto {

    @Schema(description = "Thời điểm xảy ra lỗi")
    private OffsetDateTime timestamp;

    @Schema(description = "Mã trạng thái HTTP", example = "400")
    private int status;

    @Schema(description = "Tên trạng thái lỗi", example = "Bad Request")
    private String error;

    @Schema(description = "Thông điệp mô tả lỗi chi tiết", example = "Tham số lọc không hợp lệ")
    private String message;

    @Schema(description = "Đường dẫn endpoint phát sinh lỗi", example = "/api/datasets/tbl_bridge/records")
    private String path;

    @Schema(description = "Chi tiết các lỗi validate (nếu có)")
    private List<String> errors;

    public ErrorResponseDto() {
        this.timestamp = OffsetDateTime.now();
    }

    public ErrorResponseDto(int status, String error, String message, String path) {
        this.timestamp = OffsetDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ErrorResponseDto(int status, String error, String message, String path, List<String> errors) {
        this.timestamp = OffsetDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.errors = errors;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
