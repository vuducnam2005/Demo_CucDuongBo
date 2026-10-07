package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Đối tượng Feature GeoJSON đại diện cho một thực thể địa lý")
public class GeoJsonFeatureDto {

    @Schema(description = "Loại đối tượng luôn là Feature", example = "Feature")
    private String type = "Feature";

    @Schema(description = "Định danh đối tượng", example = "17786806577720671")
    private String id;

    @Schema(description = "Hình học đối tượng")
    private GeoJsonGeometryDto geometry;

    @Schema(description = "Thuộc tính phi không gian của đối tượng")
    private Map<String, Object> properties;

    public GeoJsonFeatureDto() {
    }

    public GeoJsonFeatureDto(String id, GeoJsonGeometryDto geometry, Map<String, Object> properties) {
        this.type = "Feature";
        this.id = id;
        this.geometry = geometry;
        this.properties = properties;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public GeoJsonGeometryDto getGeometry() {
        return geometry;
    }

    public void setGeometry(GeoJsonGeometryDto geometry) {
        this.geometry = geometry;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
