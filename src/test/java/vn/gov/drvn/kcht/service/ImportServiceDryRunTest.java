package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import vn.gov.drvn.kcht.repository.ImportProgressRepository;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceDryRunTest {

    @Mock
    private DatasetRegistryService registryService;

    @Mock
    private ImportProgressRepository progressRepository;

    @Mock
    private RawDatasetRecordRepository rawRecordRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordKeyResolver keyResolver = new RecordKeyResolver();
        PayloadValidator payloadValidator = new PayloadValidator(keyResolver);

        importService = new ImportService(
                registryService,
                payloadValidator,
                progressRepository,
                rawRecordRepository,
                jdbcTemplate,
                objectMapper,
                transactionManager
        );

        ReflectionTestUtils.setField(importService, "dataDir", "C:\\Data\\kcht_json_2026-10-05");
        ReflectionTestUtils.setField(importService, "batchSize", 100);
    }


    @Test
    @DisplayName("Nghiệm thu: Chạy Dry-Run với tệp nhỏ 'assets/duonggom.json' giới hạn 5 bản ghi")
    void testDryRunSingleFile() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                new File("C:\\Data\\kcht_json_2026-10-05\\assets\\duonggom.json").exists(),
                "Thư mục dữ liệu cục bộ C:\\Data không tồn tại (CI/CD bỏ qua)");

        ImportService.ImportSummary summary = importService.importSingleFile("assets/duonggom.json", true, 5);

        assertNotNull(summary);
        assertTrue(summary.dryRun, "Phải ở chế độ DryRun");
        assertEquals(1, summary.totalFiles);
        assertEquals(1, summary.processedFiles);
        assertEquals(5, summary.totalRecordsProcessed, "Chỉ xử lý đúng 5 bản ghi giới hạn");
        assertEquals(5, summary.validRecords, "Cả 5 bản ghi phải hợp lệ");
        assertEquals(0, summary.invalidRecords);

        // Đảm bảo không ghi CSDL khi DryRun
        verifyNoInteractions(rawRecordRepository);
        verifyNoInteractions(progressRepository);
    }
}
