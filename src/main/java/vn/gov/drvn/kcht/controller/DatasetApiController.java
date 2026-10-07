package vn.gov.drvn.kcht.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.service.DatasetQueryService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/datasets")
@Validated
@Tag(name = "Datasets", description = "Quản lý và tra cứu tập dữ liệu KCHT, siêu dữ liệu, bản ghi và bản đồ WebGIS")
public class DatasetApiController {

    private final DatasetQueryService datasetQueryService;
    private final vn.gov.drvn.kcht.adapter.AssetQueryAdapter assetQueryAdapter;
    private final vn.gov.drvn.kcht.service.AssetCrudService assetCrudService;

    public DatasetApiController(DatasetQueryService datasetQueryService,
                                vn.gov.drvn.kcht.adapter.AssetQueryAdapter assetQueryAdapter,
                                vn.gov.drvn.kcht.service.AssetCrudService assetCrudService) {
        this.datasetQueryService = datasetQueryService;
        this.assetQueryAdapter = assetQueryAdapter;
        this.assetCrudService = assetCrudService;
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các dataset KCHT",
            description = "Trả về danh sách các tập dữ liệu có hỗ trợ tìm kiếm theo từ khóa, lọc theo phân loại (asset, module,...) và phân trang server-side.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tham số yêu cầu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PagedResponse<DatasetSummaryDto>> getDatasets(
            @Parameter(description = "Phân loại dataset (VD: asset, module)")
            @RequestParam(required = false) String kind,
            @Parameter(description = "Từ khóa tìm kiếm theo mã hoặc tên dataset")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Chỉ số trang (bắt đầu từ 0)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return ResponseEntity.ok(datasetQueryService.getDatasets(kind, q, page, size));
    }

    @GetMapping("/tree")
    @Operation(summary = "Lấy cây phân cấp danh mục KCHT đường bộ",
            description = "Trả về cây phân cấp danh mục các tập dữ liệu KCHT Đường bộ. Hỗ trợ lazy load với tham số parent.")
    public ResponseEntity<java.util.List<DatasetTreeNodeDto>> getDatasetTree(
            @Parameter(description = "Mã node cha cần lazy load các node con (VD: group_all_assets, group_modules)")
            @RequestParam(required = false) String parent) {
        return ResponseEntity.ok(datasetQueryService.getDatasetTree(parent));
    }

