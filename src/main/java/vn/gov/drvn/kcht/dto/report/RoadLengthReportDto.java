package vn.gov.drvn.kcht.dto.report;

import java.util.List;

public record RoadLengthReportDto(
        ReportSummary summary,
        List<RouteLengthItem> routes,
        List<BranchDistributionItem> branchDistribution,
        List<SurfaceDistributionItem> surfaceDistribution
) {
    public record ReportSummary(
            int totalRoutes,
            int totalSegments,
            double totalLengthKm,
            double averageLengthKm,
            String longestRouteCode,
            double longestRouteLengthKm
    ) {}

    public record RouteLengthItem(
            String routeCode,
            String routeName,
            double lengthKm,
            int segmentCount,
            String branchId,
            String branchName,
            String surfaceType,
            String roadClass,
            String managementUnit
    ) {}

    public record BranchDistributionItem(
            String branchId,
            String branchName,
            int routeCount,
            double totalLengthKm,
            double percent
    ) {}

    public record SurfaceDistributionItem(
            String surfaceType,
            double totalLengthKm,
            double percent
    ) {}
}
