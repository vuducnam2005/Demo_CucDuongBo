package vn.gov.drvn.kcht.dto.report;

import java.util.List;

public record IriRoughnessReportDto(
        IriSummary summary,
        List<ConditionDistribution> distribution,
        List<IriSegmentItem> segments,
        long totalSegments
) {
    public record IriSummary(
            double surveyLengthKm,
            long totalValidSegments,
            double averageIri,
            double medianIri,
            String standardName,
            long totalCorrelatedDefects,
            double poorOrVeryPoorPercentage
    ) {}

    public record ConditionDistribution(
            String conditionGroup,
            String conditionLabel,
            long count,
            double percentage,
            String color,
            String description
    ) {}

    public record IriSegmentItem(
            int rank,
            String roadName,
            String routeCode,
            String chainage,
            Long startMeters,
            Long endMeters,
            double iriValue,
            Double speedKmh,
            String conditionGroup,
            String conditionLabel,
            Integer defectCount
    ) {}
}
