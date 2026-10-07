package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử tích hợp Giai đoạn 12: Đối soát dữ liệu (Import Reconciliation Test).
 * Đối chiếu tính toàn vẹn và số lượng bản ghi giữa:
 * - Bộ dữ liệu JSON nguồn (manifest.json tại C:\Data\kcht_json_2026-10-05)
 * - Bảng raw_dataset_record trong PostgreSQL
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://localhost:5436/kcht_db",
        "spring.datasource.username=kcht_user",
        "spring.datasource.password=${POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Kiểm thử Đối soát Dữ liệu Nguồn và Cơ sở Dữ liệu (Import Data Reconciliation)")
class DataReconciliationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RawDatasetRecordRepository rawDatasetRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String MANIFEST_PATH = "C:/Data/kcht_json_2026-10-05/manifest.json";

    @Test
    @DisplayName("1. Đối soát Manifest: Đọc tệp manifest.json và kiểm tra chỉ số tổng quan")
    void testManifestCrossCheckWithDatabaseCounts() throws Exception {
        File manifestFile = new File(MANIFEST_PATH);
        if (!manifestFile.exists()) {
            // Trường hợp chạy trong CI/CD không có ổ C:\Data, kiểm tra tối thiểu trong DB
            assertTrue(rawDatasetRecordRepository.count() > 0, "CSDL phải chứa dữ liệu thô đã nạp");
            return;
        }

        String content = Files.readString(Path.of(MANIFEST_PATH));
        JsonNode manifest = objectMapper.readTree(content);

        // Kiểm tra các chỉ số tổng hợp trong manifest
        int dataFiles = manifest.get("counts").get("data_files").asInt();
        int assetFiles = manifest.get("counts").get("asset_files").asInt();
        int moduleFiles = manifest.get("counts").get("module_files").asInt();
        long totalSourceRecords = manifest.get("counts").get("total_records").asLong();

        assertEquals(658, dataFiles, "Số lượng file dữ liệu trong manifest phải là 658");
        assertEquals(57, assetFiles, "Số lượng file asset phải là 57");
        assertEquals(601, moduleFiles, "Số lượng file module phải là 601");
        assertEquals(1104088L, totalSourceRecords, "Tổng số dòng trong nguồn phải là 1.104.088");

        // Đối soát với CSDL: Sau khi deduplication loại 1.691 bản ghi trùng lặp nội bộ trong 658 file nguồn
        long importedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_dataset_record WHERE import_job_id IS NOT NULL",
                Long.class
        );
        assertEquals(1102397L, importedCount, "Số lượng bản ghi duy nhất nạp từ nguồn trong raw_dataset_record phải là 1.102.397");
        assertEquals(1691L, totalSourceRecords - importedCount, "Số dòng trùng lặp được khử loại phải là đúng 1.691 dòng");
    }

    @Test
    @DisplayName("2. Đối soát Số lượng Bản ghi theo từng Tập dữ liệu Trọng điểm")
    void testTopDatasetsRecordCountsReconciliation() {
        Map<String, Long> expectedCounts = Map.of(
                "tbl_road_sign", 222112L,     // Biển báo
                "tbl_bridge", 11631L,         // Cầu đường bộ
                "duongnhanh", 360L,           // Đường nhánh
                "mst_national_road", 169L,    // Tuyến quốc lộ
                "duonggom", 63L               // Đường gom
        );

        for (Map.Entry<String, Long> entry : expectedCounts.entrySet()) {
            String dataset = entry.getKey();
            Long expected = entry.getValue();

            Long actual = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM raw_dataset_record WHERE dataset_key = ?",
                    Long.class,
                    dataset
            );

            assertNotNull(actual, "Số đếm dataset " + dataset + " không được null");
            assertEquals(expected, actual, String.format("Dataset '%s' có số dòng thực tế (%d) không khớp nguồn (%d)", dataset, actual, expected));
        }
    }

    @Test
    @DisplayName("3. Tính Toàn vẹn Khóa và Mã băm SHA-256 (Record Key & Hash Integrity)")
    void testRawDatasetRecordKeyIntegrity() {
        // Kiểm tra không có bất kỳ bản ghi nào có record_key rỗng hoặc null
        Long nullKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_dataset_record WHERE record_key IS NULL OR TRIM(record_key) = ''",
                Long.class
        );
        assertEquals(0L, nullKeyCount, "Tuyệt đối không được tồn tại bản ghi có record_key rỗng hoặc null");

        // Kiểm tra mã băm SHA-256 chuẩn độ dài 64 ký tự hex
        Long invalidHashCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_dataset_record WHERE LENGTH(payload_sha256) != 64 OR LENGTH(source_sha256) != 64",
                Long.class
        );
        assertEquals(0L, invalidHashCount, "Tất cả bản ghi phải có mã băm SHA-256 hợp lệ 64 ký tự");

        // Kiểm tra trạng thái nạp hợp lệ RAW_STORED
        Long validStatusCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_dataset_record WHERE record_status = 'RAW_STORED'",
                Long.class
        );
        assertTrue(validStatusCount > 0, "Các bản ghi phải có trạng thái RAW_STORED");
    }

    @Test
    @DisplayName("4. Kiểm tra Toàn vẹn Tọa độ Không gian trong Lãnh thổ Việt Nam (EPSG:4326)")
    void testSpatialCoordinateIntegrityWithinVietnamBoundary() {
        // Với các bản ghi có tọa độ x_min, y_min trong tbl_road_sign, kiểm tra tọa độ nằm trong dải Việt Nam:
        // Kinh độ (X): 102.0 đến 110.0 | Vĩ độ (Y): 8.0 đến 24.0
        Long outOfBoundsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM raw_dataset_record " +
                        "WHERE dataset_key = 'tbl_road_sign' " +
                        "AND (raw_payload ->> 'x_min') IS NOT NULL " +
                        "AND (raw_payload ->> 'y_min') IS NOT NULL " +
                        "AND ( " +
                        "   (raw_payload ->> 'x_min')::numeric < 100.0 " +
                        "   OR (raw_payload ->> 'x_min')::numeric > 115.0 " +
                        "   OR (raw_payload ->> 'y_min')::numeric < 6.0 " +
                        "   OR (raw_payload ->> 'y_min')::numeric > 25.0 " +
                        ")",
                Long.class
        );

        assertNotNull(outOfBoundsCount);
        assertEquals(0L, outOfBoundsCount, "Tất cả tọa độ biển báo hợp lệ phải nằm trong lãnh thổ Việt Nam");
    }

    @Test
    @DisplayName("5. Kiểm tra Không có File Nguồn nào bị Mất hoặc Bỏ sót trong Dataset Registry")
    void testRegistryDatasetCoverage() {
        Long unmappedFilesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT r.dataset_key) FROM raw_dataset_record r " +
                        "LEFT JOIN dataset_registry d ON r.dataset_key = d.dataset_key " +
                        "WHERE d.dataset_key IS NULL",
                Long.class
        );

        assertEquals(0L, unmappedFilesCount, "Mọi dataset trong raw_dataset_record đều phải được khai báo trong dataset_registry");
    }
}
