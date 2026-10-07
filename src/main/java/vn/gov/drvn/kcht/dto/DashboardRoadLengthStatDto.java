package vn.gov.drvn.kcht.dto;

import java.time.OffsetDateTime;
import java.util.List;

public class DashboardRoadLengthStatDto {

    private int totalRoutes;
    private double totalLengthKm;
    private double averageLengthKm;
    private List<RouteLengthDto> longestRoutes;
    private List<LengthDistributionDto> distribution;
    private String sourceDataset = "mst_national_road (data_->actual_length)";
    private String filter = "Toàn bộ 168 tuyến Quốc lộ chính";
    private OffsetDateTime lastUpdated;

    public DashboardRoadLengthStatDto() {}

    public DashboardRoadLengthStatDto(int totalRoutes, double totalLengthKm, double averageLengthKm,
                                     List<RouteLengthDto> longestRoutes, List<LengthDistributionDto> distribution,
                                     OffsetDateTime lastUpdated) {
        this.totalRoutes = totalRoutes;
        this.totalLengthKm = totalLengthKm;
        this.averageLengthKm = averageLengthKm;
        this.longestRoutes = longestRoutes;
        this.distribution = distribution;
        this.lastUpdated = lastUpdated;
    }

    public int getTotalRoutes() { return totalRoutes; }
    public void setTotalRoutes(int totalRoutes) { this.totalRoutes = totalRoutes; }

    public double getTotalLengthKm() { return totalLengthKm; }
    public void setTotalLengthKm(double totalLengthKm) { this.totalLengthKm = totalLengthKm; }

    public double getAverageLengthKm() { return averageLengthKm; }
    public void setAverageLengthKm(double averageLengthKm) { this.averageLengthKm = averageLengthKm; }

    public List<RouteLengthDto> getLongestRoutes() { return longestRoutes; }
    public void setLongestRoutes(List<RouteLengthDto> longestRoutes) { this.longestRoutes = longestRoutes; }

    public List<LengthDistributionDto> getDistribution() { return distribution; }
    public void setDistribution(List<LengthDistributionDto> distribution) { this.distribution = distribution; }

    public String getSourceDataset() { return sourceDataset; }
    public void setSourceDataset(String sourceDataset) { this.sourceDataset = sourceDataset; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public OffsetDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(OffsetDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public static class RouteLengthDto {
        private String routeCode;
        private String routeName;
        private double lengthKm;

        public RouteLengthDto() {}
        public RouteLengthDto(String routeCode, String routeName, double lengthKm) {
            this.routeCode = routeCode;
            this.routeName = routeName;
            this.lengthKm = lengthKm;
        }

        public String getRouteCode() { return routeCode; }
        public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

        public String getRouteName() { return routeName; }
        public void setRouteName(String routeName) { this.routeName = routeName; }

        public double getLengthKm() { return lengthKm; }
        public void setLengthKm(double lengthKm) { this.lengthKm = lengthKm; }
    }

    public static class LengthDistributionDto {
        private String rangeLabel;
        private int routeCount;
        private double totalKm;

        public LengthDistributionDto() {}
        public LengthDistributionDto(String rangeLabel, int routeCount, double totalKm) {
            this.rangeLabel = rangeLabel;
            this.routeCount = routeCount;
            this.totalKm = totalKm;
        }

        public String getRangeLabel() { return rangeLabel; }
        public void setRangeLabel(String rangeLabel) { this.rangeLabel = rangeLabel; }

        public int getRouteCount() { return routeCount; }
        public void setRouteCount(int routeCount) { this.routeCount = routeCount; }

        public double getTotalKm() { return totalKm; }
        public void setTotalKm(double totalKm) { this.totalKm = totalKm; }
    }
}
