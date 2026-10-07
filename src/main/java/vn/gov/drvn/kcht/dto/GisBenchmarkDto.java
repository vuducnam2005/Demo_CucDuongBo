package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả đo kiểm hiệu năng không gian PostGIS (GIS Benchmark)")
public class GisBenchmarkDto {

    @Schema(description = "Thời điểm thực thi kiểm thử", example = "2026-10-06T02:00:00Z")
    private String benchmarkTimestamp;

    @Schema(description = "Tổng số bản ghi được đánh giá qua benchmark", example = "274308")
    private long totalRecordsEvaluated;

    @Schema(description = "Chi tiết kết quả benchmark từng tập dữ liệu")
    private List<DatasetGisBenchmarkItem> datasets;

    @Schema(description = "Đánh giá an toàn bộ nhớ và hiệu năng", example = "PASS: Đạt tiêu chuẩn không tải 222k điểm vào browser")
    private String safetyAssessment;

    @Schema(description = "Trạng thái tuân thủ quy tắc kiến trúc không tải hàng loạt", example = "true")
    private boolean complianceNoFullLoad;

    public GisBenchmarkDto() {
    }

    public GisBenchmarkDto(String benchmarkTimestamp, long totalRecordsEvaluated,
                           List<DatasetGisBenchmarkItem> datasets, String safetyAssessment,
                           boolean complianceNoFullLoad) {
        this.benchmarkTimestamp = benchmarkTimestamp;
        this.totalRecordsEvaluated = totalRecordsEvaluated;
        this.datasets = datasets;
        this.safetyAssessment = safetyAssessment;
        this.complianceNoFullLoad = complianceNoFullLoad;
    }

    public String getBenchmarkTimestamp() {
        return benchmarkTimestamp;
    }

    public void setBenchmarkTimestamp(String benchmarkTimestamp) {
        this.benchmarkTimestamp = benchmarkTimestamp;
    }

    public long getTotalRecordsEvaluated() {
        return totalRecordsEvaluated;
    }

    public void setTotalRecordsEvaluated(long totalRecordsEvaluated) {
        this.totalRecordsEvaluated = totalRecordsEvaluated;
    }

    public List<DatasetGisBenchmarkItem> getDatasets() {
        return datasets;
    }

    public void setDatasets(List<DatasetGisBenchmarkItem> datasets) {
        this.datasets = datasets;
    }

    public String getSafetyAssessment() {
        return safetyAssessment;
    }

    public void setSafetyAssessment(String safetyAssessment) {
        this.safetyAssessment = safetyAssessment;
    }

    public boolean isComplianceNoFullLoad() {
        return complianceNoFullLoad;
    }

    public void setComplianceNoFullLoad(boolean complianceNoFullLoad) {
        this.complianceNoFullLoad = complianceNoFullLoad;
    }

    public static class DatasetGisBenchmarkItem {
        private String datasetKey;
        private String datasetName;
        private long totalDatasetRecords;
        private long spatialRecordsWithCoordinates;
        private long bboxQueryExecutionTimeMs;
        private int bboxFeaturesReturned;
        private long gridClusterExecutionTimeMs;
        private int gridClustersReturned;
        private long fullLoadEstimatedSizeBytes;
        private long actualPayloadSizeBytes;
        private double compressionRatioPercent;
        private String verdict;

        public DatasetGisBenchmarkItem() {
        }

        public DatasetGisBenchmarkItem(String datasetKey, String datasetName, long totalDatasetRecords,
                                       long spatialRecordsWithCoordinates, long bboxQueryExecutionTimeMs,
                                       int bboxFeaturesReturned, long gridClusterExecutionTimeMs,
                                       int gridClustersReturned, long fullLoadEstimatedSizeBytes,
                                       long actualPayloadSizeBytes, double compressionRatioPercent,
                                       String verdict) {
            this.datasetKey = datasetKey;
            this.datasetName = datasetName;
            this.totalDatasetRecords = totalDatasetRecords;
            this.spatialRecordsWithCoordinates = spatialRecordsWithCoordinates;
            this.bboxQueryExecutionTimeMs = bboxQueryExecutionTimeMs;
            this.bboxFeaturesReturned = bboxFeaturesReturned;
            this.gridClusterExecutionTimeMs = gridClusterExecutionTimeMs;
            this.gridClustersReturned = gridClustersReturned;
            this.fullLoadEstimatedSizeBytes = fullLoadEstimatedSizeBytes;
            this.actualPayloadSizeBytes = actualPayloadSizeBytes;
            this.compressionRatioPercent = compressionRatioPercent;
            this.verdict = verdict;
        }

        public String getDatasetKey() { return datasetKey; }
        public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

        public String getDatasetName() { return datasetName; }
        public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

        public long getTotalDatasetRecords() { return totalDatasetRecords; }
        public void setTotalDatasetRecords(long totalDatasetRecords) { this.totalDatasetRecords = totalDatasetRecords; }

        public long getSpatialRecordsWithCoordinates() { return spatialRecordsWithCoordinates; }
        public void setSpatialRecordsWithCoordinates(long spatialRecordsWithCoordinates) { this.spatialRecordsWithCoordinates = spatialRecordsWithCoordinates; }

        public long getBboxQueryExecutionTimeMs() { return bboxQueryExecutionTimeMs; }
        public void setBboxQueryExecutionTimeMs(long bboxQueryExecutionTimeMs) { this.bboxQueryExecutionTimeMs = bboxQueryExecutionTimeMs; }

        public int getBboxFeaturesReturned() { return bboxFeaturesReturned; }
        public void setBboxFeaturesReturned(int bboxFeaturesReturned) { this.bboxFeaturesReturned = bboxFeaturesReturned; }

        public long getGridClusterExecutionTimeMs() { return gridClusterExecutionTimeMs; }
        public void setGridClusterExecutionTimeMs(long gridClusterExecutionTimeMs) { this.gridClusterExecutionTimeMs = gridClusterExecutionTimeMs; }

        public int getGridClustersReturned() { return gridClustersReturned; }
        public void setGridClustersReturned(int gridClustersReturned) { this.gridClustersReturned = gridClustersReturned; }

        public long getFullLoadEstimatedSizeBytes() { return fullLoadEstimatedSizeBytes; }
        public void setFullLoadEstimatedSizeBytes(long fullLoadEstimatedSizeBytes) { this.fullLoadEstimatedSizeBytes = fullLoadEstimatedSizeBytes; }

        public long getActualPayloadSizeBytes() { return actualPayloadSizeBytes; }
        public void setActualPayloadSizeBytes(long actualPayloadSizeBytes) { this.actualPayloadSizeBytes = actualPayloadSizeBytes; }

        public double getCompressionRatioPercent() { return compressionRatioPercent; }
        public void setCompressionRatioPercent(double compressionRatioPercent) { this.compressionRatioPercent = compressionRatioPercent; }

        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }
    }
}
