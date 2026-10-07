package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "import_error")
public class ImportErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImportJobEntity job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    private ImportFileEntity file;

    @Column(name = "dataset_key", length = 100)
    private String datasetKey;

    @Column(name = "record_key", length = 150)
    private String recordKey;

    @Column(name = "error_stage", nullable = false, length = 50)
    private String errorStage;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "raw_fragment", columnDefinition = "JSONB")
    private String rawFragment;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public ImportErrorEntity() {}

    public ImportErrorEntity(ImportJobEntity job, ImportFileEntity file, String datasetKey,
                             String recordKey, String errorStage, String errorCode,
                             String errorMessage, String rawFragment) {
        this.job = job;
        this.file = file;
        this.datasetKey = datasetKey;
        this.recordKey = recordKey;
        this.errorStage = errorStage;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.rawFragment = rawFragment;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ImportJobEntity getJob() { return job; }
    public void setJob(ImportJobEntity job) { this.job = job; }

    public ImportFileEntity getFile() { return file; }
    public void setFile(ImportFileEntity file) { this.file = file; }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }

    public String getErrorStage() { return errorStage; }
    public void setErrorStage(String errorStage) { this.errorStage = errorStage; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getRawFragment() { return rawFragment; }
    public void setRawFragment(String rawFragment) { this.rawFragment = rawFragment; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
