package vn.gov.drvn.kcht.dto.report;

import java.util.List;

public record RoadSignBlackspotReportDto(
        OverviewSummary summary,
        List<SignCategoryItem> signCategories,
        List<BlackspotItem> blackspots,
        List<BranchSignStatistics> branchStatistics
) {
    public record OverviewSummary(
            long totalRoadSigns,
            int totalSignCategories,
            int totalBlackspots,
            int highRiskBlackspots,
            int rectifiedBlackspots,
            int monitoredBlackspots
    ) {}

    public record SignCategoryItem(
            String categoryCode,
            String categoryName,
            String signPrefix,
            long count,
            double percent,
            String sampleSignCode,
            String description
    ) {}

    public record BlackspotItem(
            String id,
            String spotCode,
            String routeCode,
            String kmMarker,
            String branchId,
            String branchName,
            String severity, // "Rất nguy hiểm (Điểm đen)", "Nguy hiểm (Điểm tiềm ẩn)", "Đã xử lý"
            String description,
            int incidentCount,
            int fatalityCount,
            String rectificationStatus,
            Double longitude,
            Double latitude
    ) {}

    public record BranchSignStatistics(
            String branchId,
            String branchName,
            long signCount,
            int blackspotCount
    ) {}
}
