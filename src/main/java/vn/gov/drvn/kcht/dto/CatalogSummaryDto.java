package vn.gov.drvn.kcht.dto;

public record CatalogSummaryDto(
        String catalogCode,
        String catalogName,
        String description,
        long itemCount,
        String sourceTable,
        boolean editable,
        String groupKey,
        String groupName,
        String icon
) {
    /**
     * Backward-compatible constructor (existing code uses 6 params).
     */
    public CatalogSummaryDto(String catalogCode, String catalogName, String description,
                             long itemCount, String sourceTable, boolean editable) {
        this(catalogCode, catalogName, description, itemCount, sourceTable, editable,
             "other", "Khác", "database");
    }
}
