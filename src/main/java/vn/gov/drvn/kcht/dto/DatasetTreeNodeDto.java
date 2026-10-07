package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "Node trong cây phân cấp tài sản KCHT đường bộ")
public class DatasetTreeNodeDto {

    @Schema(description = "Mã định danh duy nhất của node", example = "group_bridges")
    private String key;

    @Schema(description = "Tiêu đề hiển thị tiếng Việt", example = "Cầu & Kết cấu vượt")
    private String title;

    @Schema(description = "Mã dataset tương ứng (nếu là leaf node)", example = "tbl_bridge")
    private String datasetKey;

    @Schema(description = "Tổng số bản ghi (nếu là leaf node hoặc tổng hợp)", example = "11631")
    private Long totalRecords;

    @Schema(description = "Phân loại nhóm hoặc dataset", example = "asset")
    private String kind;

    @Schema(description = "Biểu tượng gợi ý", example = "BuildOutlined")
    private String icon;

    @com.fasterxml.jackson.annotation.JsonProperty("isLeaf")
    @Schema(description = "Đánh dấu là node lá (dataset cụ thể)", example = "true")
    private boolean isLeaf;

    @Schema(description = "Danh sách node con")
    private List<DatasetTreeNodeDto> children;

    public DatasetTreeNodeDto() {
        this.children = new ArrayList<>();
    }

    public DatasetTreeNodeDto(String key, String title, String datasetKey, Long totalRecords,
                              String kind, String icon, boolean isLeaf) {
        this.key = key;
        this.title = title;
        this.datasetKey = datasetKey;
        this.totalRecords = totalRecords;
        this.kind = kind;
        this.icon = icon;
        this.isLeaf = isLeaf;
        this.children = new ArrayList<>();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDatasetKey() {
        return datasetKey;
    }

    public void setDatasetKey(String datasetKey) {
        this.datasetKey = datasetKey;
    }

    public Long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isLeaf")
    public boolean isLeaf() {
        return isLeaf;
    }

    public void setLeaf(boolean leaf) {
        isLeaf = leaf;
    }

    public List<DatasetTreeNodeDto> getChildren() {
        return children;
    }

    public void setChildren(List<DatasetTreeNodeDto> children) {
        this.children = children;
    }
}
