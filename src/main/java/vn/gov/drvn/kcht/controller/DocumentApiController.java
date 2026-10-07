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
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.DocumentFolderDto;
import vn.gov.drvn.kcht.dto.DocumentItemDto;
import vn.gov.drvn.kcht.dto.ErrorResponseDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.service.DocumentQueryService;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Validated
@Tag(name = "Documents", description = "Quản lý và khai thác hồ sơ tài liệu, bản vẽ hoàn công công trình KCHT")
public class DocumentApiController {

    private final DocumentQueryService documentQueryService;

    public DocumentApiController(DocumentQueryService documentQueryService) {
        this.documentQueryService = documentQueryService;
    }

    @GetMapping("/folders")
    @Operation(summary = "Lấy danh sách cây thư mục tài liệu",
            description = "Trả về danh sách các thư mục, phân cấp cha-con và số lượng tài liệu trong mỗi thư mục.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công")
    })
    public ResponseEntity<List<DocumentFolderDto>> getFolders() {
        return ResponseEntity.ok(documentQueryService.getFolders());
    }

    @GetMapping
    @Operation(summary = "Tra cứu và phân trang danh sách hồ sơ tài liệu",
            description = "Hỗ trợ tìm kiếm theo tên tài liệu, đối tượng công trình, lọc theo nhóm thư mục, định dạng MIME và phân trang.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PagedResponse<DocumentItemDto>> getDocuments(
            @Parameter(description = "Từ khóa tìm kiếm (tên tài liệu, đối tượng công trình, người tải)")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Mã thư mục / nhóm (VD: sxd_kh, cdb_vn, kqldb_1)")
            @RequestParam(required = false) String folderId,
            @Parameter(description = "Kiểu MIME của tệp (VD: application/pdf)")
            @RequestParam(required = false) String mimeType,
            @Parameter(description = "Phần mở rộng tệp (VD: pdf, docx, xlsx)")
            @RequestParam(required = false) String extension,
            @Parameter(description = "Chỉ số trang (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(documentQueryService.getDocuments(q, folderId, mimeType, extension, page, size));
    }

    @GetMapping({"/{id}", "/{id}/metadata"})
    @Operation(summary = "Xem thông tin chi tiết hồ sơ tài liệu",
            description = "Trả về metadata chi tiết của một hồ sơ tài liệu theo ID hoặc file_entry_id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = DocumentItemDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DocumentItemDto> getDocumentById(
            @Parameter(description = "Mã ID hoặc file_entry_id của tài liệu")
            @PathVariable("id") String id) {

        return ResponseEntity.ok(documentQueryService.getDocumentById(id));
    }

    @GetMapping("/{id}/file")
    @Operation(summary = "Tải về tệp tin tài liệu nhị phân",
            description = "Tải tệp tin tài liệu gốc (PDF, DOCX, XLSX, DWG) kèm header Content-Disposition phù hợp.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tải tệp thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy tài liệu",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<byte[]> downloadFile(
            @Parameter(description = "Mã ID hoặc file_entry_id của tài liệu")
            @PathVariable("id") String id) {

        var download = documentQueryService.downloadFileContent(id);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.fileName() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(download.mimeType()))
                .body(download.content());
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('EDITOR', 'MANAGER', 'ADMIN')")
    @Operation(summary = "Tải lên hồ sơ tài liệu mới (Lưu trữ file nhị phân vào MinIO/S3 và lưu metadata vào CSDL)",
            description = "Hỗ trợ các định dạng tệp hồ sơ công trình (PDF, DOCX, XLSX, DWG, hình ảnh) dung lượng tối đa 25MB.")
    public ResponseEntity<DocumentItemDto> uploadDocument(
            @Parameter(description = "Tệp tin đính kèm")
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @Parameter(description = "Mã thư mục lưu trữ")
            @RequestParam(value = "folderId", required = false) String folderId,
            @Parameter(description = "Mã tài sản công trình liên kết (asset_record_id)")
            @RequestParam(value = "assetRecordId", required = false) String assetRecordId,
            @Parameter(description = "Mã đơn vị quản lý")
            @RequestParam(value = "branchId", required = false) String branchId,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String username = resolveUsername();
        return ResponseEntity.ok(documentQueryService.uploadDocument(file, folderId, assetRecordId, branchId, username));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Xóa hồ sơ tài liệu khỏi hệ thống (Yêu cầu quyền ADMIN hoặc MANAGER)",
            description = "Xóa tệp khỏi vùng lưu trữ MinIO/S3 và cập nhật trạng thái xóa mềm trong cơ sở dữ liệu.")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Mã ID hoặc file_entry_id của tài liệu")
            @PathVariable("id") String id,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String userRole = resolveUserRole(headerRole);
        String username = resolveUsername();
        documentQueryService.deleteDocument(id, userRole, username);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @Operation(summary = "Tạo thư mục tài liệu mới trong cây phân cấp (Yêu cầu quyền ADMIN hoặc MANAGER)")
    public ResponseEntity<DocumentFolderDto> createFolder(
            @Parameter(description = "Tên thư mục mới")
            @RequestParam("folderName") String folderName,
            @Parameter(description = "Mã thư mục cha (mặc định '#' cho thư mục gốc)")
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader(value = "X-User-Role", required = false) String headerRole) {

        String userRole = resolveUserRole(headerRole);
        return ResponseEntity.ok(documentQueryService.createFolder(folderName, parentId, userRole));
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

    private String resolveUsername() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }
        return "admin";
    }
}

