package vn.gov.drvn.kcht.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.entity.ImportErrorEntity;
import vn.gov.drvn.kcht.entity.ImportFileEntity;
import vn.gov.drvn.kcht.entity.ImportJobEntity;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class ImportProgressRepository {

    private final ImportJobRepository jobRepository;
    private final ImportFileRepository fileRepository;
    private final ImportErrorRepository errorRepository;

    public ImportProgressRepository(ImportJobRepository jobRepository,
                                    ImportFileRepository fileRepository,
                                    ImportErrorRepository errorRepository) {
        this.jobRepository = jobRepository;
        this.fileRepository = fileRepository;
        this.errorRepository = errorRepository;
    }

    @Transactional
    public ImportJobEntity createJob(String jobName, int totalFiles, int totalRecords) {
        ImportJobEntity job = new ImportJobEntity(jobName);
        job.setTotalFiles(totalFiles);
        job.setTotalRecords(totalRecords);
        job.setStatus("RUNNING");
        job.setStartedAt(OffsetDateTime.now());
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<ImportJobEntity> findActiveJob() {
        return jobRepository.findFirstByStatusOrderByStartedAtDesc("RUNNING");
    }

    @Transactional(readOnly = true)
    public Optional<ImportFileEntity> findFileProgress(Long jobId, String filePath) {
        return fileRepository.findByJobIdAndFilePath(jobId, filePath);
    }

    @Transactional
    public ImportFileEntity registerOrGetFile(ImportJobEntity job, String filePath, long fileBytes, String sha256, int expectedRecords) {
        return fileRepository.findByJobIdAndFilePath(job.getId(), filePath)
            .orElseGet(() -> {
                ImportFileEntity file = new ImportFileEntity();
                file.setJob(job);
                file.setFilePath(filePath);
                file.setFileBytes(fileBytes);
                file.setSourceSha256(sha256);
                file.setRecordCount(expectedRecords);
                file.setStatus("PENDING");
                return fileRepository.save(file);
            });
    }

    @Transactional
    public void markFileProcessing(ImportFileEntity file) {
        file.setStatus("PROCESSING");
        file.setStartedAt(OffsetDateTime.now());
        fileRepository.save(file);
    }

    @Transactional
    public void markFileCompleted(ImportFileEntity file, int recordsImported) {
        file.setStatus("COMPLETED");
        file.setCompletedAt(OffsetDateTime.now());
        fileRepository.save(file);

        ImportJobEntity job = file.getJob();
        if (job != null) {
            job.setProcessedFiles(job.getProcessedFiles() + 1);
            job.setSuccessRecords(job.getSuccessRecords() + recordsImported);
            jobRepository.save(job);
        }
    }

    @Transactional
    public void markFileFailed(ImportFileEntity file, String errorMessage) {
        file.setStatus("FAILED");
        file.setErrorMessage(errorMessage);
        file.setCompletedAt(OffsetDateTime.now());
        fileRepository.save(file);

        ImportJobEntity job = file.getJob();
        if (job != null) {
            job.setProcessedFiles(job.getProcessedFiles() + 1);
            job.setErrorRecords(job.getErrorRecords() + 1);
            jobRepository.save(job);
        }
    }

    @Transactional
    public void recordError(ImportJobEntity job, ImportFileEntity file, String datasetKey,
                            String recordKey, String errorStage, String errorCode,
                            String errorMessage, String rawFragment) {
        ImportErrorEntity error = new ImportErrorEntity(job, file, datasetKey, recordKey,
                errorStage, errorCode, errorMessage, rawFragment);
        errorRepository.save(error);

        if (job != null) {
            job.setErrorRecords(job.getErrorRecords() + 1);
            jobRepository.save(job);
        }
    }

    @Transactional
    public void completeJob(ImportJobEntity job, String status) {
        job.setStatus(status);
        job.setCompletedAt(OffsetDateTime.now());
        jobRepository.save(job);
    }
}
