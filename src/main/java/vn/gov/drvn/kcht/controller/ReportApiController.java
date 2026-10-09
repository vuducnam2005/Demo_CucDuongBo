package vn.gov.drvn.kcht.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.report.MaintenanceReportDto;
import vn.gov.drvn.kcht.dto.report.RoadLengthReportDto;
import vn.gov.drvn.kcht.dto.report.RoadSignBlackspotReportDto;
import vn.gov.drvn.kcht.dto.report.IriRoughnessReportDto;
import vn.gov.drvn.kcht.service.ReportService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Báo cáo thống kê chuyên đề: Chiều dài đường, Kế hoạch bảo trì, Biển báo và Điểm đen TNGT")
public class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/road-lengths")
    @Operation(summary = "Báo cáo chiều dài mạng lưới đường bộ",
            description = "Tổng hợp chiều dài theo tuyến quốc lộ, phân loại mặt đường, cấp đường và đơn vị quản lý.")
    public ResponseEntity<RoadLengthReportDto> getRoadLengthReport(
            @Parameter(description = "Mã đơn vị quản lý (Khu QLĐB I, II, III, IV)")
            @RequestParam(required = false) String branch,
            @Parameter(description = "Mã tuyến đường (VD: QL.1, QL.5)")
            @RequestParam(required = false) String route,
            @Parameter(description = "Loại mặt đường (Bê tông nhựa, Bê tông xi măng, v.v.)")
            @RequestParam(required = false) String surfaceType) {

        return ResponseEntity.ok(reportService.getRoadLengthReport(branch, route, surfaceType));
    }

    @GetMapping("/road-lengths/export")
    @Operation(summary = "Xuất báo cáo chiều dài đường ra tệp CSV (UTF-8 BOM)",
            description = "Xuất toàn bộ danh sách tuyến đường và chiều dài ra CSV tương thích hoàn hảo với Microsoft Excel tiếng Việt.")
    public ResponseEntity<byte[]> exportRoadLengthReport(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String surfaceType) {

        byte[] csvData = reportService.exportRoadLengthReportCsv(branch, route, surfaceType);
        String filename = "Bao_cao_chieu_dai_duong_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/maintenance")
    @Operation(summary = "Báo cáo kế hoạch và thực hiện bảo trì đường bộ",
            description = "Tổng hợp danh mục dự án sửa chữa định kỳ, đột xuất, kinh phí thực hiện theo đơn vị và năm kế hoạch.")
    public ResponseEntity<MaintenanceReportDto> getMaintenanceReport(
            @Parameter(description = "Mã đơn vị quản lý")
            @RequestParam(required = false) String branch,
            @Parameter(description = "Năm kế hoạch bảo trì")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "Loại hình bảo trì (Sửa chữa định kỳ, Bảo dưỡng thường xuyên, Xử lý điểm đen)")
            @RequestParam(required = false) String type,
            @Parameter(description = "Trạng thái thực hiện (Hoàn thành, Đang thi công, Chuẩn bị đầu tư)")
            @RequestParam(required = false) String status) {

        return ResponseEntity.ok(reportService.getMaintenanceReport(branch, year, type, status));
    }

    @GetMapping("/maintenance/export")
    @Operation(summary = "Xuất báo cáo bảo trì đường bộ ra tệp CSV (UTF-8 BOM)",
            description = "Xuất danh mục dự án và tiến độ kinh phí bảo trì ra CSV tương thích Microsoft Excel.")
    public ResponseEntity<byte[]> exportMaintenanceReport(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {

        byte[] csvData = reportService.exportMaintenanceReportCsv(branch, year, type, status);
        String filename = "Bao_cao_bao_tri_duong_bo_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/road-signs-blackspots")
    @Operation(summary = "Thống kê biển báo hiệu đường bộ và điểm tiềm ẩn tai nạn / điểm đen",
            description = "Phân tích 222.112 biển báo theo 5 nhóm QCVN 41 và danh mục các điểm đen giao thông trọng yếu.")
    public ResponseEntity<RoadSignBlackspotReportDto> getRoadSignBlackspotReport(
            @Parameter(description = "Mã đơn vị quản lý")
            @RequestParam(required = false) String branch,
            @Parameter(description = "Mã tuyến đường")
            @RequestParam(required = false) String route,
            @Parameter(description = "Nhóm biển hoặc mức độ điểm đen")
            @RequestParam(required = false) String category) {

        return ResponseEntity.ok(reportService.getRoadSignBlackspotReport(branch, route, category));
    }

    @GetMapping("/road-signs-blackspots/export")
    @Operation(summary = "Xuất danh sách điểm đen và biển báo ra tệp CSV (UTF-8 BOM)",
            description = "Xuất dữ liệu thống kê điểm đen và biển báo ra CSV tương thích Microsoft Excel.")
    public ResponseEntity<byte[]> exportRoadSignBlackspotReport(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String category) {

        byte[] csvData = reportService.exportRoadSignBlackspotReportCsv(branch, route, category);
        String filename = "Thong_ke_bien_bao_diem_den_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/iri-roughness")
    @Operation(summary = "Báo cáo khảo sát độ gồ ghề mặt đường IRI và phân nhóm HDM-4",
            description = "Tổng hợp số liệu quan trắc IRI 22,1 km đường bộ, phân loại theo tiêu chuẩn quốc tế HDM-4 và danh sách các đoạn xung yếu.")
    public ResponseEntity<IriRoughnessReportDto> getIriRoughnessReport(
            @Parameter(description = "Tên hoặc mã tuyến đường (VD: QL1, QL53)")
            @RequestParam(required = false) String route,
            @Parameter(description = "Mức độ đánh giá (very_poor, poor, fair, good)")
            @RequestParam(required = false) String conditionGroup) {

        return ResponseEntity.ok(reportService.getIriRoughnessReport(route, conditionGroup));
    }

    @GetMapping("/iri-roughness/export")
    @Operation(summary = "Xuất dữ liệu khảo sát IRI ra tệp CSV (UTF-8 BOM)",
            description = "Xuất toàn bộ bảng danh mục đoạn đo IRI và chỉ số độ gồ ghề ra CSV tương thích Microsoft Excel.")
    public ResponseEntity<byte[]> exportIriRoughnessReport(
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String conditionGroup) {

        byte[] csvData = reportService.exportIriRoughnessReportCsv(route, conditionGroup);
        String filename = "Bao_cao_khao_sat_do_go_ghe_IRI_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}
