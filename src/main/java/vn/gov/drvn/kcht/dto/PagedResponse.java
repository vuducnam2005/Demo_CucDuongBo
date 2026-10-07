package vn.gov.drvn.kcht.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;

@Schema(description = "Phản hồi phân trang chuẩn hóa hệ thống")
public class PagedResponse<T> {

    @Schema(description = "Danh sách bản ghi của trang hiện tại")
    private List<T> content;

    @Schema(description = "Chỉ số trang (0-indexed)", example = "0")
    private int page;

    @Schema(description = "Kích thước mỗi trang", example = "20")
    private int size;

    @Schema(description = "Tổng số lượng bản ghi thỏa mãn điều kiện", example = "11631")
    private long totalElements;

    @Schema(description = "Tổng số trang", example = "582")
    private int totalPages;

    @Schema(description = "Có phải trang đầu tiên không", example = "true")
    private boolean first;

    @Schema(description = "Có phải trang cuối cùng không", example = "false")
    private boolean last;

    public PagedResponse() {
        this.content = Collections.emptyList();
    }

    public PagedResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        this.first = page == 0;
        this.last = page >= (this.totalPages - 1);
    }

    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean first, boolean last) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
    }

    public static <T> PagedResponse<T> of(org.springframework.data.domain.Page<T> page) {
        if (page == null) {
            return new PagedResponse<>();
        }
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }
}
