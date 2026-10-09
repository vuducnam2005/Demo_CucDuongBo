package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.gov.drvn.kcht.dto.DocumentFolderDto;
import vn.gov.drvn.kcht.dto.DocumentItemDto;
import vn.gov.drvn.kcht.dto.PagedResponse;

import vn.gov.drvn.kcht.entity.DocumentMetadataEntity;
import vn.gov.drvn.kcht.exception.BadRequestException;
import vn.gov.drvn.kcht.exception.ResourceNotFoundException;
import vn.gov.drvn.kcht.repository.DocumentMetadataRepository;
import vn.gov.drvn.kcht.storage.StorageService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentQueryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentQueryService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final StorageService storageService;

    public DocumentQueryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                DocumentMetadataRepository documentMetadataRepository,
                                StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.documentMetadataRepository = documentMetadataRepository;
        this.storageService = storageService;
    }

    /**
     * Lấy toàn bộ cây thư mục hồ sơ tài liệu.
     */
    public List<DocumentFolderDto> getFolders() {
        String sql = "SELECT record_key, raw_payload FROM raw_dataset_record " +
                "WHERE dataset_key = 'document_folders' ORDER BY id ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String recKey = rs.getString("record_key");
            String payloadJson = rs.getString("raw_payload");

            String folderId = recKey;
            String folderName = recKey;
            String parentId = "#";
            int count = 0;

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.hasNonNull("id")) folderId = node.get("id").asText();
                    if (node.hasNonNull("text")) folderName = node.get("text").asText();
                    if (node.hasNonNull("parent")) parentId = node.get("parent").asText();
                    if (node.hasNonNull("count")) count = node.get("count").asInt(0);
                } catch (Exception e) {
                    log.warn("Không thể phân tích payload thư mục {}: {}", recKey, e.getMessage());
                }
            }

            return new DocumentFolderDto(folderId, folderId, folderName, parentId, count);
        });
    }

    /**
     * Tra cứu và phân trang danh sách tài liệu hồ sơ.
     */
    public PagedResponse<DocumentItemDto> getDocuments(String keyword, String folderId,
                                                      String mimeType, String extension,
                                                      int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = Math.min(Math.max(1, size), 100);
        int offset = validPage * validSize;

        StringBuilder whereSql = new StringBuilder(" WHERE dataset_key = 'documents'");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            whereSql.append("""
                 AND (
                     raw_payload->>'vidagis_name' ILIKE ?
                     OR raw_payload->>'vidagis_object_name' ILIKE ?
                     OR raw_payload->>'vidagis_username' ILIKE ?
                     OR record_key ILIKE ?
                 )
            """);
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }

        if (folderId != null && !folderId.isBlank()) {
            whereSql.append(" AND raw_payload->>'vidagis_group_id' = ?");
            params.add(folderId.trim());
        }

        if (mimeType != null && !mimeType.isBlank()) {
            whereSql.append(" AND raw_payload->>'vidagis_mimetype' ILIKE ?");
            params.add("%" + mimeType.trim() + "%");
        }

        if (extension != null && !extension.isBlank()) {
            whereSql.append(" AND raw_payload->>'vidagis_extension' ILIKE ?");
            params.add(extension.trim().replace(".", ""));
        }

        String countSql = "SELECT count(*) FROM raw_dataset_record" + whereSql;
        Long totalElements = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long total = totalElements != null ? totalElements : 0;

        String selectSql = "SELECT record_key, raw_payload FROM raw_dataset_record" + whereSql +
                " ORDER BY id ASC LIMIT ? OFFSET ?";
        params.add(validSize);
        params.add(offset);

        List<DocumentItemDto> content = jdbcTemplate.query(selectSql, (rs, rowNum) -> {
            String recKey = rs.getString("record_key");
            String payloadJson = rs.getString("raw_payload");

            String fileEntryId = null;
            String fileName = "Tài liệu " + recKey;
            String fileExt = null;
            String mime = null;
            Long fileSize = null;
            String groupId = null;
            String groupName = null;
            String objectName = null;
            String tableName = null;
            String uploader = null;
            String createdAt = null;

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.hasNonNull("vidagis_fileentryid")) fileEntryId = node.get("vidagis_fileentryid").asText();
                    if (node.hasNonNull("vidagis_name")) fileName = node.get("vidagis_name").asText();
                    if (node.hasNonNull("vidagis_extension")) fileExt = node.get("vidagis_extension").asText();
                    if (node.hasNonNull("vidagis_mimetype")) mime = node.get("vidagis_mimetype").asText();
                    if (node.hasNonNull("vidagis_size")) fileSize = node.get("vidagis_size").asLong();
                    if (node.hasNonNull("vidagis_group_id")) groupId = node.get("vidagis_group_id").asText();
                    if (node.hasNonNull("vidagis_group_name")) groupName = node.get("vidagis_group_name").asText();
                    if (node.hasNonNull("vidagis_object_name")) objectName = node.get("vidagis_object_name").asText();
                    if (node.hasNonNull("vidagis_table_name")) tableName = node.get("vidagis_table_name").asText();
                    if (node.hasNonNull("vidagis_username")) uploader = node.get("vidagis_username").asText();
                    if (node.hasNonNull("vidagis_createdate")) createdAt = node.get("vidagis_createdate").asText();
                } catch (Exception e) {
                    log.warn("Không thể phân tích payload tài liệu {}: {}", recKey, e.getMessage());
                }
            }

            return new DocumentItemDto(recKey, fileEntryId, fileName, fileExt, mime, fileSize,
                    groupId, groupName, objectName, tableName, uploader, createdAt);
        }, params.toArray());

        return new PagedResponse<>(content, validPage, validSize, total);
    }

    /**
     * Tra cứu một tài liệu hồ sơ theo ID bản ghi hoặc file_entry_id.
     */
    public DocumentItemDto getDocumentById(String idOrFileEntryId) {
        if (idOrFileEntryId == null || idOrFileEntryId.isBlank()) {
            throw new BadRequestException("Mã định danh tài liệu không được để trống");
        }

        // 1. Thử tra cứu từ bảng chuẩn document_metadata
        var docOpt = documentMetadataRepository.findByFileEntryIdAndIsDeletedFalse(idOrFileEntryId.trim());
        if (docOpt.isPresent()) {
            var entity = docOpt.get();
            return new DocumentItemDto(
                    String.valueOf(entity.getId()),
                    entity.getFileEntryId(),
                    entity.getOriginalName(),
                    entity.getFileExtension(),
                    entity.getMimeType(),
                    entity.getFileSize(),
                    entity.getFolderId() != null ? String.valueOf(entity.getFolderId()) : "",
                    entity.getBranchId() != null ? entity.getBranchId() : entity.getOrganizationId(),
                    entity.getAssetRecordId(),
                    "asset_record",
                    entity.getUploaderUsername(),
                    entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : ""
            );
        }

        // 2. Tra cứu từ tầng raw_dataset_record
        String sql = "SELECT record_key, raw_payload FROM raw_dataset_record " +
                "WHERE dataset_key = 'documents' AND (record_key = ? OR raw_payload->>'vidagis_fileentryid' = ?) LIMIT 1";

        List<DocumentItemDto> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String recKey = rs.getString("record_key");
            String payloadJson = rs.getString("raw_payload");

            String fileEntryId = recKey;
            String fileName = "Tài liệu " + recKey;
            String fileExt = "pdf";
            String mime = "application/pdf";
            Long fileSize = null;
            String groupId = null;
            String groupName = null;
            String objectName = null;
            String tableName = null;
            String uploader = null;
            String createdAt = null;

            if (payloadJson != null) {
                try {
                    JsonNode node = objectMapper.readTree(payloadJson);
                    if (node.hasNonNull("vidagis_fileentryid")) fileEntryId = node.get("vidagis_fileentryid").asText();
                    if (node.hasNonNull("vidagis_name")) fileName = node.get("vidagis_name").asText();
                    if (node.hasNonNull("vidagis_extension")) fileExt = node.get("vidagis_extension").asText();
                    if (node.hasNonNull("vidagis_mimetype")) mime = node.get("vidagis_mimetype").asText();
                    if (node.hasNonNull("vidagis_size")) fileSize = node.get("vidagis_size").asLong();
                    if (node.hasNonNull("vidagis_group_id")) groupId = node.get("vidagis_group_id").asText();
                    if (node.hasNonNull("vidagis_group_name")) groupName = node.get("vidagis_group_name").asText();
                    if (node.hasNonNull("vidagis_object_name")) objectName = node.get("vidagis_object_name").asText();
                    if (node.hasNonNull("vidagis_table_name")) tableName = node.get("vidagis_table_name").asText();
                    if (node.hasNonNull("vidagis_username")) uploader = node.get("vidagis_username").asText();
                    if (node.hasNonNull("vidagis_createdate")) createdAt = node.get("vidagis_createdate").asText();
                } catch (Exception e) {
                    log.warn("Không thể phân tích payload tài liệu {}: {}", recKey, e.getMessage());
                }
            }

            return new DocumentItemDto(recKey, fileEntryId, fileName, fileExt, mime, fileSize,
                    groupId, groupName, objectName, tableName, uploader, createdAt);
        }, idOrFileEntryId.trim(), idOrFileEntryId.trim());

        if (!items.isEmpty()) {
            return items.get(0);
        }

        throw new ResourceNotFoundException("Không tìm thấy hồ sơ tài liệu: " + idOrFileEntryId);
    }

    public record DocumentFileDownload(String fileName, String mimeType, byte[] content) {}

    /**
     * Chỉ tải tệp nhị phân đã thực sự lưu trữ cục bộ.
     */
    public DocumentFileDownload downloadFileContent(String idOrFileEntryId) {
        DocumentItemDto doc = getDocumentById(idOrFileEntryId);
        var metadata = documentMetadataRepository.findByFileEntryIdAndIsDeletedFalse(doc.getFileEntryId())
                .orElseThrow(() -> new ResourceNotFoundException("Tệp nguồn chưa được lưu trong demo"));
        String objectKey = metadata.getLocalPath();

        // Thử tải từ Object Storage
        Optional<InputStream> isOpt = storageService.downloadFile(objectKey);
        if (isOpt.isPresent()) {
            try (InputStream is = isOpt.get()) {
                byte[] bytes = is.readAllBytes();
                return new DocumentFileDownload(doc.getFileName(), doc.getMimeType(), bytes);
            } catch (Exception e) {
                log.warn("Lỗi đọc file từ storage: {}", e.getMessage());
            }
        }

        throw new ResourceNotFoundException("Tệp nguồn chưa được lưu trong demo");
    }

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "dwg", "jpg", "jpeg", "png", "zip", "rar"
    );

    /**
     * Tải lên tài liệu mới: Lưu trữ nhị phân tại MinIO/S3 (StorageService) và lưu metadata vào CSDL.
     */
    public DocumentItemDto uploadDocument(org.springframework.web.multipart.MultipartFile file,
                                          String folderId,
                                          String assetRecordId,
                                          String branchId,
                                          String username) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Tệp tin tải lên không được rỗng");
        }

        // Giới hạn kích thước tệp tối đa 25MB
        long maxSizeBytes = 25L * 1024L * 1024L;
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("Kích thước tệp vượt quá giới hạn tối đa cho phép (25 MB)");
        }

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex + 1).toLowerCase();
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Định dạng tệp không được hỗ trợ. Chỉ chấp nhận các định dạng: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String fileEntryId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String objectKey = "documents/" + fileEntryId + "." + extension;

        byte[] fileBytes;
        String sha256;
        try {
            fileBytes = file.getBytes();
            sha256 = computeSha256(fileBytes);
        } catch (Exception e) {
            throw new BadRequestException("Không thể đọc nội dung tệp: " + e.getMessage());
        }

        // 1. Lưu trữ nhị phân vào Object Storage (MinIO / S3 / LocalStorage)
        try {
            storageService.uploadFile(objectKey, file.getInputStream(), file.getSize(), file.getContentType());
        } catch (Exception e) {
            log.error("Lỗi khi lưu trữ tệp tin vào StorageService", e);
            throw new RuntimeException("Lỗi lưu trữ tệp tin vào Storage", e);
        }

        // 2. Lưu siêu dữ liệu vào bảng document_metadata
        Long parsedFolderId = null;
        if (folderId != null && !folderId.isBlank()) {
            if (folderId.matches("^[0-9]+$")) {
                long fid = Long.parseLong(folderId);
                try {
                    Integer cnt = jdbcTemplate.queryForObject("SELECT count(*) FROM document_folder WHERE id = ?", Integer.class, fid);
                    if (cnt != null && cnt > 0) {
                        parsedFolderId = fid;
                    }
                } catch (Exception e) {
                    log.warn("Không thể tra cứu thư mục cha {}: {}", folderId, e.getMessage());
                }
            } else {
                try {
                    List<Long> ids = jdbcTemplate.query("SELECT id FROM document_folder WHERE folder_code = ? LIMIT 1",
                            (rs, rowNum) -> rs.getLong("id"), folderId.trim());
                    if (!ids.isEmpty()) {
                        parsedFolderId = ids.get(0);
                    }
                } catch (Exception e) {
                    log.warn("Không thể tra cứu mã thư mục {}: {}", folderId, e.getMessage());
                }
            }
        }

        try {
            DocumentMetadataEntity meta = new DocumentMetadataEntity();
            meta.setFileEntryId(fileEntryId);
            meta.setOriginalName(originalName);
            meta.setFileExtension(extension);
            meta.setMimeType(file.getContentType());
            meta.setFileSize(file.getSize());
            meta.setLocalPath(objectKey);
            meta.setSha256(sha256);
            meta.setFolderId(parsedFolderId);
            meta.setAssetRecordId(assetRecordId);
            meta.setOrganizationId("moc_dbvn");
            meta.setBranchId(branchId != null ? branchId : "moc_dbvn");
            meta.setUploaderUsername(username != null ? username : "admin");
            meta.setIsPublic(true);
            meta.setIsDeleted(false);
            meta.setUploadedAt(OffsetDateTime.now());
            meta.setCreatedAt(OffsetDateTime.now());
            documentMetadataRepository.save(meta);
        } catch (Exception ex) {
            log.warn("Không thể ghi vào document_metadata qua repository: {}", ex.getMessage());
        }

        // 3. Đồng bộ vào raw_dataset_record để API getDocuments trả về ngay lập tức
        try {
            jdbcTemplate.update("""
                INSERT INTO dataset_registry (dataset_key, dataset_name, kind, source_file, total_records)
                VALUES ('documents', 'Hồ sơ tài liệu', 'document', 'documents.json', 1)
                ON CONFLICT (dataset_key) DO NOTHING
            """);

            String groupName = (folderId != null && !folderId.isBlank()) ? "Thư mục " + folderId : "Hồ sơ chung";
            String payloadJson = String.format("""
                {
                    "vidagis_fileentryid": "%s",
                    "vidagis_name": "%s",
                    "vidagis_extension": "%s",
                    "vidagis_mimetype": "%s",
                    "vidagis_size": %d,
                    "vidagis_group_id": "%s",
                    "vidagis_group_name": "%s",
                    "vidagis_object_name": "%s",
                    "vidagis_username": "%s",
                    "vidagis_createdate": "%s"
                }
            """, fileEntryId, originalName, extension, file.getContentType(), file.getSize(),
                    folderId != null ? folderId : "root", groupName,
                    assetRecordId != null ? assetRecordId : "Toàn tuyến",
                    username != null ? username : "admin",
                    OffsetDateTime.now().toString());

            String payloadSha = computeSha256(payloadJson.getBytes(StandardCharsets.UTF_8));

            jdbcTemplate.update("""
                INSERT INTO raw_dataset_record (dataset_key, record_key, raw_payload, source_file, source_sha256, payload_sha256, record_status, imported_at, updated_at)
                VALUES ('documents', ?, ?::jsonb, 'documents.json', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (dataset_key, record_key) DO UPDATE
                SET raw_payload = EXCLUDED.raw_payload, updated_at = CURRENT_TIMESTAMP
            """, fileEntryId, payloadJson, sha256, payloadSha);
        } catch (Exception ex) {
            log.warn("Lỗi đồng bộ raw_dataset_record cho document: {}", ex.getMessage());
        }

        log.info("Tải lên tài liệu thành công: {} ({} bytes, key: {}, uploader: {})", originalName, file.getSize(), objectKey, username);

        return new DocumentItemDto(fileEntryId, fileEntryId, originalName, extension, file.getContentType(),
                file.getSize(), folderId, (folderId != null && !folderId.isBlank()) ? "Thư mục " + folderId : "Hồ sơ chung",
                assetRecordId, "asset_record", username, OffsetDateTime.now().toString());
    }

    /**
     * Xóa tài liệu khỏi hệ thống (yêu cầu quyền ADMIN hoặc MANAGER).
     */
    public void deleteDocument(String idOrFileEntryId, String userRole, String username) {
        if (userRole == null || (!userRole.contains("ADMIN") && !userRole.contains("MANAGER"))) {
            throw new vn.gov.drvn.kcht.exception.AccessDeniedException(
                    "Từ chối truy cập: Chỉ Quản trị viên (ADMIN) hoặc Cán bộ quản lý (MANAGER) mới có quyền xóa tài liệu"
            );
        }

        DocumentItemDto doc = getDocumentById(idOrFileEntryId);
        String key = doc.getFileEntryId() != null ? doc.getFileEntryId() : doc.getId();

        // 1. Xóa trong Object Storage
        try {
            storageService.deleteFile("documents/" + key + "." + doc.getFileExtension());
            storageService.deleteFile(key);
        } catch (Exception e) {
            log.warn("Lỗi khi xóa tệp tin khỏi StorageService: {}", e.getMessage());
        }

        // 2. Cập nhật soft delete trong CSDL
        try {
            jdbcTemplate.update("UPDATE document_metadata SET is_deleted = true WHERE file_entry_id = ?", key);
        } catch (Exception e) {
            log.warn("Không thể cập nhật soft delete tài liệu {}: {}", key, e.getMessage());
        }

        jdbcTemplate.update("DELETE FROM raw_dataset_record WHERE dataset_key = 'documents' AND (record_key = ? OR raw_payload->>'vidagis_fileentryid' = ?)", key, key);
        log.info("Người dùng {} ({}) đã xóa tài liệu {}", username, userRole, idOrFileEntryId);
    }

    /**
     * Tạo thư mục tài liệu mới trong cây phân cấp.
     */
    public DocumentFolderDto createFolder(String folderName, String parentId, String userRole) {
        if (userRole == null || (!userRole.contains("ADMIN") && !userRole.contains("MANAGER"))) {
            throw new vn.gov.drvn.kcht.exception.AccessDeniedException(
                    "Từ chối truy cập: Chỉ Quản trị viên (ADMIN) hoặc Cán bộ quản lý (MANAGER) mới có quyền tạo thư mục tài liệu"
            );
        }
        if (folderName == null || folderName.isBlank()) {
            throw new BadRequestException("Tên thư mục không được để trống");
        }

        String folderCode = "folder_" + UUID.randomUUID().toString().substring(0, 8);
        String effectiveParent = (parentId != null && !parentId.isBlank()) ? parentId : "#";

        // 1. Thêm vào document_folder nếu có parent hợp lệ
        Long parentFolderId = null;
        if (parentId != null && parentId.matches("^[0-9]+$")) {
            long pfId = Long.parseLong(parentId);
            try {
                Integer cnt = jdbcTemplate.queryForObject("SELECT count(*) FROM document_folder WHERE id = ?", Integer.class, pfId);
                if (cnt != null && cnt > 0) parentFolderId = pfId;
            } catch (Exception e) {
                log.warn("Không thể tra cứu thư mục cha {}: {}", parentId, e.getMessage());
            }
        }
        try {
            jdbcTemplate.update("""
                INSERT INTO document_folder (folder_code, folder_name, parent_folder_id, organization_id, sort_order)
                VALUES (?, ?, ?, 'moc_dbvn', 0)
                ON CONFLICT (folder_code) DO NOTHING
            """, folderCode, folderName.trim(), parentFolderId);
        } catch (Exception ex) {
            log.warn("Không thể ghi document_folder: {}", ex.getMessage());
        }

        // 2. Thêm vào dataset_registry & raw_dataset_record
        try {
            jdbcTemplate.update("""
                INSERT INTO dataset_registry (dataset_key, dataset_name, kind, source_file, total_records)
                VALUES ('document_folders', 'Cây thư mục tài liệu', 'document', 'document_folders.json', 1)
                ON CONFLICT (dataset_key) DO NOTHING
            """);

            String payload = String.format("{\"id\": \"%s\", \"text\": \"%s\", \"parent\": \"%s\", \"count\": 0}",
                    folderCode, folderName.trim(), effectiveParent);
            String payloadSha = computeSha256(payload.getBytes(StandardCharsets.UTF_8));

            jdbcTemplate.update("""
                INSERT INTO raw_dataset_record (dataset_key, record_key, raw_payload, source_file, source_sha256, payload_sha256, record_status, imported_at, updated_at)
                VALUES ('document_folders', ?, ?::jsonb, 'document_folders.json', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (dataset_key, record_key) DO UPDATE
                SET raw_payload = EXCLUDED.raw_payload, updated_at = CURRENT_TIMESTAMP
            """, folderCode, payload, payloadSha, payloadSha);
        } catch (Exception ex) {
            log.warn("Không thể ghi raw_dataset_record cho folder: {}", ex.getMessage());
        }

        log.info("Tạo thư mục tài liệu mới thành công: {} (mã: {})", folderName, folderCode);
        return new DocumentFolderDto(folderCode, folderCode, folderName.trim(), effectiveParent, 0);
    }

    private String computeSha256(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }
}