    @GetMapping("/{dataset}/export")
    @Operation(summary = "Xuất dữ liệu dataset theo bộ lọc và tìm kiếm hiện hành sang file CSV",
            description = "Hỗ trợ xuất tối đa 5000 bản ghi theo bộ lọc, từ khóa và sắp xếp hiện hành, định dạng UTF-8 kèm BOM.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Xuất file thành công"),
            @ApiResponse(responseCode = "400", description = "Tham số lọc hoặc sắp xếp không hợp lệ"),
            @ApiResponse(responseCode = "404", description = "Dataset không tồn tại"),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập")
    })
    public ResponseEntity<byte[]> exportDataset(
            @Parameter(description = "Mã định danh dataset (VD: tbl_bridge, tbl_road_sign)")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Từ khóa tìm kiếm")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Cột và chiều sắp xếp")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(description = "Số lượng bản ghi tối đa xuất (mặc định 1000, tối đa 5000)")
            @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int limit,
            @Parameter(hidden = true)
            @RequestParam Map<String, String> allParams,
            @Parameter(description = "Vai trò người dùng")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        byte[] csvData = datasetQueryService.exportRecordsToCsv(dataset, q, allParams, sort, limit, userRole);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dataset + "_export.csv\"")
                .body(csvData);
    }

    @GetMapping("/{dataset}/metadata")
    @Operation(summary = "Lấy siêu dữ liệu và từ điển trường của dataset",
            description = "Trả về chi tiết cấu trúc, danh sách các trường thuộc tính, kiểu dữ liệu và cấu hình tìm kiếm/lọc của dataset.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = DatasetMetadataDto.class))),
            @ApiResponse(responseCode = "404", description = "Dataset không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<DatasetMetadataDto> getDatasetMetadata(
            @Parameter(description = "Mã định danh dataset (VD: tbl_bridge, tbl_road_sign)")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Vai trò người dùng (ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER)")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(datasetQueryService.getDatasetMetadata(dataset, userRole));
    }

    @GetMapping("/{dataset}/records")
    @Operation(summary = "Tra cứu danh sách bản ghi dữ liệu có phân trang và bộ lọc",
            description = "Truy vấn danh sách bản ghi của dataset có hỗ trợ phân trang server-side, tìm kiếm keyword (q), sắp xếp an toàn (sort) và lọc theo trường được phép.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Tham số sắp xếp hoặc bộ lọc không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Dataset không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<PagedResponse<RecordItemDto>> getRecords(
            @Parameter(description = "Mã định danh dataset (VD: tbl_bridge)")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Từ khóa tìm kiếm trong toàn bộ bản ghi")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Cột và chiều sắp xếp (VD: id,asc hoặc created_at,desc)")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(description = "Chỉ số trang (0-indexed)")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang (tối đa 100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(hidden = true)
            @RequestParam Map<String, String> allParams,
            @Parameter(description = "Vai trò người dùng")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(assetQueryAdapter.getRecords(dataset, q, allParams, sort, page, size, userRole));
    }

    @GetMapping("/{dataset}/records/{id}")
    @Operation(summary = "Lấy chi tiết một bản ghi theo ID hoặc khóa định danh",
            description = "Trả về thông tin chi tiết kèm payload JSON nguyên bản của bản ghi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = RecordItemDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bản ghi",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RecordItemDto> getRecordById(
            @Parameter(description = "Mã định danh dataset (VD: tbl_bridge)")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "ID cơ sở dữ liệu hoặc record_key nghiệp vụ")
            @PathVariable("id") String id,
            @Parameter(description = "Vai trò người dùng")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(assetQueryAdapter.getRecordById(dataset, id, userRole));
    }

    @GetMapping("/{dataset}/geo")
    @Operation(summary = "Lấy dữ liệu không gian GeoJSON theo khung nhìn Bounding Box",
            description = "Trả về GeoJSON FeatureCollection theo chuẩn RFC 7946 cho các ứng dụng bản đồ WebGIS. Hỗ trợ lọc theo bbox, đơn vị, trạng thái, tuyến và từ khóa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = GeoJsonFeatureCollectionDto.class))),
            @ApiResponse(responseCode = "400", description = "Tham số bounding box không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Dataset không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<GeoJsonFeatureCollectionDto> getGeoData(
            @Parameter(description = "Mã định danh dataset có dữ liệu không gian (VD: tbl_bridge, tbl_road_sign)")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Kinh độ nhỏ nhất (Tây)", example = "105.0")
            @RequestParam(required = false) Double minLon,
            @Parameter(description = "Vĩ độ nhỏ nhất (Nam)", example = "10.0")
            @RequestParam(required = false) Double minLat,
            @Parameter(description = "Kinh độ lớn nhất (Đông)", example = "107.0")
            @RequestParam(required = false) Double maxLon,
            @Parameter(description = "Vĩ độ lớn nhất (Bắc)", example = "21.0")
            @RequestParam(required = false) Double maxLat,
            @Parameter(description = "Bounding box dạng minLon,minLat,maxLon,maxLat", example = "105.0,10.0,107.0,21.0")
            @RequestParam(required = false) String bbox,
            @Parameter(description = "Lọc theo mã đơn vị quản lý (branch_id)")
            @RequestParam(required = false) String branch,
            @Parameter(description = "Lọc theo trạng thái khai thác (state_name)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Lọc theo tuyến đường (route)")
            @RequestParam(required = false) String route,
            @Parameter(description = "Từ khóa tìm kiếm (q)")
            @RequestParam(required = false) String q,
            @Parameter(description = "Giới hạn số lượng feature trả về (mặc định 500, tối đa 2000)", example = "500")
            @RequestParam(required = false) @Min(1) @Max(2000) Integer limit,
            @Parameter(description = "Vai trò người dùng")
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(assetQueryAdapter.getGeoData(dataset, minLon, minLat, maxLon, maxLat, bbox, branch, status, route, q, limit, userRole));
    }

    @GetMapping("/{dataset}/clusters")
    @Operation(summary = "Lấy cụm không gian PostGIS (Spatial Grid Clustering) cho dữ liệu quy mô lớn",
            description = "Gom cụm không gian nén hàng chục nghìn điểm thành các điểm đại diện cụm (cluster pins), tránh làm quá tải trình duyệt web.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = GeoJsonFeatureCollectionDto.class))),
            @ApiResponse(responseCode = "400", description = "Tham số không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Dataset không tồn tại",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<GeoJsonFeatureCollectionDto> getSpatialClusters(
            @Parameter(description = "Mã định danh dataset (VD: tbl_road_sign, tbl_bridge)")
            @PathVariable("dataset") String dataset,
            @RequestParam(required = false) Double minLon,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLon,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) String bbox,
            @Parameter(description = "Kích thước mắt lưới (đơn vị độ kinh/vĩ)", example = "0.2")
            @RequestParam(required = false) Double gridSize,
            @Parameter(description = "Mức độ zoom hiện tại của bản đồ OpenLayers (1-18)", example = "8")
            @RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String q,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(assetQueryAdapter.getSpatialClusters(dataset, minLon, minLat, maxLon, maxLat, bbox, gridSize, zoom, branch, status, route, q, userRole));
    }

    @GetMapping("/gis/benchmark")
    @Operation(summary = "Đo kiểm hiệu năng không gian PostGIS (Benchmark)",
            description = "Thực thi kiểm chuẩn hiệu năng trên tbl_road_sign (222k bản ghi) và dataset geometry lớn khác, chứng minh tuân thủ nguyên tắc không tải toàn bộ dữ liệu vào browser.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thành công",
                    content = @Content(schema = @Schema(implementation = vn.gov.drvn.kcht.dto.GisBenchmarkDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<vn.gov.drvn.kcht.dto.GisBenchmarkDto> runGisBenchmark(
            @Parameter(description = "Tập dữ liệu 1 (mặc định tbl_road_sign)")
            @RequestParam(defaultValue = "tbl_road_sign") String dataset1,
            @Parameter(description = "Tập dữ liệu 2 (mặc định road_sphere_mirror)")
            @RequestParam(defaultValue = "road_sphere_mirror") String dataset2,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        return ResponseEntity.ok(assetQueryAdapter.runGisBenchmark(dataset1, dataset2, userRole));
    }

    @PostMapping("/{dataset}/records")
    @Operation(summary = "Thêm mới bản ghi tài sản vào tầng Curated",
            description = "Tạo mới bản ghi tài sản hạ tầng đường bộ trong bảng curated asset_record. Tuyệt đối không chỉnh sửa raw_dataset_record. Ghi nhật ký kiểm toán audit_log.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tạo mới thành công",
                    content = @Content(schema = @Schema(implementation = RecordItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu yêu cầu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RecordItemDto> createRecord(
            @Parameter(description = "Mã định danh dataset (VD: tbl_road_sign, tbl_bridge)")
            @PathVariable("dataset") String dataset,
            @Valid @RequestBody AssetCreateUpdateDto dto,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            HttpServletRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "user";
        String ip = request.getRemoteAddr();
        RecordItemDto created = assetCrudService.createRecord(dataset, dto, userRole, username, ip);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{dataset}/records/{id}")
    @Operation(summary = "Cập nhật bản ghi tài sản với Khóa lạc quan (Optimistic Locking)",
            description = "Cập nhật thông tin kỹ thuật bản ghi tài sản. Chỉ tác động vào bảng curated asset_record, giữ liên kết raw và ghi audit_log. Kiểm tra số hiệu version để chống ghi đè đồng thời.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cập nhật thành công",
                    content = @Content(schema = @Schema(implementation = RecordItemDto.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bản ghi",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Xung đột phiên bản (Optimistic Locking Conflict)",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<RecordItemDto> updateRecord(
            @Parameter(description = "Mã định danh dataset")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Mã định danh bản ghi (record_id hoặc ID cơ sở dữ liệu)")
            @PathVariable("id") String id,
            @Valid @RequestBody AssetCreateUpdateDto dto,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            HttpServletRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "user";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(assetCrudService.updateRecord(dataset, id, dto, userRole, username, ip));
    }

    @DeleteMapping("/{dataset}/records/{id}")
    @Operation(summary = "Xóa mềm bản ghi tài sản (Soft Delete)",
            description = "Đánh dấu bản ghi là đã xóa mềm (is_deleted = true) kèm kiểm tra Khóa lạc quan và ghi audit_log. Không xóa raw_dataset_record. Yêu cầu quyền ROLE_MANAGER hoặc ROLE_ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Xóa mềm thành công"),
            @ApiResponse(responseCode = "404", description = "Không tìm thấy bản ghi",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "Xung đột phiên bản khi xóa",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền xóa",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteRecord(
            @Parameter(description = "Mã định danh dataset")
            @PathVariable("dataset") String dataset,
            @Parameter(description = "Mã định danh bản ghi")
            @PathVariable("id") String id,
            @Parameter(description = "Phiên bản hiện tại phục vụ Khóa lạc quan")
            @RequestParam(value = "version", required = false) Integer version,
            @Parameter(description = "Lý do xóa bản ghi")
            @RequestParam(value = "reason", required = false) String reason,
            @RequestBody(required = false) AssetDeleteRequestDto body,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            HttpServletRequest request,
            Principal principal) {
        Integer effectiveVersion = (body != null && body.version() != null) ? body.version() : version;
        String effectiveReason = (body != null && body.reason() != null) ? body.reason() : reason;
        String username = principal != null ? principal.getName() : "user";
        String ip = request.getRemoteAddr();
        assetCrudService.deleteRecord(dataset, id, effectiveVersion, effectiveReason, userRole, username, ip);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{dataset}/import/preview")
    @Operation(summary = "Xem trước và kiểm tra (Dry-run) dữ liệu CSV/JSON nạp",
            description = "Phân tích cú pháp tệp CSV hoặc JSON, đối chiếu danh mục trường schema, phát hiện lỗi dữ liệu mà không ghi vào CSDL.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Phân tích và kiểm tra thành công",
                    content = @Content(schema = @Schema(implementation = ImportPreviewResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Nội dung tệp nạp không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền truy cập",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ImportPreviewResponseDto> previewImport(
            @Parameter(description = "Mã định danh dataset")
            @PathVariable("dataset") String dataset,
            @Valid @RequestBody ImportPreviewRequestDto req,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {
        return ResponseEntity.ok(assetCrudService.previewImport(dataset, req.format(), req.content(), userRole));
    }

    @PostMapping("/{dataset}/import")
    @Operation(summary = "Thực thi nạp dữ liệu hàng loạt theo lô (Transactional Batch Import)",
            description = "Nạp dữ liệu theo lô giao dịch (batchSize), có checkpoint và tự động rollback khi phát hiện lỗi vi phạm ràng buộc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thực thi nạp hoặc dry-run thành công",
                    content = @Content(schema = @Schema(implementation = ImportExecutionResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Dữ liệu nạp không hợp lệ",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Không có quyền thực hiện nạp",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    public ResponseEntity<ImportExecutionResponseDto> executeImport(
            @Parameter(description = "Mã định danh dataset")
            @PathVariable("dataset") String dataset,
            @Valid @RequestBody ImportExecutionRequestDto req,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            HttpServletRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "user";
        String ip = request.getRemoteAddr();
        return ResponseEntity.ok(assetCrudService.executeImport(dataset, req, userRole, username, ip));
    }
}
