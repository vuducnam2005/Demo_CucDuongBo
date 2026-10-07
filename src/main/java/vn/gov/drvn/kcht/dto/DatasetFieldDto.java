package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mô tả trường thuộc tính dữ liệu")
public class DatasetFieldDto {

    @Schema(description = "Tên trường mã hóa", example = "bridge_name")
    private String fieldName;

    @Schema(description = "Tên nhãn hiển thị tiếng Việt", example = "Tên cầu")
    private String displayName;

    @Schema(description = "Kiểu dữ liệu", example = "string")
    private String dataType;

    @Schema(description = "Cho phép tìm kiếm", example = "true")
    private boolean searchable;

    @Schema(description = "Cho phép lọc", example = "true")
    private boolean filterable;

    @Schema(description = "Đơn vị tính (nếu có)", example = "m")
    private String unit;

    public DatasetFieldDto() {
    }

    public DatasetFieldDto(String fieldName, String displayName, String dataType,
                           boolean searchable, boolean filterable, String unit) {
        this.fieldName = fieldName;
        this.displayName = displayName;
        this.dataType = dataType;
        this.searchable = searchable;
        this.filterable = filterable;
        this.unit = unit;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
