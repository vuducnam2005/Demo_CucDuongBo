package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "import_file", uniqueConstraints = {
    @UniqueConstraint(name = "uq_import_file_job_path", columnNames = {"job_id", "file_path"})
})
public class ImportFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImportJobEntity job;

    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;

    @Column(name = "file_bytes", nullable = false)
    private Long fileBytes;

    @Column(name = "source_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceSha256;

    @Column(name = "record_count")
    private Integer recordCount = 0;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    public ImportFileEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ImportJobEntity getJob() { return job; }
    public void setJob(ImportJobEntity job) { this.job = job; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileBytes() { return fileBytes; }
    public void setFileBytes(Long fileBytes) { this.fileBytes = fileBytes; }

    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String sourceSha256) { this.sourceSha256 = sourceSha256; }

    public Integer getRecordCount() { return recordCount; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
