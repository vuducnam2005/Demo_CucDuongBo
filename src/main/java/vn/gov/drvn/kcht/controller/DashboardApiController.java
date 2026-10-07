package vn.gov.drvn.kcht.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.gov.drvn.kcht.dto.*;
import vn.gov.drvn.kcht.service.DashboardService;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Các API thống kê và tổng hợp số liệu điều hành KCHT đường bộ phía Server")
public class DashboardApiController {

    private final DashboardService dashboardService;

    public DashboardApiController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Lấy số liệu thống kê KPI tổng quan", description = "Tổng hợp tổng số tài sản, số tập dữ liệu, cầu, biển báo, tuyến quốc lộ và chiều dài từ server-side")
    public ResponseEntity<DashboardSummaryDto> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }

    @GetMapping("/stats/branches")
    @Operation(summary = "Thống kê phân bổ theo Đơn vị / Chi nhánh quản lý", description = "Lấy tổng số tài sản, số cầu, biển báo theo từng Khu QLĐB và Sở GTVT địa phương")
    public ResponseEntity<List<DashboardBranchStatDto>> getBranchStats() {
        return ResponseEntity.ok(dashboardService.getBranchStats());
    }

    @GetMapping("/stats/datasets")
    @Operation(summary = "Thống kê top các tập dữ liệu tài sản lớn nhất", description = "Danh sách 10 tập dữ liệu KCHT có số lượng bản ghi lớn nhất")
    public ResponseEntity<List<DashboardDatasetStatDto>> getTopDatasets() {
        return ResponseEntity.ok(dashboardService.getTopDatasets());
    }

    @GetMapping("/stats/road-signs")
    @Operation(summary = "Thống kê chuyên đề Biển báo đường bộ", description = "Tổng số biển báo 222,112 biển, phân bổ theo Khu QLĐB và nhóm phân loại QCVN 41:2019/BGTVT")
    public ResponseEntity<DashboardRoadSignStatDto> getRoadSignStats() {
        return ResponseEntity.ok(dashboardService.getRoadSignStats());
    }

    @GetMapping("/stats/road-lengths")
    @Operation(summary = "Thống kê chuyên đề Chiều dài Quốc lộ", description = "Tổng chiều dài 27,469 km của 168 tuyến quốc lộ, danh sách tuyến dài nhất và phân bố cự ly")
    public ResponseEntity<DashboardRoadLengthStatDto> getRoadLengthStats() {
        return ResponseEntity.ok(dashboardService.getRoadLengthStats());
    }

    @GetMapping("/stats/recent-assets")
    @Operation(summary = "Danh sách tài sản cập nhật gần đây", description = "Lấy danh sách các tài sản được nạp hoặc cập nhật mới nhất kèm mốc thời gian")
    public ResponseEntity<List<DashboardRecentAssetDto>> getRecentAssets(
            @Parameter(description = "Số lượng bản ghi cần lấy (mặc định 10)")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentAssets(limit));
    }
}
