package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Yêu cầu xóa mềm bản ghi tài sản hạ tầng đường bộ")
public record AssetDeleteRequestDto(

        @Schema(description = "Số hiệu phiên bản hiện hành phục vụ kiểm tra Khóa lạc quan (Optimistic Locking)", example = "1")
        Integer version,

        @Schema(description = "Lý do xóa bản ghi phục vụ nhật ký kiểm toán (Audit Log)", example = "Tháo dỡ biển báo theo phương án tổ chức giao thông mới")
        String reason
) {}
