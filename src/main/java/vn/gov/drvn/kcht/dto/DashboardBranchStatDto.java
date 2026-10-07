package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;

public class DashboardBranchStatDto {

    private String branchId;
    private String branchName;
    private long totalAssets;
    private long bridgeCount;
    private long roadSignCount;
    private long nationalRoadCount;
    private String sourceDataset = "mv_dashboard_branch_stats";
    private String filter = "Theo Đơn vị / Chi nhánh quản lý";
    private OffsetDateTime lastUpdated;

    public DashboardBranchStatDto() {}

    public DashboardBranchStatDto(String branchId, String branchName, long totalAssets,
                                 long bridgeCount, long roadSignCount, long nationalRoadCount,
                                 OffsetDateTime lastUpdated) {
        this.branchId = branchId;
        this.branchName = branchName;
        this.totalAssets = totalAssets;
        this.bridgeCount = bridgeCount;
        this.roadSignCount = roadSignCount;
        this.nationalRoadCount = nationalRoadCount;
        this.lastUpdated = lastUpdated;
    }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public long getTotalAssets() { return totalAssets; }
    public void setTotalAssets(long totalAssets) { this.totalAssets = totalAssets; }

    public long getBridgeCount() { return bridgeCount; }
    public void setBridgeCount(long bridgeCount) { this.bridgeCount = bridgeCount; }

    public long getRoadSignCount() { return roadSignCount; }
    public void setRoadSignCount(long roadSignCount) { this.roadSignCount = roadSignCount; }

    public long getNationalRoadCount() { return nationalRoadCount; }
    public void setNationalRoadCount(long nationalRoadCount) { this.nationalRoadCount = nationalRoadCount; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public OffsetDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(OffsetDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
