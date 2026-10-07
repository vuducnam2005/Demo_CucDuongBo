package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Đối tượng hình học GeoJSON theo RFC 7946")
public class GeoJsonGeometryDto {

    @Schema(description = "Kiểu hình học", example = "Point")
    private String type;

    @Schema(description = "Tọa độ địa lý [Kinh độ, Vĩ độ] hoặc mảng tọa độ", example = "[105.8342, 21.0278]")
    private Object coordinates;

    public GeoJsonGeometryDto() {
    }

    public GeoJsonGeometryDto(String type, Object coordinates) {
        this.type = type;
        this.coordinates = coordinates;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Object coordinates) {
        this.coordinates = coordinates;
    }
}
