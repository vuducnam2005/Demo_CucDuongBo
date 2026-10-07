package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;

public class DashboardRecentAssetDto {

    private String datasetKey;
    private String datasetName;
    private String recordKey;
    private String assetName;
    private String branchId;
    private String branchName;
    private OffsetDateTime importedAt;
    private String sourceDataset;
    private String detailUrl;

    public DashboardRecentAssetDto() {}

    public DashboardRecentAssetDto(String datasetKey, String datasetName, String recordKey,
                                  String assetName, String branchId, String branchName,
                                  OffsetDateTime importedAt, String sourceDataset, String detailUrl) {
        this.datasetKey = datasetKey;
        this.datasetName = datasetName;
        this.recordKey = recordKey;
        this.assetName = assetName;
        this.branchId = branchId;
        this.branchName = branchName;
        this.importedAt = importedAt;
        this.sourceDataset = sourceDataset;
        this.detailUrl = detailUrl;
    }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }

    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public OffsetDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(OffsetDateTime importedAt) { this.importedAt = importedAt; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getDetailUrl() { return detailUrl; }
    public void setDetailUrl(String detailUrl) { this.detailUrl = detailUrl; }
}
