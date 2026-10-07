package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Kết quả xem trước và kiểm tra tính hợp lệ dữ liệu nạp")
public record ImportPreviewResponseDto(
        @Schema(description = "Mã tập dữ liệu", example = "tbl_road_sign")
        String datasetCode,

        @Schema(description = "Tổng số dòng phân tích được", example = "50")
        int totalRows,

        @Schema(description = "Số dòng thỏa mãn toàn bộ luật kiểm tra", example = "48")
        int validRows,

        @Schema(description = "Số dòng vi phạm luật dữ liệu", example = "2")
        int errorRows,

        @Schema(description = "Danh sách dữ liệu xem trước tối đa 10 dòng đầu tiên")
        List<Map<String, Object>> previewRows,

        @Schema(description = "Danh sách chi tiết lỗi kiểm tra (nếu có)")
        List<ImportValidationErrorDto> errors,

        @Schema(description = "Trạng thái sẵn sàng cho phép nạp chính thức", example = "false")
        boolean canProceed
) {}
