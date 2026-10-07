package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu thực thi nạp dữ liệu hàng loạt (Batch Import)")
public record ImportExecutionRequestDto(
        @Schema(description = "Nội dung tệp nạp dạng văn bản thô (CSV hoặc JSON Array)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nội dung nạp không được để trống")
        String content,

        @Schema(description = "Định dạng dữ liệu nạp: CSV hoặc JSON", example = "CSV", defaultValue = "CSV")
        String format,

        @Schema(description = "Cờ chạy thử (Dry-run): nếu true thì chỉ kiểm tra logic, không ghi vào CSDL", defaultValue = "false")
        boolean dryRun,

        @Schema(description = "Kích thước mỗi batch giao dịch CSDL (mặc định 500 dòng/batch, tối đa 2000)", defaultValue = "500")
        @Min(value = 1, message = "batchSize phải >= 1")
        @Max(value = 2000, message = "batchSize tối đa 2000")
        int batchSize
) {
    public ImportExecutionRequestDto {
        if (batchSize <= 0) {
            batchSize = 500;
        }
        if (format == null || format.isBlank()) {
            format = "CSV";
        }
    }
}
