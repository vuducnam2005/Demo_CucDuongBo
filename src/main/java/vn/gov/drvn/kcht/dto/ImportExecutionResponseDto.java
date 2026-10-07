package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Kết quả thực thi nạp dữ liệu theo lô (Batch Import Result)")
public record ImportExecutionResponseDto(
        @Schema(description = "Mã tập dữ liệu", example = "tbl_road_sign")
        String datasetCode,

        @Schema(description = "Chế độ chạy thử (Dry-run)", example = "false")
        boolean dryRun,

        @Schema(description = "Tổng số bản ghi đã xử lý", example = "500")
        int totalProcessed,

        @Schema(description = "Số bản ghi thêm mới thành công", example = "450")
        int insertedCount,

        @Schema(description = "Số bản ghi cập nhật thành công", example = "50")
        int updatedCount,

        @Schema(description = "Số bản ghi thất bại", example = "0")
        int failedCount,

        @Schema(description = "Cờ xác nhận batch giao dịch bị hoàn tác (Rollback) khi xảy ra lỗi", example = "false")
        boolean rolledBack,

        @Schema(description = "Thông báo tổng kết kết quả", example = "Nạp dữ liệu thành công 500 bản ghi")
        String message,

        @Schema(description = "Chi tiết các thông điệp cảnh báo hoặc lỗi")
        List<String> errorMessages
) {}
