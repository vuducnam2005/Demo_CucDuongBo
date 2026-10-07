package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dataset_registry")
public class DatasetRegistryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_key", nullable = false, unique = true, length = 100)
    private String datasetKey;

    @Column(name = "dataset_name", nullable = false, length = 255)
    private String datasetName;

    @Column(name = "kind", nullable = false, length = 50)
    private String kind;

    @Column(name = "endpoint", length = 255)
    private String endpoint;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @Column(name = "total_records")
    private Integer totalRecords = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DatasetFieldEntity> fields = new ArrayList<>();

    public DatasetRegistryEntity() {}

    public DatasetRegistryEntity(String datasetKey, String datasetName, String kind,
                                 String endpoint, String sourceFile, Integer totalRecords) {
        this.datasetKey = datasetKey;
        this.datasetName = datasetName;
        this.kind = kind;
        this.endpoint = endpoint;
        this.sourceFile = sourceFile;
        this.totalRecords = totalRecords != null ? totalRecords : 0;
        this.isActive = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDatasetKey() { return datasetKey; }
    public void setDatasetKey(String datasetKey) { this.datasetKey = datasetKey; }

    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DatasetFieldEntity> getFields() { return fields; }
    public void setFields(List<DatasetFieldEntity> fields) { this.fields = fields; }
}
