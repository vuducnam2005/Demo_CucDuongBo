package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.gov.drvn.kcht.entity.ImportFileEntity;
import vn.gov.drvn.kcht.entity.ImportJobEntity;
import vn.gov.drvn.kcht.entity.RawDatasetRecordEntity;
import vn.gov.drvn.kcht.repository.ImportProgressRepository;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    @Value("${kcht.data.dir:C:\\Data\\kcht_json_2026-10-05}")
    private String dataDir;

    @Value("${kcht.import.batch-size:1000}")
    private int batchSize;

    private final DatasetRegistryService registryService;
    private final PayloadValidator payloadValidator;
    private final ImportProgressRepository progressRepository;
    private final RawDatasetRecordRepository rawRecordRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final JsonFactory jsonFactory;

    // Cache manifest metadata
    private Map<String, ManifestFileInfo> manifestCache = null;

    public ImportService(DatasetRegistryService registryService,
                         PayloadValidator payloadValidator,
                         ImportProgressRepository progressRepository,
                         RawDatasetRecordRepository rawRecordRepository,
                         JdbcTemplate jdbcTemplate,
                         ObjectMapper objectMapper,
                         PlatformTransactionManager transactionManager) {
        this.registryService = registryService;
        this.payloadValidator = payloadValidator;
        this.progressRepository = progressRepository;
        this.rawRecordRepository = rawRecordRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.jsonFactory = new JsonFactory();
    }

    public static class ManifestFileInfo {
        public String path;
        public long bytes;
        public String sha256;
        public int records;
        public String kind;
    }

    public static class FileImportAudit {
        public String relativePath;
        public String datasetKey;
        public long fileBytes;
        public String fileSha256;
        public int manifestExpectedRecords;
        public int rowsRead;
        public int rowsInserted;
        public int rowsUpdated;
        public int rowsSkipped;
        public int rowsFailed;
        public int uniqueKeys;
        public int duplicateKeys;
        public int duplicatePayloads;
        public int validGeometries;
        public long durationMs;
        public long memoryUsedMb;
        public double speedRecordsPerSec;

        @Override
        public String toString() {
            return String.format(
                "File: %s | Read: %d | Ins: %d | Upd: %d | Skip: %d | Fail: %d | UniqueKeys: %d | Geoms: %d | Time: %dms (%.1f rec/s) | RAM: %dMB",
                relativePath, rowsRead, rowsInserted, rowsUpdated, rowsSkipped, rowsFailed, uniqueKeys, validGeometries, durationMs, speedRecordsPerSec, memoryUsedMb
            );
        }
    }

    public static class ImportSummary {
        public String jobName;
        public boolean dryRun;
        public int totalFiles;
        public int processedFiles;
        public int skippedCompletedFiles;
        public int totalRecordsProcessed;
        public int validRecords;
        public int invalidRecords;
        public int totalInserted;
        public int totalUpdated;
        public int totalSkipped;
        public int totalFailed;
        public long durationMs;
        public List<FileImportAudit> audits = new ArrayList<>();
        public List<String> fileSummaries = new ArrayList<>();

        public String toFormattedTable() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("=========================================================================================================\n"));
            sb.append(String.format("IMPORT SUMMARY REPORT (DryRun: %b, Job: %s, Total Time: %d ms)\n", dryRun, jobName, durationMs));
            sb.append(String.format("=========================================================================================================\n"));
            sb.append(String.format("%-32s | %8s | %8s | %8s | %8s | %8s | %8s | %8s | %9s | %7s\n",
                    "Dataset File", "Manifest", "Read", "Inserted", "Updated", "Skipped", "Failed", "Time(ms)", "Speed(r/s)", "RAM(MB)"));
            sb.append("---------------------------------+----------+----------+----------+----------+----------+----------+----------+-----------+--------\n");

            for (FileImportAudit a : audits) {
                sb.append(String.format("%-32s | %8d | %8d | %8d | %8d | %8d | %8d | %8d | %9.1f | %7d\n",
                        a.relativePath, a.manifestExpectedRecords, a.rowsRead,
                        a.rowsInserted, a.rowsUpdated, a.rowsSkipped, a.rowsFailed,
                        a.durationMs, a.speedRecordsPerSec, a.memoryUsedMb));
            }
            sb.append(String.format("=========================================================================================================\n"));
            sb.append(String.format("TOTAL: Files=%d/%d (Skipped=%d), Read=%d, Ins=%d, Upd=%d, Skip=%d, Fail=%d\n",
                    processedFiles, totalFiles, skippedCompletedFiles,
                    totalRecordsProcessed, totalInserted, totalUpdated, totalSkipped, totalFailed));
            sb.append(String.format("=========================================================================================================\n"));
            return sb.toString();
        }

        @Override
        public String toString() {
            return toFormattedTable();
        }
    }

    /**
     * Nạp dữ liệu cho một tệp đơn lẻ.
     */
    public ImportSummary importSingleFile(String relativeFilePath, boolean dryRun, int recordLimit) throws Exception {
        return importFiles(List.of(relativeFilePath), dryRun, recordLimit);
    }

    /**
     * Nạp dữ liệu cho danh sách tệp xác định.
     */
    public ImportSummary importFiles(List<String> relativeFilePaths, boolean dryRun, int recordLimit) throws Exception {
        long startTime = System.currentTimeMillis();
        ImportSummary summary = new ImportSummary();
        summary.dryRun = dryRun;
        summary.totalFiles = relativeFilePaths.size();
        summary.jobName = (dryRun ? "DRY_RUN_" : "IMPORT_JOB_") + System.currentTimeMillis();

        loadManifestCache();

        ImportJobEntity job = null;
        if (!dryRun) {
            int totalExpected = 0;
            for (String p : relativeFilePaths) {
                ManifestFileInfo info = manifestCache.get(p);
                if (info != null) totalExpected += info.records;
            }
            job = progressRepository.createJob(summary.jobName, relativeFilePaths.size(), totalExpected);
        }

        int fileIndex = 0;
        int totalFiles = relativeFilePaths.size();
        for (String relativePath : relativeFilePaths) {
            fileIndex++;
            File file = new File(dataDir, relativePath);
            if (!file.exists()) {
                log.warn("[{}/{}] Không tìm thấy tệp: {}", fileIndex, totalFiles, file.getAbsolutePath());
                continue;
            }

            try {
                if (fileIndex % 25 == 0 || fileIndex == 1 || fileIndex == totalFiles) {
                    log.info("[{}/{}] Đang xử lý: {} (Tiến độ: {:.1f}%) ...",
                            fileIndex, totalFiles, relativePath, (fileIndex * 100.0 / totalFiles));
                }
                FileImportAudit audit = processStreamingFile(file, relativePath, job, dryRun, recordLimit, summary);
                if (audit != null) {
                    summary.audits.add(audit);
                    summary.totalRecordsProcessed += audit.rowsRead;
                    summary.validRecords += (audit.rowsRead - audit.rowsFailed);
                    summary.invalidRecords += audit.rowsFailed;
                    summary.totalInserted += audit.rowsInserted;
                    summary.totalUpdated += audit.rowsUpdated;
                    summary.totalSkipped += audit.rowsSkipped;
                    summary.totalFailed += audit.rowsFailed;
                }
            } catch (Exception e) {
                log.error("[{}/{}] Lỗi khi xử lý tệp {}: {}. Tiến trình tiếp tục với tệp tiếp theo.",
                        fileIndex, totalFiles, relativePath, e.getMessage());
                summary.totalFailed++;
                if (totalFiles == 1) {
                    throw e;
                }
            }
        }

        if (!dryRun && job != null) {
            progressRepository.completeJob(job, "COMPLETED");
        }

        summary.durationMs = System.currentTimeMillis() - startTime;
        return summary;
    }

    /**
     * Nạp toàn bộ hoặc lọc các tệp từ manifest.json.
     */
    public ImportSummary importFromManifest(boolean dryRun, String fileFilter, int limitPerFile) throws Exception {
        loadManifestCache();

        List<String> selectedFiles = new ArrayList<>();
        for (String path : manifestCache.keySet()) {
            if (fileFilter == null || fileFilter.trim().isEmpty() || path.contains(fileFilter)) {
                selectedFiles.add(path);
            }
        }

        return importFiles(selectedFiles, dryRun, limitPerFile);
    }

    /**
     * Đọc luồng (Streaming) tệp JSON qua Jackson JsonParser để bảo vệ RAM.
     */
    private FileImportAudit processStreamingFile(File file, String relativePath, ImportJobEntity job,
                                                 boolean dryRun, int recordLimit, ImportSummary summary) throws Exception {
        long fileStartTime = System.currentTimeMillis();
        String fileName = file.getName();
        String datasetKey = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;
        long fileLength = file.length();

        ManifestFileInfo manifestInfo = manifestCache.get(relativePath);
        int expectedRecords = manifestInfo != null ? manifestInfo.records : 0;
        String manifestSha = manifestInfo != null ? manifestInfo.sha256 : "0".repeat(64);

        FileImportAudit audit = new FileImportAudit();
        audit.relativePath = relativePath;
        audit.datasetKey = datasetKey;
        audit.fileBytes = fileLength;
        audit.manifestExpectedRecords = expectedRecords;

        log.info(">>> Xử lý [{}] (Dung lượng: {} bytes, Dự kiến: {} dòng, DryRun: {})",
                relativePath, fileLength, expectedRecords, dryRun);

        ImportFileEntity fileProgress = null;
        if (!dryRun && job != null) {
            // Đảm bảo dataset đã đăng ký trong dataset_registry trước khi ghi raw_dataset_record (FK constraint)
            registryService.ensureDatasetRegistered(
                    datasetKey, relativePath,
                    manifestInfo != null ? manifestInfo.kind : "asset",
                    expectedRecords
            );

            // Checkpoint & Resume check
            Optional<ImportFileEntity> existingProgress = progressRepository.findFileProgress(job.getId(), relativePath);
            if (existingProgress.isPresent() && "COMPLETED".equals(existingProgress.get().getStatus())) {
                log.info("Tệp {} đã hoàn tất trong phiên này. Bỏ qua (Checkpoint).", relativePath);
                summary.skippedCompletedFiles++;
                return null;
            }

            fileProgress = progressRepository.registerOrGetFile(job, relativePath, fileLength, manifestSha, expectedRecords);
            progressRepository.markFileProcessing(fileProgress);
        }

        Set<String> seenKeys = new HashSet<>();
        Set<String> seenPayloadHashes = new HashSet<>();
        List<RawDatasetRecordEntity> batchList = new ArrayList<>(batchSize);
        MessageDigest fileShaDigest = MessageDigest.getInstance("SHA-256");

        try (InputStream fis = new BufferedInputStream(new FileInputStream(file));
             DigestInputStream dis = new DigestInputStream(fis, fileShaDigest);
             JsonParser parser = jsonFactory.createParser(dis)) {

            parser.setCodec(objectMapper);
            JsonToken token = parser.nextToken();

            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    audit.rowsRead++;
                    JsonNode recordNode = parser.readValueAsTree();

                    PayloadValidator.ValidationResult valResult = payloadValidator.validate(recordNode);
                    if (!valResult.isValid()) {
                        audit.rowsFailed++;
                        log.warn("Bản ghi #{} trong {} không hợp lệ: {}", audit.rowsRead, relativePath, valResult.getErrors());
                        if (!dryRun && job != null) {
                            progressRepository.recordError(job, fileProgress, datasetKey,
                                    valResult.getRecordKey(), "VALIDATE", "ERR_INVALID_PAYLOAD",
                                    String.join("; ", valResult.getErrors()), recordNode.toString());
                        }
                    } else {
                        String rKey = valResult.getRecordKey();
                        String pSha = valResult.getPayloadSha256();

                        if (!seenKeys.add(rKey)) {
                            audit.duplicateKeys++;
                        }
                        if (!seenPayloadHashes.add(pSha)) {
                            audit.duplicatePayloads++;
                        }

                        // Kiểm tra hình học tọa độ hợp lệ
                        if (hasValidGeometry(recordNode)) {
                            audit.validGeometries++;
                        }

                        if (!dryRun) {
                            RawDatasetRecordEntity entity = new RawDatasetRecordEntity(
                                    datasetKey,
                                    rKey,
                                    recordNode.toString(),
                                    relativePath,
                                    manifestSha,
                                    pSha,
                                    job != null ? job.getId() : null,
                                    "RAW_STORED"
                            );
                            batchList.add(entity);

                            if (batchList.size() >= batchSize) {
                                commitBatch(datasetKey, batchList, audit);
                                batchList.clear();
                            }
                        }
                    }

                    if (recordLimit > 0 && audit.rowsRead >= recordLimit) {
                        log.info("Đã đạt giới hạn {} bản ghi cho tệp {}.", recordLimit, relativePath);
                        break;
                    }
                }
            } else if (token == JsonToken.START_OBJECT) {
                audit.rowsRead = 1;
                JsonNode recordNode = parser.readValueAsTree();
                PayloadValidator.ValidationResult valResult = payloadValidator.validate(recordNode);
                if (valResult.isValid()) {
                    String rKey = valResult.getRecordKey();
                    seenKeys.add(rKey);
                    if (hasValidGeometry(recordNode)) {
                        audit.validGeometries++;
                    }
                    if (!dryRun) {
                        batchList.add(new RawDatasetRecordEntity(
                                datasetKey, rKey, recordNode.toString(),
                                relativePath, manifestSha, valResult.getPayloadSha256(),
                                job != null ? job.getId() : null, "RAW_STORED"
                        ));
                    }
                } else {
                    audit.rowsFailed = 1;
                }
            }

            // Ghi phần còn lại trong batch
            if (!dryRun && !batchList.isEmpty()) {
                commitBatch(datasetKey, batchList, audit);
                batchList.clear();
            }

            audit.uniqueKeys = seenKeys.size();
            audit.fileSha256 = HexFormat.of().formatHex(fileShaDigest.digest());
            audit.durationMs = System.currentTimeMillis() - fileStartTime;
            audit.speedRecordsPerSec = audit.durationMs > 0 ? (audit.rowsRead * 1000.0 / audit.durationMs) : 0;
            Runtime rt = Runtime.getRuntime();
            audit.memoryUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);


            if (!dryRun && fileProgress != null) {
                fileProgress.setSourceSha256(audit.fileSha256);
                progressRepository.markFileCompleted(fileProgress, audit.rowsInserted + audit.rowsUpdated);
            }

            summary.processedFiles++;
            summary.fileSummaries.add(audit.toString());
            log.info("<<< Hoàn tất tệp: {}", audit);

            return audit;

        } catch (Exception e) {
            log.error("Lỗi khi xử lý luồng tệp {}: {}", relativePath, e.getMessage(), e);
            if (!dryRun && fileProgress != null) {
                progressRepository.markFileFailed(fileProgress, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Ghi một lô (Batch) bản ghi vào CSDL qua JdbcTemplate với cơ chế kiểm tra chống lặp Idempotent.
     */
    private void commitBatch(String datasetKey, List<RawDatasetRecordEntity> batch, FileImportAudit audit) {
        if (batch.isEmpty()) return;

        transactionTemplate.executeWithoutResult(status -> {
            // Khử trùng lặp nội bộ trong cùng batch (nếu có)
            Map<String, RawDatasetRecordEntity> distinctBatchMap = new LinkedHashMap<>();
            for (RawDatasetRecordEntity rec : batch) {
                distinctBatchMap.put(rec.getRecordKey(), rec);
            }
            List<RawDatasetRecordEntity> distinctBatch = new ArrayList<>(distinctBatchMap.values());

            // 1. Lấy danh sách record_key cần kiểm tra
            List<String> keys = distinctBatch.stream().map(RawDatasetRecordEntity::getRecordKey).toList();

            // 2. Tra cứu các key đã tồn tại trong CSDL kèm băm SHA256 (1 query duy nhất cho cả batch)
            String inPlaceholders = String.join(",", Collections.nCopies(keys.size(), "?"));
            String selectSql = "SELECT record_key, payload_sha256 FROM raw_dataset_record WHERE dataset_key = ? AND record_key IN (" + inPlaceholders + ")";

            List<Object> params = new ArrayList<>(keys.size() + 1);
            params.add(datasetKey);
            params.addAll(keys);

            Map<String, String> existingKeyMap = new HashMap<>();
            jdbcTemplate.query(selectSql, rs -> {
                existingKeyMap.put(rs.getString("record_key"), rs.getString("payload_sha256"));
            }, params.toArray());

            List<RawDatasetRecordEntity> toInsert = new ArrayList<>();
            List<RawDatasetRecordEntity> toUpdate = new ArrayList<>();
            int skipped = 0;

            for (RawDatasetRecordEntity rec : distinctBatch) {
                String existingSha = existingKeyMap.get(rec.getRecordKey());
                if (existingSha == null) {
                    toInsert.add(rec);
                } else if (!existingSha.trim().equals(rec.getPayloadSha256().trim())) {
                    toUpdate.add(rec);
                } else {
                    skipped++;
                }
            }

            // 3. Batch INSERT các bản ghi mới
            if (!toInsert.isEmpty()) {
                String insertSql = """
                    INSERT INTO raw_dataset_record (
                        dataset_key, record_key, raw_payload, source_file, source_sha256, payload_sha256,
                        import_job_id, record_status, imported_at, updated_at
                    ) VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, 'RAW_STORED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """;
                jdbcTemplate.batchUpdate(insertSql, toInsert, toInsert.size(), (ps, rec) -> {
                    ps.setString(1, rec.getDatasetKey());
                    ps.setString(2, rec.getRecordKey());
                    ps.setString(3, rec.getRawPayload());
                    ps.setString(4, rec.getSourceFile());
                    ps.setString(5, rec.getSourceSha256());
                    ps.setString(6, rec.getPayloadSha256());
                    if (rec.getImportJobId() != null) {
                        ps.setLong(7, rec.getImportJobId());
                    } else {
                        ps.setNull(7, java.sql.Types.BIGINT);
                    }
                });
                audit.rowsInserted += toInsert.size();
            }

            // 4. Batch UPDATE các bản ghi có nội dung thay đổi
            if (!toUpdate.isEmpty()) {
                String updateSql = """
                    UPDATE raw_dataset_record
                    SET raw_payload = ?::jsonb, payload_sha256 = ?, source_file = ?, source_sha256 = ?,
                        import_job_id = ?, record_status = 'RAW_STORED', updated_at = CURRENT_TIMESTAMP
                    WHERE dataset_key = ? AND record_key = ?
                """;
                jdbcTemplate.batchUpdate(updateSql, toUpdate, toUpdate.size(), (ps, rec) -> {
                    ps.setString(1, rec.getRawPayload());
                    ps.setString(2, rec.getPayloadSha256());
                    ps.setString(3, rec.getSourceFile());
                    ps.setString(4, rec.getSourceSha256());
                    if (rec.getImportJobId() != null) {
                        ps.setLong(5, rec.getImportJobId());
                    } else {
                        ps.setNull(5, java.sql.Types.BIGINT);
                    }
                    ps.setString(6, rec.getDatasetKey());
                    ps.setString(7, rec.getRecordKey());
                });
                audit.rowsUpdated += toUpdate.size();
            }

            audit.rowsSkipped += skipped;
        });
    }

    /**
     * Nhận diện xem bản ghi có tọa độ không gian hợp lệ không.
     */
    private boolean hasValidGeometry(JsonNode recordNode) {
        if (recordNode.has("x_min") && recordNode.has("y_min")) {
            JsonNode xNode = recordNode.get("x_min");
            JsonNode yNode = recordNode.get("y_min");
            if (xNode != null && !xNode.isNull() && yNode != null && !yNode.isNull()) {
                double x = xNode.asDouble(0.0);
                double y = yNode.asDouble(0.0);
                return x != 0.0 && y != 0.0;
            }
        }
        if (recordNode.has("geom") && !recordNode.get("geom").isNull()) {
            String geomStr = recordNode.get("geom").asText();
            if (!"geom".equalsIgnoreCase(geomStr) && !geomStr.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private synchronized void loadManifestCache() {
        if (manifestCache != null) return;
        manifestCache = new LinkedHashMap<>();
        File manifestFile = new File(dataDir, "manifest.json");
        if (!manifestFile.exists()) return;

        try {
            JsonNode root = objectMapper.readTree(manifestFile);
            JsonNode filesNode = root.get("files");
            if (filesNode != null && filesNode.isArray()) {
                for (JsonNode f : filesNode) {
                    ManifestFileInfo info = new ManifestFileInfo();
                    info.path = f.has("path") ? f.get("path").asText() : "";
                    info.bytes = f.has("bytes") ? f.get("bytes").asLong() : 0L;
                    info.sha256 = f.has("sha256") ? f.get("sha256").asText() : "";
                    info.records = f.has("records") ? f.get("records").asInt() : 0;
                    info.kind = f.has("kind") ? f.get("kind").asText() : "asset";
                    manifestCache.put(info.path, info);
                }
            }
        } catch (Exception e) {
            log.warn("Không thể nạp manifest.json cache: {}", e.getMessage());
        }
    }
}
