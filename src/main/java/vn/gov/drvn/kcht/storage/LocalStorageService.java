package vn.gov.drvn.kcht.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Implementation lưu trữ tệp cục bộ đơn giản cho môi trường local development,
 * lưu trữ tệp trong thư mục chỉ định và sẵn sàng chuyển tiếp sang MinIO/S3.
 */
@Service
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path rootLocation;

    public LocalStorageService(@Value("${kcht.storage.local-path:./data/storage}") String storagePath) {
        this.rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Khởi tạo Local Storage Service tại thư mục: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("Không thể khởi tạo thư mục lưu trữ cục bộ: {}", storagePath, e);
        }
    }

    @Override
    public String uploadFile(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            Path destinationFile = this.rootLocation.resolve(objectKey).normalize();
            if (!destinationFile.getParent().equals(this.rootLocation)) {
                Files.createDirectories(destinationFile.getParent());
            }
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Lưu tệp thành công: {} ({} bytes, MIME: {})", objectKey, size, contentType);
            return objectKey;
        } catch (IOException e) {
            log.error("Lỗi khi lưu trữ tệp cục bộ với key: {}", objectKey, e);
            throw new RuntimeException("Lỗi lưu trữ tệp tin", e);
        }
    }

    @Override
    public Optional<InputStream> downloadFile(String objectKey) {
        try {
            Path filePath = this.rootLocation.resolve(objectKey).normalize();
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                return Optional.of(Files.newInputStream(filePath));
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("Lỗi khi đọc tệp cục bộ: {}", objectKey, e);
            return Optional.empty();
        }
    }

    @Override
    public String generateDownloadUrl(String objectKey, int durationMinutes) {
        // Trong môi trường local, trả về endpoint tải tệp qua API
        return "/api/v1/documents/files/" + objectKey + "/content";
    }

    @Override
    public boolean exists(String objectKey) {
        Path filePath = this.rootLocation.resolve(objectKey).normalize();
        return Files.exists(filePath);
    }

    @Override
    public boolean deleteFile(String objectKey) {
        try {
            Path filePath = this.rootLocation.resolve(objectKey).normalize();
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Lỗi khi xóa tệp cục bộ: {}", objectKey, e);
            return false;
        }
    }
}
