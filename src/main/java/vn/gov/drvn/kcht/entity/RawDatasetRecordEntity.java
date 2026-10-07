package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "raw_dataset_record", uniqueConstraints = {
    @UniqueConstraint(name = "uq_raw_dataset_record", columnNames = {"dataset_key", "record_key"})
})
public class RawDatasetRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_key", nullable = false, length = 100)
    private String datasetKey;

    @Column(name = "record_key", nullable = false, length = 150)
    private String recordKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "JSONB")
    private String rawPayload;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @Column(name = "source_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceSha256;

    @Column(name = "payload_sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String payloadSha256;

    @Column(name = "import_job_id")
    private Long importJobId;

    @Column(name = "record_status", nullable = false, length = 50)
    private String recordStatus = "RAW_STORED";

    @Column(name = "imported_at")
    private OffsetDateTime importedAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public RawDatasetRecordEntity() {}

    public RawDatasetRecordEntity(String datasetKey, String recordKey, String rawPayload,
                                  String sourceFile, String sourceSha256, String payloadSha256,
                                  Long importJobId, String recordStatus) {
        this.datasetKey = datasetKey;
        this.recordKey = recordKey;
        this.rawPayload = rawPayload;
        this.sourceFile = sourceFile;
        this.sourceSha256 = sourceSha256;
        this.payloadSha256 = payloadSha256;
        this.importJobId = importJobId;
        this.recordStatus = recordStatus != null ? recordStatus : "RAW_STORED";
        this.importedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getRecordKey() { return recordKey; }
    public void setRecordKey(String recordKey) { this.recordKey = recordKey; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String sourceSha256) { this.sourceSha256 = sourceSha256; }

    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }

    public Long getImportJobId() { return importJobId; }
    public void setImportJobId(Long importJobId) { this.importJobId = importJobId; }

    public String getRecordStatus() { return recordStatus; }
    public void setRecordStatus(String recordStatus) { this.recordStatus = recordStatus; }

    public OffsetDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(OffsetDateTime importedAt) { this.importedAt = importedAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
