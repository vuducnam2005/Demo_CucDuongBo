package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.entity.DatasetFieldEntity;
import vn.gov.drvn.kcht.entity.DatasetRegistryEntity;
import vn.gov.drvn.kcht.repository.DatasetFieldRepository;
import vn.gov.drvn.kcht.repository.DatasetRegistryRepository;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DatasetRegistryService {

    private static final Logger log = LoggerFactory.getLogger(DatasetRegistryService.class);

    @Value("${kcht.data.dir:C:\\Data\\kcht_json_2026-10-05}")
    private String dataDir;

    private final DatasetRegistryRepository registryRepository;
    private final DatasetFieldRepository fieldRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DatasetRegistryService(DatasetRegistryRepository registryRepository,
                                  DatasetFieldRepository fieldRepository,
                                  JdbcTemplate jdbcTemplate,
                                  ObjectMapper objectMapper) {
        this.registryRepository = registryRepository;
        this.fieldRepository = fieldRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Đảm bảo dataset đã được đăng ký trong bảng dataset_registry trước khi ghi raw_dataset_record.
     */
    @Transactional
    public DatasetRegistryEntity ensureDatasetRegistered(String datasetKey, String relativePath, String kind, int expectedRecords) {
        return registryRepository.findByDatasetKey(datasetKey).orElseGet(() -> {
            String displayName = datasetKey;
            String endpoint = null;
            if (relativePath != null) {
                File metaFile = new File(dataDir, relativePath.replace(".json", "_metadata.json"));
                if (metaFile.exists()) {
                    try {
                        JsonNode metaNode = objectMapper.readTree(metaFile);
                        if (metaNode.has("collection") && metaNode.get("collection").has("name")) {
                            displayName = metaNode.get("collection").get("name").asText();
                        }
                        if (metaNode.has("endpoint")) {
                            endpoint = metaNode.get("endpoint").asText();
                        }
                    } catch (Exception e) {
                        log.warn("Không thể đọc metadata dataset từ {}: {}", metaFile.getName(), e.getMessage());
                    }
                }
            }
            DatasetRegistryEntity entity = new DatasetRegistryEntity(
                    datasetKey, displayName, kind != null ? kind : "asset", endpoint,
                    relativePath != null ? relativePath : datasetKey + ".json", expectedRecords
            );
            return registryRepository.save(entity);
        });
    }

    /**
     * Nạp toàn bộ metadata từ manifest.json và field_dictionary.json vào CSDL.
     * Đảm bảo tính lũy tiến (Idempotent).
     */
    @Transactional
    public List<DatasetRegistryEntity> syncRegistryFromManifest() throws IOException {
        File manifestFile = new File(dataDir, "manifest.json");
        if (!manifestFile.exists()) {
            throw new IllegalArgumentException("Không tìm thấy manifest.json tại: " + manifestFile.getAbsolutePath());
        }

        log.info("Đang đọc manifest từ: {}", manifestFile.getAbsolutePath());
        JsonNode manifestNode = objectMapper.readTree(manifestFile);
        JsonNode filesNode = manifestNode.get("files");

        List<DatasetRegistryEntity> registeredList = new ArrayList<>();
        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileEntry : filesNode) {
                String path = fileEntry.get("path").asText();
                int records = fileEntry.has("records") ? fileEntry.get("records").asInt() : 0;
                String kind = fileEntry.has("kind") ? fileEntry.get("kind").asText() : "unknown";

                String fileName = new File(path).getName();
                String datasetKey = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;

                String datasetName = datasetKey;
                String endpoint = null;
                File metaFile = new File(dataDir, path.replace(".json", "_metadata.json"));
                if (metaFile.exists()) {
                    try {
                        JsonNode metaNode = objectMapper.readTree(metaFile);
                        if (metaNode.has("collection")) {
                            JsonNode col = metaNode.get("collection");
                            if (col.has("name") && !col.get("name").isNull()) {
                                datasetName = col.get("name").asText();
                            }
                            if (col.has("endpoint") && !col.get("endpoint").isNull()) {
                                endpoint = col.get("endpoint").asText();
                            }
                        } else if (metaNode.has("endpoint") && !metaNode.get("endpoint").isNull()) {
                            endpoint = metaNode.get("endpoint").asText();
                        }
                    } catch (Exception e) {
                        log.warn("Không thể đọc metadata cho file {}: {}", path, e.getMessage());
                    }
                }

                final String finalName = datasetName;
                final String finalEndpoint = endpoint;
                DatasetRegistryEntity entity = registryRepository.findByDatasetKey(datasetKey)
                    .map(existing -> {
                        existing.setDatasetName(finalName);
                        existing.setTotalRecords(records);
                        existing.setSourceFile(path);
                        existing.setKind(kind);
                        existing.setEndpoint(finalEndpoint);
                        existing.setUpdatedAt(OffsetDateTime.now());
                        return registryRepository.save(existing);
                    })
                    .orElseGet(() -> {
                        DatasetRegistryEntity newEntity = new DatasetRegistryEntity(
                                datasetKey, finalName, kind, finalEndpoint, path, records
                        );
                        return registryRepository.save(newEntity);
                    });

                registeredList.add(entity);
            }
        }

        log.info("Đã đồng bộ {} datasets vào bảng dataset_registry.", registeredList.size());

        // Đồng bộ từ điển trường từ field_dictionary.json
        syncFieldDictionary();

        return registeredList;
    }

    /**
     * Đồng bộ hàng loạt 10,142 trường từ field_dictionary.json bằng JdbcTemplate Batch.
     */
    @Transactional
    public void syncFieldDictionary() {
        File fieldDictFile = new File(dataDir, "field_dictionary.json");
        if (!fieldDictFile.exists()) {
            log.warn("Không tìm thấy field_dictionary.json tại: {}", fieldDictFile.getAbsolutePath());
            return;
        }

        try {
            log.info("Đang đọc từ điển trường từ: {}", fieldDictFile.getAbsolutePath());
            JsonNode dictArray = objectMapper.readTree(fieldDictFile);
            if (dictArray != null && dictArray.isArray()) {
                // Tải trước bản đồ datasetKey -> ID để tránh N+1 queries
                Map<String, Long> datasetIdMap = new HashMap<>();
                registryRepository.findAll().forEach(d -> datasetIdMap.put(d.getDatasetKey(), d.getId()));

                List<Object[]> batchParams = new ArrayList<>();
                for (JsonNode item : dictArray) {
                    String datasetKey = item.has("dataset") ? item.get("dataset").asText() : null;
                    String field = item.has("field") ? item.get("field").asText() : null;
                    String alias = item.has("alias") ? item.get("alias").asText() : null;

                    if (datasetKey != null && field != null) {
                        Long datasetId = datasetIdMap.get(datasetKey);
                        if (datasetId != null) {
                            batchParams.add(new Object[]{datasetId, field, alias, "varchar"});
                        }
                    }
                }

                String insertSql = """
                    INSERT INTO dataset_field (dataset_id, field_name, field_alias, data_type, created_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (dataset_id, field_name) DO UPDATE
                    SET field_alias = EXCLUDED.field_alias, data_type = EXCLUDED.data_type
                """;
                jdbcTemplate.batchUpdate(insertSql, batchParams);
                log.info("Đã đồng bộ {} trường vào bảng dataset_field bằng batch update.", batchParams.size());
            }
        } catch (Exception e) {
            log.error("Lỗi khi đọc field_dictionary.json: {}", e.getMessage(), e);
        }
    }

    public Optional<DatasetRegistryEntity> getByDatasetKey(String datasetKey) {
        return registryRepository.findByDatasetKey(datasetKey);
    }
}

