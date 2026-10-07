package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;

public class DashboardSummaryDto {

    private long totalAssets;
    private int totalDatasets;
    private int physicalAssetDatasets;
    private int moduleDatasets;
    private long totalBridges;
    private long totalRoadSigns;
    private int totalNationalRoadRoutes;
    private double totalNationalRoadLengthKm;
    private long totalDocuments;
    private String sourceDataset = "dataset_registry, tbl_bridge, tbl_road_sign, mst_national_road, document_metadata";
    private String filter = "Toàn quốc (Bộ GTVT & Cục Đường bộ Việt Nam)";
    private OffsetDateTime lastUpdated;

    public DashboardSummaryDto() {}

    public DashboardSummaryDto(long totalAssets, int totalDatasets, int physicalAssetDatasets,
                               int moduleDatasets, long totalBridges, long totalRoadSigns,
                               int totalNationalRoadRoutes, double totalNationalRoadLengthKm,
                               long totalDocuments, OffsetDateTime lastUpdated) {
        this.totalAssets = totalAssets;
        this.totalDatasets = totalDatasets;
        this.physicalAssetDatasets = physicalAssetDatasets;
        this.moduleDatasets = moduleDatasets;
        this.totalBridges = totalBridges;
        this.totalRoadSigns = totalRoadSigns;
        this.totalNationalRoadRoutes = totalNationalRoadRoutes;
        this.totalNationalRoadLengthKm = totalNationalRoadLengthKm;
        this.totalDocuments = totalDocuments;
        this.lastUpdated = lastUpdated;
    }

    public long getTotalAssets() { return totalAssets; }
    public void setTotalAssets(long totalAssets) { this.totalAssets = totalAssets; }

    public int getTotalDatasets() { return totalDatasets; }
    public void setTotalDatasets(int totalDatasets) { this.totalDatasets = totalDatasets; }

    public int getPhysicalAssetDatasets() { return physicalAssetDatasets; }
    public void setPhysicalAssetDatasets(int physicalAssetDatasets) { this.physicalAssetDatasets = physicalAssetDatasets; }

    public int getModuleDatasets() { return moduleDatasets; }
    public void setModuleDatasets(int moduleDatasets) { this.moduleDatasets = moduleDatasets; }

    public long getTotalBridges() { return totalBridges; }
    public void setTotalBridges(long totalBridges) { this.totalBridges = totalBridges; }

    public long getTotalRoadSigns() { return totalRoadSigns; }
    public void setTotalRoadSigns(long totalRoadSigns) { this.totalRoadSigns = totalRoadSigns; }

    public int getTotalNationalRoadRoutes() { return totalNationalRoadRoutes; }
    public void setTotalNationalRoadRoutes(int totalNationalRoadRoutes) { this.totalNationalRoadRoutes = totalNationalRoadRoutes; }

    public double getTotalNationalRoadLengthKm() { return totalNationalRoadLengthKm; }
    public void setTotalNationalRoadLengthKm(double totalNationalRoadLengthKm) { this.totalNationalRoadLengthKm = totalNationalRoadLengthKm; }

    public long getTotalDocuments() { return totalDocuments; }
    public void setTotalDocuments(long totalDocuments) { this.totalDocuments = totalDocuments; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public OffsetDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(OffsetDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
