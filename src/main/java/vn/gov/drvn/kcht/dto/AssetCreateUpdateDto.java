package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "Dữ liệu yêu cầu thêm mới hoặc cập nhật tài sản hạ tầng đường bộ")
public record AssetCreateUpdateDto(

        @Schema(description = "Tên công trình/tài sản", example = "Biển báo P.102 Cấm đi ngược chiều", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Tên công trình/tài sản không được để trống")
        @Size(max = 500, message = "Tên công trình không được vượt quá 500 ký tự")
        String name,

        @Schema(description = "Mã tuyến đường", example = "QL.1")
        @Size(max = 100, message = "Mã tuyến không được vượt quá 100 ký tự")
        String routeCode,

        @Schema(description = "Tên tuyến đường", example = "Quốc lộ 1")
        @Size(max = 255, message = "Tên tuyến không được vượt quá 255 ký tự")
        String routeName,

        @Schema(description = "Lý trình từ (Km số)", example = "128.500")
        @DecimalMin(value = "0.0", message = "Lý trình từ (km_from) phải lớn hơn hoặc bằng 0")
        BigDecimal kmFrom,

        @Schema(description = "Lý trình đến (Km số)", example = "129.000")
        @DecimalMin(value = "0.0", message = "Lý trình đến (km_to) phải lớn hơn hoặc bằng 0")
        BigDecimal kmTo,

        @Schema(description = "Chuỗi hiển thị lý trình", example = "Km 128 + 500")
        String lytrinh,

        @Schema(description = "Mã tỉnh thành phố", example = "c_tinhthanhpho_hanoi")
        String provinceId,

        @Schema(description = "Tên tỉnh thành phố", example = "Hà Nội")
        String provinceName,

        @Schema(description = "Tên quận huyện", example = "Thường Tín")
        String districtName,

        @Schema(description = "Tên xã phường", example = "Thị trấn Thường Tín")
        String townName,

        @Schema(description = "Cấp kỹ thuật đường bộ", example = "Cấp I - II đồng bằng")
        String roadClass,

        @Schema(description = "Loại đường bộ", example = "Quốc lộ")
        String roadType,

        @Schema(description = "Cơ quan chủ quản", example = "moc_dbvn")
        String organizationId,

        @Schema(description = "Mã đơn vị quản lý đường bộ", example = "kqldb_1")
        String branchId,

        @Schema(description = "Tên hiển thị đơn vị quản lý", example = "Khu Quản lý đường bộ I")
        String branchName,

        @Schema(description = "Đơn vị/Hạt quản lý trực tiếp", example = "Chi cục Quản lý đường bộ I.1")
        String managementAgency,

        @Schema(description = "Mã trạng thái duyệt", example = "Approved")
        String state,

        @Schema(description = "Tên trạng thái duyệt", example = "Đã duyệt")
        String stateName,

        @Schema(description = "Cấp duyệt trạng thái", example = "cdb_vn")
        String levelState,

        @Schema(description = "Tình trạng khai thác kỹ thuật", example = "Bình thường")
        String activeStatus,

        @Schema(description = "Giá trị kinh phí bảo trì (VNĐ)", example = "25000000.00")
        BigDecimal maintainValue,

        @Schema(description = "Năm xây dựng hoặc đưa vào sử dụng", example = "2021")
        Integer constructionYear,

        @Schema(description = "Mã định danh công trình cha (nếu có)", example = "bridge_45228")
        String parentId,

        @Schema(description = "Thuộc tính động mở rộng JSONB của dataset")
        Map<String, Object> attributes,

        @Schema(description = "Số hiệu phiên bản dùng cho Khóa lạc quan (Optimistic Locking). Bắt buộc khi cập nhật", example = "1")
        Integer version
) {}
