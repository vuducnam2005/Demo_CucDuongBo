package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.gov.drvn.kcht.entity.ImportFileEntity;
import vn.gov.drvn.kcht.entity.ImportJobEntity;
import vn.gov.drvn.kcht.repository.ImportProgressRepository;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.io.File;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchRollbackTest {

    @Mock
    private DatasetRegistryService registryService;

    @Mock
    private ImportProgressRepository progressRepository;

    @Mock
    private RawDatasetRecordRepository rawRecordRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ImportService importService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        RecordKeyResolver keyResolver = new RecordKeyResolver();
        PayloadValidator payloadValidator = new PayloadValidator(keyResolver);

        TransactionStatus status = new SimpleTransactionStatus();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);

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
        ReflectionTestUtils.setField(importService, "batchSize", 5);
    }

    @Test
    @DisplayName("Kiểm tra Rollback và ghi nhận lỗi khi một Batch bị lỗi cơ sở dữ liệu")
    void testRollbackOnBatchFailure() {
        ImportJobEntity job = new ImportJobEntity("TEST_JOB");
        job.setId(100L);

        when(progressRepository.createJob(anyString(), anyInt(), anyInt())).thenReturn(job);

        ImportFileEntity fileProgress = new ImportFileEntity();
        fileProgress.setId(200L);
        fileProgress.setJob(job);
        fileProgress.setFilePath("assets/duonggom.json");


        when(progressRepository.findFileProgress(anyLong(), anyString())).thenReturn(Optional.empty());
        when(progressRepository.registerOrGetFile(any(), anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(fileProgress);

        // Giả lập lỗi SQLException / DataAccessException khi thực hiện batchUpdate trong CSDL
        doThrow(new org.springframework.dao.DataIntegrityViolationException("Database constraint violation on batch commit"))
                .when(jdbcTemplate).batchUpdate(anyString(), anyList(), anyInt(), any());

        // Thực thi import và kiểm tra ngoại lệ được ném ra để rollback transaction
        Exception exception = assertThrows(Exception.class, () -> {
            importService.importSingleFile("assets/duonggom.json", false, 10);
        });

        assertTrue(exception.getMessage().contains("Database constraint violation"));

        // Xác nhận giao dịch bị rollback và file được đánh dấu FAILED trong bảng import_file
        verify(progressRepository, times(1)).markFileFailed(eq(fileProgress), anyString());
        verify(progressRepository, never()).markFileCompleted(any(), anyInt());
    }
}
