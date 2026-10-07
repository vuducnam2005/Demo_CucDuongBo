package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;

@Schema(description = "Tập hợp các thực thể không gian GeoJSON FeatureCollection theo RFC 7946")
public class GeoJsonFeatureCollectionDto {

    @Schema(description = "Loại đối tượng luôn là FeatureCollection", example = "FeatureCollection")
    private String type = "FeatureCollection";

    @Schema(description = "Tổng số đối tượng trong tập hợp", example = "45")
    private int totalFeatures;

    @Schema(description = "Danh sách các Feature")
    private List<GeoJsonFeatureDto> features;

    public GeoJsonFeatureCollectionDto() {
        this.features = Collections.emptyList();
    }

    public GeoJsonFeatureCollectionDto(List<GeoJsonFeatureDto> features) {
        this.type = "FeatureCollection";
        this.features = features != null ? features : Collections.emptyList();
        this.totalFeatures = this.features.size();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getTotalFeatures() {
        return totalFeatures;
    }

    public void setTotalFeatures(int totalFeatures) {
        this.totalFeatures = totalFeatures;
    }

    public List<GeoJsonFeatureDto> getFeatures() {
        return features;
    }

    public void setFeatures(List<GeoJsonFeatureDto> features) {
        this.features = features;
        this.totalFeatures = features != null ? features.size() : 0;
    }
}
