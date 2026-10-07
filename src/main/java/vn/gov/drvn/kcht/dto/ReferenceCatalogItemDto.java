package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Mục danh mục tham chiếu chuẩn hóa")
public class ReferenceCatalogItemDto {

    @Schema(description = "Mã danh mục", example = "reference_moc_dbvn_c_tinhthanhpho")
    private String catalogCode;

    @Schema(description = "Mã mục danh mục", example = "01")
    private String itemCode;

    @Schema(description = "Tên mục danh mục", example = "Thành phố Hà Nội")
    private String itemName;

    @Schema(description = "Mã mục cha (nếu có)", example = "V1")
    private String parentCode;

    @Schema(description = "Thứ tự sắp xếp", example = "1")
    private Integer sortOrder;

    @Schema(description = "Trạng thái sử dụng", example = "true")
    private Boolean active;

    @Schema(description = "Thuộc tính mở rộng khác")
    private Map<String, Object> extraAttributes;

    public ReferenceCatalogItemDto() {
    }

    public ReferenceCatalogItemDto(String catalogCode, String itemCode, String itemName,
                                  String parentCode, Integer sortOrder, Boolean active,
                                  Map<String, Object> extraAttributes) {
        this.catalogCode = catalogCode;
        this.itemCode = itemCode;
        this.itemName = itemName;
        this.parentCode = parentCode;
        this.sortOrder = sortOrder;
        this.active = active;
        this.extraAttributes = extraAttributes;
    }

    public String getCatalogCode() {
        return catalogCode;
    }

    public void setCatalogCode(String catalogCode) {
        this.catalogCode = catalogCode;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Map<String, Object> getExtraAttributes() {
        return extraAttributes;
    }

    public void setExtraAttributes(Map<String, Object> extraAttributes) {
        this.extraAttributes = extraAttributes;
    }
}
