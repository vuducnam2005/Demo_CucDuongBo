package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dataset_field", uniqueConstraints = {
    @UniqueConstraint(name = "uq_dataset_field", columnNames = {"dataset_id", "field_name"})
})
public class DatasetFieldEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetRegistryEntity dataset;

    @Column(name = "field_name", nullable = false, length = 150)
    private String fieldName;

    @Column(name = "field_alias", length = 255)
    private String fieldAlias;

    @Column(name = "data_type", length = 50)
    private String dataType = "varchar";

    @Column(name = "is_searchable", nullable = false)
    private Boolean isSearchable = false;

    @Column(name = "is_filter", nullable = false)
    private Boolean isFilter = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public DatasetFieldEntity() {}

    public DatasetFieldEntity(DatasetRegistryEntity dataset, String fieldName, String fieldAlias, String dataType) {
        this.dataset = dataset;
        this.fieldName = fieldName;
        this.fieldAlias = fieldAlias;
        this.dataType = dataType != null ? dataType : "varchar";
        this.isSearchable = false;
        this.isFilter = false;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DatasetRegistryEntity getDataset() { return dataset; }
    public void setDataset(DatasetRegistryEntity dataset) { this.dataset = dataset; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getFieldAlias() { return fieldAlias; }
    public void setFieldAlias(String fieldAlias) { this.fieldAlias = fieldAlias; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public Boolean getIsSearchable() { return isSearchable; }
    public void setIsSearchable(Boolean isSearchable) { this.isSearchable = isSearchable; }

    public Boolean getIsFilter() { return isFilter; }
    public void setIsFilter(Boolean isFilter) { this.isFilter = isFilter; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
