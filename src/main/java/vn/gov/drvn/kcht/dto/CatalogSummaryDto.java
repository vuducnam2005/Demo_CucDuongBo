package vn.gov.drvn.kcht.dto;

public record CatalogSummaryDto(
        String catalogCode,
        String catalogName,
        String description,
        long itemCount,
        String sourceTable,
        boolean editable
) {}
