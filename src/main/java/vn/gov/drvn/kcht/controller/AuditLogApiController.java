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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.gov.drvn.kcht.dto.AuditLogItemDto;
import vn.gov.drvn.kcht.dto.ErrorResponseDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.service.AuditLogService;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@Validated
@Tag(name = "Audit Logs", description = "Truy vết và kiểm toán lịch sử đăng nhập, thay đổi dữ liệu công trình và tài liệu")
public class AuditLogApiController {

    private final AuditLogService auditLogService;

    public AuditLogApiController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('AUDIT:READ')")
    @Operation(summary = "Tra cứu nhật ký kiểm toán hệ thống",
            description = "Dành riêng cho Quản trị viên (ROLE_ADMIN) và Lãnh đạo (ROLE_MANAGER) tra cứu lịch sử thao tác người dùng, đăng nhập và sửa đổi dữ liệu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập nhật ký kiểm toán",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PagedResponse<AuditLogItemDto>> getAuditLogs(
            @Parameter(description = "Lọc theo tên đăng nhập người thao tác")
            @RequestParam(required = false) String username,
            @Parameter(description = "Lọc theo phân loại thực thể (AUTH, ASSET, USER, DOCUMENT)")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PagedResponse<AuditLogEntity> entityPage = auditLogService.getAuditLogs(username, entityType, page, size);

        List<AuditLogItemDto> items = entityPage.getContent().stream()
                .map(this::toDto)
                .toList();

        PagedResponse<AuditLogItemDto> response = new PagedResponse<>(
                items,
                entityPage.getPage(),
                entityPage.getSize(),
                entityPage.getTotalElements(),
                entityPage.getTotalPages(),
                entityPage.isFirst(),
                entityPage.isLast()
        );

        return ResponseEntity.ok(response);
    }

    private AuditLogItemDto toDto(AuditLogEntity e) {
        return new AuditLogItemDto(
                e.getId(),
                e.getUserId(),
                e.getUsername(),
                e.getAction(),
                e.getEntityType(),
                e.getEntityId(),
                e.getOldValues(),
                e.getNewValues(),
                e.getIpAddress(),
                e.getUserAgent(),
                e.getCreatedAt()
        );
    }
}
