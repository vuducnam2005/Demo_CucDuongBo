package vn.gov.drvn.kcht.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.ErrorResponseDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.ReferenceCatalogItemDto;
import vn.gov.drvn.kcht.service.ReferenceCatalogQueryService;

@RestController
@RequestMapping({"/api/reference-catalogs", "/api/catalogs"})
@Validated
@Tag(name = "Reference Catalogs", description = "Tra cứu danh mục dùng chung (Tỉnh thành, cấp đường, biển báo, loại kết cấu,...)")
public class ReferenceCatalogApiController {

    private final ReferenceCatalogQueryService catalogQueryService;

    public ReferenceCatalogApiController(ReferenceCatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/{catalog}")
    @Operation(summary = "Tra cứu các mục thuộc một danh mục tham chiếu",
            description = "Trả về danh sách các giá trị danh mục (mã, tên, giá trị cha, thứ tự) hỗ trợ tìm kiếm và phân trang.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "404", description = "Danh mục không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PagedResponse<ReferenceCatalogItemDto>> getCatalogItems(
            @Parameter(description = "Mã danh mục (VD: reference_moc_dbvn_c_tinhthanhpho, c_capduong, road_sign_catalog)")
            @PathVariable("catalog") String catalog,
            @Parameter(description = "Từ khóa tìm kiếm theo mã hoặc tên mục danh mục")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Chỉ số trang (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng mục mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(catalogQueryService.getCatalogItems(catalog, q, page, size));
    }

    @GetMapping("/{catalog}/{itemCode}")
    @Operation(summary = "Tra cứu một mục danh mục cụ thể theo mã itemCode",
            description = "Trả về thông tin chi tiết của một phần tử danh mục theo mã định danh item_code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = ReferenceCatalogItemDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy phần tử danh mục",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ReferenceCatalogItemDto> getCatalogItemByCode(
            @Parameter(description = "Mã danh mục (VD: c_tinhthanhpho, c_capduong)")
            @PathVariable("catalog") String catalog,
            @Parameter(description = "Mã phần tử danh mục (VD: 01, W.247)")
            @PathVariable("itemCode") String itemCode) {

        return ResponseEntity.ok(catalogQueryService.getCatalogItemByCode(catalog, itemCode));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tổng hợp tất cả các danh mục tham chiếu",
            description = "Trả về danh sách tất cả các danh mục chuẩn hóa trong hệ thống kèm số lượng phần tử.")
    public ResponseEntity<java.util.List<vn.gov.drvn.kcht.dto.CatalogSummaryDto>> getAllCatalogs() {
        return ResponseEntity.ok(catalogQueryService.getAllCatalogsSummary());
    }

    @PostMapping("/{catalog}")
    @Operation(summary = "Tạo mới một mục danh mục tham chiếu (Yêu cầu ADMIN hoặc MANAGER)",
            description = "Thêm mới một giá trị danh mục vào hệ thống. Chỉ người dùng có vai trò ADMIN hoặc MANAGER mới được phép.")
    public ResponseEntity<ReferenceCatalogItemDto> createCatalogItem(
            @Parameter(description = "Mã danh mục")
            @PathVariable("catalog") String catalog,
            @RequestBody @jakarta.validation.Valid ReferenceCatalogItemDto dto,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String userRole = resolveUserRole(headerRole);
        return ResponseEntity.ok(catalogQueryService.createCatalogItem(catalog, dto, userRole));
    }

    @PutMapping("/{catalog}/{itemCode}")
    @Operation(summary = "Cập nhật một mục danh mục tham chiếu (Yêu cầu ADMIN hoặc MANAGER)",
            description = "Chỉnh sửa tên hiển thị, thứ tự, trạng thái hoặc giá trị cha của một mục danh mục.")
    public ResponseEntity<ReferenceCatalogItemDto> updateCatalogItem(
            @Parameter(description = "Mã danh mục")
            @PathVariable("catalog") String catalog,
            @Parameter(description = "Mã phần tử danh mục")
            @PathVariable("itemCode") String itemCode,
            @RequestBody @jakarta.validation.Valid ReferenceCatalogItemDto dto,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String userRole = resolveUserRole(headerRole);
        return ResponseEntity.ok(catalogQueryService.updateCatalogItem(catalog, itemCode, dto, userRole));
    }

    @DeleteMapping("/{catalog}/{itemCode}")
    @Operation(summary = "Xóa mềm một mục danh mục tham chiếu (Yêu cầu ADMIN hoặc MANAGER)",
            description = "Đánh dấu ngừng sử dụng một mục danh mục tham chiếu trong hệ thống.")
    public ResponseEntity<Void> deleteCatalogItem(
            @Parameter(description = "Mã danh mục")
            @PathVariable("catalog") String catalog,
            @Parameter(description = "Mã phần tử danh mục")
            @PathVariable("itemCode") String itemCode,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String userRole = resolveUserRole(headerRole);
        catalogQueryService.deleteCatalogItem(catalog, itemCode, userRole);
        return ResponseEntity.noContent().build();
    }

    private String resolveUserRole(String headerRole) {
        if (headerRole != null && !headerRole.isBlank()) {
            String role = headerRole.trim().toUpperCase();
            return role.startsWith("ROLE_") ? role : "ROLE_" + role;
        }
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            return auth.getAuthorities().iterator().next().getAuthority();
        }
        return "ROLE_VIEWER";
    }
}

