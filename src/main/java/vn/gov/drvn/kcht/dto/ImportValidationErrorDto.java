package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chi tiết lỗi kiểm tra tính hợp lệ của dòng dữ liệu nạp")
public record ImportValidationErrorDto(
        @Schema(description = "Chỉ số số thứ tự dòng (1-indexed)", example = "4")
        int rowNumber,

        @Schema(description = "Tên trường vi phạm", example = "name")
        String field,

        @Schema(description = "Thông điệp mô tả lỗi", example = "Tên công trình/tài sản không được để trống")
        String message
) {}
