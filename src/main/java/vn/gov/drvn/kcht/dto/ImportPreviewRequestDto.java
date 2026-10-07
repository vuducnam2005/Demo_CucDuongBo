package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Yêu cầu xem trước và kiểm tra (Dry-run) dữ liệu CSV/JSON nạp")
public record ImportPreviewRequestDto(
        @Schema(description = "Nội dung tệp nạp dạng văn bản thô (CSV hoặc JSON Array)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Nội dung nạp không được để trống")
        String content,

        @Schema(description = "Định dạng dữ liệu nạp: CSV hoặc JSON", example = "CSV", defaultValue = "CSV")
        String format
) {}
