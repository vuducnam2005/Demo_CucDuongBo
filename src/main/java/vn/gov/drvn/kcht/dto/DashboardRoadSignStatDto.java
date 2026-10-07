package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class DashboardRoadSignStatDto {

    private long totalSigns;
    private List<BranchSignCountDto> byBranch;
    private List<SignCategoryDto> byShape;
    private String sourceDataset = "tbl_road_sign";
    private String filter = "Toàn quốc (Theo QCVN 41:2019/BGTVT)";
    private OffsetDateTime lastUpdated;

    public DashboardRoadSignStatDto() {}

    public DashboardRoadSignStatDto(long totalSigns, List<BranchSignCountDto> byBranch,
                                    List<SignCategoryDto> byShape, OffsetDateTime lastUpdated) {
        this.totalSigns = totalSigns;
        this.byBranch = byBranch;
        this.byShape = byShape;
        this.lastUpdated = lastUpdated;
    }

    public long getTotalSigns() { return totalSigns; }
    public void setTotalSigns(long totalSigns) { this.totalSigns = totalSigns; }

    public List<BranchSignCountDto> getByBranch() { return byBranch; }
    public void setByBranch(List<BranchSignCountDto> byBranch) { this.byBranch = byBranch; }

    public List<SignCategoryDto> getByShape() { return byShape; }
    public void setByShape(List<SignCategoryDto> byShape) { this.byShape = byShape; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public OffsetDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(OffsetDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public static class BranchSignCountDto {
        private String branchId;
        private String branchName;
        private long count;
        private double percentage;

        public BranchSignCountDto() {}
        public BranchSignCountDto(String branchId, String branchName, long count, double percentage) {
            this.branchId = branchId;
            this.branchName = branchName;
            this.count = count;
            this.percentage = percentage;
        }

        public String getBranchId() { return branchId; }
        public void setBranchId(String branchId) { this.branchId = branchId; }

        public String getBranchName() { return branchName; }
        public void setBranchName(String branchName) { this.branchName = branchName; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }

        public double getPercentage() { return percentage; }
        public void setPercentage(double percentage) { this.percentage = percentage; }
    }

    public static class SignCategoryDto {
        private String category;
        private long count;

        public SignCategoryDto() {}
        public SignCategoryDto(String category, long count) {
            this.category = category;
            this.count = count;
        }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
