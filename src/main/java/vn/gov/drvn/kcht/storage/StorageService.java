package vn.gov.drvn.kcht.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Interface trừu tượng hóa dịch vụ lưu trữ tệp tin (Object Storage).
 * Mặc định sử dụng LocalStorageService trên môi trường local,
 * và dễ dàng chuyển sang MinIO / S3 Storage qua cấu hình.
 */
public interface StorageService {

    /**
     * Lưu trữ tệp tin nhị phân.
     *
     * @param objectKey Khóa định danh tệp (đã băm bằng UUID)
     * @param inputStream Dòng dữ liệu nhị phân
     * @param size Kích thước tệp (bytes)
     * @param contentType MIME type của tệp
     * @return Khóa định danh hoặc đường dẫn lưu trữ
     */
    String uploadFile(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * Tải tệp tin nhị phân dưới dạng InputStream.
     *
     * @param objectKey Khóa định danh tệp
     * @return Optional InputStream
     */
    Optional<InputStream> downloadFile(String objectKey);

    /**
     * Sinh Pre-signed URL hoặc đường dẫn tải tệp an toàn có thời hạn.
     *
     * @param objectKey Khóa định danh tệp
     * @param durationMinutes Thời hạn hiệu lực tính bằng phút
     * @return URL truy cập tạm thời
     */
    String generateDownloadUrl(String objectKey, int durationMinutes);

    /**
     * Kiểm tra sự tồn tại của tệp tin.
     *
     * @param objectKey Khóa định danh tệp
     * @return true nếu tồn tại
     */
    boolean exists(String objectKey);

    /**
     * Xóa tệp tin khỏi vùng lưu trữ.
     *
     * @param objectKey Khóa định danh tệp
     * @return true nếu xóa thành công
     */
    boolean deleteFile(String objectKey);
}
