package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;

public class DashboardDatasetStatDto {

    private String datasetKey;
    private String datasetName;
    private String kind;
    private long totalRecords;
    private String sourceFile;
    private String sourceDataset = "dataset_registry";
    private String filter = "Danh mục tập dữ liệu";
    private OffsetDateTime lastUpdated;

    public DashboardDatasetStatDto() {}

    public DashboardDatasetStatDto(String datasetKey, String datasetName, String kind,
                                  long totalRecords, String sourceFile, OffsetDateTime lastUpdated) {
        this.datasetKey = datasetKey;
        this.datasetName = datasetName;
        this.kind = kind;
        this.totalRecords = totalRecords;
        this.sourceFile = sourceFile;
        this.lastUpdated = lastUpdated;
    }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(long totalRecords) { this.totalRecords = totalRecords; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public OffsetDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(OffsetDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
