package vn.gov.drvn.kcht.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.dto.DocumentItemDto;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.security.UserPrincipal;
import vn.gov.drvn.kcht.storage.StorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

@RestController
@RequestMapping("/api/local-documents")
public class LocalDocumentController {
    private static final String CENTRAL = "moc_dbvn";
    private static final Set<String> EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "dwg", "jpg", "jpeg", "png", "zip");
    private final JdbcTemplate database;
    private final StorageService storage;
    private final TransactionTemplate transaction;

    public LocalDocumentController(JdbcTemplate database, StorageService storage,
                                   TransactionTemplate transaction) {
        this.database = database;
        this.storage = storage;
        this.transaction = transaction;
    }

    @GetMapping("/folders")
    public List<Folder> folders(@AuthenticationPrincipal UserPrincipal principal) {
        requireReader(principal);
        return database.query("""
                SELECT f.id, f.folder_code, f.folder_name, f.parent_folder_id, f.organization_id,
                       (SELECT COUNT(*) FROM document_metadata d
                        WHERE d.folder_id = f.id AND NOT d.is_deleted) AS document_count
                FROM document_folder f
                WHERE NOT f.is_deleted AND (? = 'ROLE_ADMIN' OR f.organization_id = ?)
                ORDER BY f.sort_order, f.id
                """, (row, index) -> new Folder(row.getString("id"),
                row.getString("folder_code"), row.getString("folder_name"),
                row.getObject("parent_folder_id") == null ? "#" : row.getString("parent_folder_id"),
                row.getInt("document_count"), row.getString("organization_id")),
                principal.getRoleCode(), scope(principal));
    }

    public record Folder(String id, String folderCode, String folderName, String parentId,
                         int documentCount, String branchId) {}

    private Folder lockedFolder(UserPrincipal principal, long id) {
        return database.query("""
                SELECT f.id, f.folder_code, f.folder_name, f.parent_folder_id, f.organization_id,
                       (SELECT COUNT(*) FROM document_metadata d
                        WHERE d.folder_id = f.id AND NOT d.is_deleted) AS document_count
                FROM document_folder f
                WHERE f.id = ? AND NOT f.is_deleted
                  AND (? = 'ROLE_ADMIN' OR f.organization_id = ?)
                FOR UPDATE OF f
                """, (row, index) -> new Folder(row.getString("id"),
                row.getString("folder_code"), row.getString("folder_name"),
                row.getObject("parent_folder_id") == null ? "#" : row.getString("parent_folder_id"),
                row.getInt("document_count"), row.getString("organization_id")),
                id, principal.getRoleCode(), scope(principal)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/branches")
    public List<String> branches(@AuthenticationPrincipal UserPrincipal principal) {
        requireReader(principal);
        if (!"ROLE_ADMIN".equals(principal.getRoleCode())) return List.of(scope(principal));
        List<String> available = new ArrayList<>(List.of(CENTRAL));
        available.addAll(database.query("""
                SELECT DISTINCT branch_id FROM app_user
                WHERE branch_id IS NOT NULL AND branch_id <> '' AND is_active
                ORDER BY branch_id
                """, (row, index) -> row.getString(1)));
        return available;
    }

    @GetMapping
    public PagedResponse<DocumentItemDto> documents(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String folderId, @RequestParam(required = false) String q,
            @RequestParam(required = false) String extension, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireReader(principal);
        if (page < 0 || page > 10000 || size < 1 || size > 100
                || (q != null && q.length() > 200)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        StringBuilder where = new StringBuilder(" WHERE NOT d.is_deleted AND (? = 'ROLE_ADMIN' OR d.branch_id = ?)");
        List<Object> params = new ArrayList<>(List.of(principal.getRoleCode(), scope(principal)));
        if (folderId != null && !folderId.isBlank()) {
            long id = positiveId(folderId);
            where.append(" AND d.folder_id = ?");
            params.add(id);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (d.original_name ILIKE ? OR d.asset_record_id ILIKE ?)");
            String search = "%" + q.trim() + "%";
            params.add(search);
            params.add(search);
        }
        if (extension != null && !extension.isBlank()) {
            String value = extension.toLowerCase().replaceFirst("^[.]", "");
            if (!EXTENSIONS.contains(value)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            where.append(" AND d.file_extension = ?");
            params.add(value);
        }
        String from = " FROM document_metadata d LEFT JOIN document_folder f ON d.folder_id = f.id";
        Long total = database.queryForObject("SELECT COUNT(*)" + from + where, Long.class, params.toArray());
        params.add(size);
        params.add(page * size);
        List<DocumentItemDto> items = database.query("""
                SELECT d.id, d.file_entry_id, d.original_name, d.file_extension,
                       d.mime_type, d.file_size, d.folder_id, f.folder_name, d.asset_record_id,
                       d.uploader_username, d.created_at
                """ + from + where + " ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?",
                (row, index) -> new DocumentItemDto(row.getString("id"), row.getString("file_entry_id"),
                        row.getString("original_name"), row.getString("file_extension"),
                        row.getString("mime_type"), row.getLong("file_size"),
                        row.getString("folder_id"), row.getString("folder_name"),
                        row.getString("asset_record_id"), "asset_record", row.getString("uploader_username"),
                        row.getString("created_at")), params.toArray());
        return new PagedResponse<>(items, page, size, total == null ? 0 : total);
    }

    @GetMapping("/{id}")
    public DocumentItemDto document(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String id) {
        requireReader(principal);
        long documentId = positiveId(id);
        return database.query("""
                SELECT d.id, d.file_entry_id, d.original_name, d.file_extension,
                       d.mime_type, d.file_size, d.folder_id, f.folder_name, d.asset_record_id,
                       d.uploader_username, d.created_at
                FROM document_metadata d LEFT JOIN document_folder f ON d.folder_id = f.id
                WHERE d.id = ? AND NOT d.is_deleted AND (? = 'ROLE_ADMIN' OR d.branch_id = ?)
                """, (row, index) -> new DocumentItemDto(row.getString("id"), row.getString("file_entry_id"),
                        row.getString("original_name"), row.getString("file_extension"),
                        row.getString("mime_type"), row.getLong("file_size"),
                        row.getString("folder_id"), row.getString("folder_name"),
                        row.getString("asset_record_id"), "asset_record", row.getString("uploader_username"),
                        row.getString("created_at")), documentId, principal.getRoleCode(), scope(principal))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String id) throws IOException {
        requireReader(principal);
        long documentId = positiveId(id);
        StoredDocument stored = database.query("""
                SELECT original_name, local_path, mime_type, sha256 FROM document_metadata
                WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR branch_id = ?)
                """, (row, index) -> new StoredDocument(row.getString("original_name"),
                row.getString("local_path"), row.getString("mime_type"), row.getString("sha256")),
                documentId, principal.getRoleCode(), scope(principal)).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return downloadStored(stored);
    }

    private ResponseEntity<byte[]> downloadStored(StoredDocument stored) throws IOException {
        if (!stored.objectKey().matches("documents/doc_[a-f0-9]{32}\\.(pdf|doc|docx|xls|xlsx|dwg|jpg|jpeg|png|zip)")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        InputStream stream = storage.downloadFile(stored.objectKey())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa lưu tệp gốc"));
        byte[] bytes;
        try (stream) {
            bytes = stream.readNBytes(25 * 1024 * 1024 + 1);
        }
        if (bytes.length > 25 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!sha256(bytes).equals(stored.sha256().trim())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(stored.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(stored.mimeType())).body(bytes);
    }

    private record StoredDocument(String name, String objectKey, String mimeType, String sha256) {}

    public record DocumentVersion(int versionNumber, String fileName, long fileSize, String uploadedBy,
                                  String changeNote, OffsetDateTime uploadedAt) {}

    @GetMapping("/{id}/versions")
    public List<DocumentVersion> versions(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable String id) {
        requireReader(principal);
        long documentId = positiveId(id);
        document(principal, id);
        return database.query("""
                SELECT v.version_number, v.file_name, v.file_size, v.uploaded_by,
                       v.change_note, v.uploaded_at
                FROM document_version v JOIN document_metadata d ON d.id = v.document_id
                WHERE v.document_id = ? AND NOT d.is_deleted
                  AND (? = 'ROLE_ADMIN' OR d.branch_id = ?)
                ORDER BY v.version_number DESC
                """, (row, index) -> new DocumentVersion(row.getInt("version_number"),
                row.getString("file_name"), row.getLong("file_size"), row.getString("uploaded_by"),
                row.getString("change_note"), row.getObject("uploaded_at", OffsetDateTime.class)),
                documentId, principal.getRoleCode(), scope(principal));
    }

    @GetMapping("/{id}/versions/{version}/file")
    public ResponseEntity<byte[]> downloadVersion(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable String id, @PathVariable String version)
            throws IOException {
        requireReader(principal);
        long documentId = positiveId(id);
        int versionNumber = versionNumber(version);
        StoredDocument stored = database.query("""
                SELECT v.file_name, v.object_key, v.mime_type, v.sha256
                FROM document_version v JOIN document_metadata d ON d.id = v.document_id
                WHERE v.document_id = ? AND v.version_number = ? AND NOT d.is_deleted
                  AND (? = 'ROLE_ADMIN' OR d.branch_id = ?)
                """, (row, index) -> new StoredDocument(row.getString("file_name"),
                row.getString("object_key"), row.getString("mime_type"), row.getString("sha256")),
                documentId, versionNumber, principal.getRoleCode(), scope(principal))
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return downloadStored(stored);
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentVersion replace(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String id,
                                   @RequestPart("file") MultipartFile file,
                                   @RequestParam(required = false) String note) throws IOException {
        requireWriter(principal, false);
        long documentId = positiveId(id);
        if (file == null || file.isEmpty() || file.getSize() > 25L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp rỗng hoặc vượt quá 25 MiB");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 500 || name.contains("/")
                || name.indexOf((char) 92) >= 0 || name.contains("\r") || name.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        if (!EXTENSIONS.contains(extension) || (note != null && note.length() > 1000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng hoặc ghi chú không hợp lệ");
        }
        document(principal, id);
        byte[] content = file.getBytes();
        if (content.length == 0 || content.length > 25L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp rỗng hoặc vượt quá 25 MiB");
        }
        return persistVersion(principal, documentId, name, extension, content, note);
    }

    private DocumentVersion persistVersion(UserPrincipal principal, long documentId, String name,
                                           String extension, byte[] content, String note) {
        String objectKey = "documents/doc_" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String mediaType = mediaType(extension);
        String hash = sha256(content);
        String changeNote = note == null || note.isBlank() ? null : note.trim();
        try {
            return transaction.execute(status -> {
                List<String> found = database.query("""
                        SELECT file_extension FROM document_metadata
                        WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR branch_id = ?)
                        FOR UPDATE
                        """, (row, index) -> row.getString(1), documentId,
                        principal.getRoleCode(), scope(principal));
                if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                if (!extension.equals(found.get(0))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Phiên bản mới phải có cùng định dạng với hồ sơ gốc");
                }
                int next = database.queryForObject("""
                        SELECT COALESCE(MAX(version_number), 0) + 1 FROM document_version
                        WHERE document_id = ?
                        """, Integer.class, documentId);
                storage.uploadFile(objectKey, new ByteArrayInputStream(content), content.length, mediaType);
                database.update("""
                        INSERT INTO document_version(document_id, version_number, file_name, object_key,
                                                     mime_type, file_size, sha256, uploaded_by, change_note)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, documentId, next, name, objectKey, mediaType, content.length,
                        hash, principal.getUsername(), changeNote);
                database.update("""
                        UPDATE document_metadata SET original_name = ?, file_size = ?, mime_type = ?,
                            local_path = ?, sha256 = ?, uploader_username = ?, uploaded_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, name, content.length, mediaType, objectKey, hash,
                        principal.getUsername(), documentId);
                database.update("""
                        INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                        VALUES (?, ?, 'UPDATE_VERSION', 'DOCUMENT', ?,
                                jsonb_build_object('version', ?, 'name', ?))
                        """, principal.getId(), principal.getUsername(), Long.toString(documentId), next, name);
                return database.queryForObject("""
                        SELECT version_number, file_name, file_size, uploaded_by, change_note, uploaded_at
                        FROM document_version WHERE document_id = ? AND version_number = ?
                        """, (row, index) -> new DocumentVersion(row.getInt("version_number"),
                        row.getString("file_name"), row.getLong("file_size"), row.getString("uploaded_by"),
                        row.getString("change_note"), row.getObject("uploaded_at", OffsetDateTime.class)),
                        documentId, next);
            });
        } catch (RuntimeException failure) {
            storage.deleteFile(objectKey);
            throw failure;
        }
    }

    public record RenameRequest(String name) {}

    @PatchMapping("/{id}")
    public DocumentItemDto rename(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable String id, @RequestBody RenameRequest request) {
        requireWriter(principal, true);
        long documentId = positiveId(id);
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.length() < 2 || name.length() > 500 || name.indexOf((char) 92) >= 0
                || name.contains("/") || name.contains("\r") || name.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên hồ sơ không hợp lệ");
        }
        transaction.executeWithoutResult(status -> {
            List<String[]> found = database.query("""
                    SELECT original_name, file_extension FROM document_metadata
                    WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR branch_id = ?)
                    FOR UPDATE
                    """, (row, index) -> new String[]{row.getString(1), row.getString(2)},
                    documentId, principal.getRoleCode(), scope(principal));
            if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            String extension = found.get(0)[1];
            if (!name.toLowerCase().endsWith("." + extension)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không được đổi định dạng tệp");
            }
            database.update("UPDATE document_metadata SET original_name = ? WHERE id = ?", name, documentId);
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, old_values, new_values)
                    VALUES (?, ?, 'UPDATE', 'DOCUMENT', ?, jsonb_build_object('name', ?),
                            jsonb_build_object('name', ?))
                    """, principal.getId(), principal.getUsername(), id, found.get(0)[0], name);
        });
        return document(principal, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable String id) {
        requireWriter(principal, true);
        long documentId = positiveId(id);
        transaction.executeWithoutResult(status -> {
            List<String> deleted = database.query("""
                    UPDATE document_metadata SET is_deleted = TRUE
                    WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR branch_id = ?)
                    RETURNING original_name
                    """, (row, index) -> row.getString(1), documentId,
                    principal.getRoleCode(), scope(principal));
            if (deleted.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, old_values)
                    VALUES (?, ?, 'DELETE', 'DOCUMENT', ?, jsonb_build_object('name', ?))
                    """, principal.getId(), principal.getUsername(), id, deleted.get(0));
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folders")
    public Folder createFolder(@AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String folderName, @RequestParam(required = false) String parentId,
            @RequestParam(required = false) String branchId) {
        requireWriter(principal, true);
        String name = folderName == null ? "" : folderName.trim();
        if (name.length() < 2 || name.length() > 255) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String owner = scope(principal);
        Long parent = null;
        if (parentId != null && !parentId.isBlank()) {
            parent = positiveId(parentId);
            String target = database.query("""
                    SELECT organization_id FROM document_folder
                    WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR organization_id = ?)
                    """, (row, index) -> row.getString(1), parent,
                    principal.getRoleCode(), scope(principal)).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            owner = target;
        }
        if (branchId != null && !branchId.isBlank() && !branchId.equals(owner)) {
            if (!"ROLE_ADMIN".equals(principal.getRoleCode()) || parent != null
                    || branchId.length() > 100 || !branchId.matches("[a-zA-Z0-9_-]+")
                    || !Boolean.TRUE.equals(database.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM app_user WHERE is_active AND branch_id = ?)",
                            Boolean.class, branchId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn vị không hợp lệ");
            }
            owner = branchId;
        }
        String code = "folder_" + UUID.randomUUID().toString().replace("-", "");
        String folderOwner = owner;
        Long parentFolder = parent;
        return transaction.execute(status -> {
            Long id = database.queryForObject("""
                    INSERT INTO document_folder(folder_code, folder_name, parent_folder_id, organization_id)
                    VALUES (?, ?, ?, ?) RETURNING id
                    """, Long.class, code, name, parentFolder, folderOwner);
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                    VALUES (?, ?, 'CREATE', 'DOCUMENT_FOLDER', ?, jsonb_build_object('name', ?))
                    """, principal.getId(), principal.getUsername(), String.valueOf(id), name);
            return new Folder(String.valueOf(id), code, name,
                    parentFolder == null ? "#" : String.valueOf(parentFolder), 0, folderOwner);
        });
    }

    @PatchMapping("/folders/{id}")
    public Folder renameFolder(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable String id, @RequestBody RenameRequest request) {
        requireWriter(principal, true);
        long folderId = positiveId(id);
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.length() < 2 || name.length() > 255 || name.contains("/")
                || name.indexOf((char) 92) >= 0 || name.contains("\r") || name.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên thư mục không hợp lệ");
        }
        return transaction.execute(status -> {
            Folder previous = lockedFolder(principal, folderId);
            if (name.equals(previous.folderName())) return previous;
            database.update("UPDATE document_folder SET folder_name = ? WHERE id = ?", name, folderId);
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, old_values, new_values)
                    VALUES (?, ?, 'UPDATE', 'DOCUMENT_FOLDER', ?, jsonb_build_object('name', ?),
                            jsonb_build_object('name', ?))
                    """, principal.getId(), principal.getUsername(), id, previous.folderName(), name);
            return new Folder(id, previous.folderCode(), name, previous.parentId(),
                    previous.documentCount(), previous.branchId());
        });
    }

    @DeleteMapping("/folders/{id}")
    public ResponseEntity<Void> deleteFolder(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable String id) {
        requireWriter(principal, true);
        long folderId = positiveId(id);
        transaction.execute(status -> {
            Folder folder = lockedFolder(principal, folderId);
            Boolean hasContents = database.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM document_folder child
                                   WHERE child.parent_folder_id = ? AND NOT child.is_deleted)
                        OR EXISTS (SELECT 1 FROM document_metadata document
                                   WHERE document.folder_id = ? AND NOT document.is_deleted)
                    """, Boolean.class, folderId, folderId);
            if (Boolean.TRUE.equals(hasContents)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể xóa thư mục trống");
            }
            database.update("UPDATE document_folder SET is_deleted = TRUE WHERE id = ?", folderId);
            database.update("""
                    INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, old_values)
                    VALUES (?, ?, 'DELETE', 'DOCUMENT_FOLDER', ?, jsonb_build_object('name', ?))
                    """, principal.getId(), principal.getUsername(), id, folder.folderName());
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentItemDto upload(@AuthenticationPrincipal UserPrincipal principal,
            @RequestPart("file") MultipartFile file, @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String assetRecordId,
            @RequestParam(required = false) String branchId) throws IOException {
        requireWriter(principal, false);
        if (file == null || file.isEmpty() || file.getSize() > 25L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp rỗng hoặc vượt quá 25 MiB");
        }
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 500 || name.contains("/")
                || name.indexOf((char) 92) >= 0 || name.contains("\r") || name.contains("\n")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên tệp không hợp lệ");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        if (!EXTENSIONS.contains(extension)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String owner = scope(principal);
        Long folder = null;
        if (folderId != null && !folderId.isBlank()) {
            folder = positiveId(folderId);
            owner = database.query("""
                    SELECT organization_id FROM document_folder
                    WHERE id = ? AND NOT is_deleted AND (? = 'ROLE_ADMIN' OR organization_id = ?)
                    """, (row, index) -> row.getString(1), folder,
                    principal.getRoleCode(), scope(principal)).stream().findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        if (branchId != null && !branchId.isBlank() && !branchId.equals(owner)) {
            if (!"ROLE_ADMIN".equals(principal.getRoleCode()) || folder != null
                    || branchId.length() > 100 || !branchId.matches("[a-zA-Z0-9_-]+")
                    || !Boolean.TRUE.equals(database.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM app_user WHERE branch_id = ?)", Boolean.class, branchId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn vị không hợp lệ");
            }
            owner = branchId;
        }
        if (assetRecordId != null && (assetRecordId.length() > 150 || assetRecordId.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        byte[] content = file.getBytes();
        if (content.length == 0 || content.length > 25L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return persist(principal, name, extension, owner, folder, assetRecordId, content);
    }

    private DocumentItemDto persist(UserPrincipal principal, String name, String extension, String owner,
                                    Long folder, String assetRecordId, byte[] content) {
        String entryId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        String objectKey = "documents/" + entryId + "." + extension;
        String mediaType = mediaType(extension);
        String hash = sha256(content);
        storage.uploadFile(objectKey, new ByteArrayInputStream(content), content.length, mediaType);
        try {
            Long id = transaction.execute(status -> {
                Long created = database.queryForObject("""
                        INSERT INTO document_metadata(file_entry_id, original_name, file_extension,
                            mime_type, file_size, local_path, sha256, folder_id, asset_record_id,
                            organization_id, branch_id, uploader_username, is_public, is_deleted, uploaded_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, FALSE, CURRENT_TIMESTAMP)
                        RETURNING id
                        """, Long.class, entryId, name, extension, mediaType, content.length, objectKey,
                        hash, folder, assetRecordId, CENTRAL, owner, principal.getUsername());
                database.update("""
                        INSERT INTO document_version(document_id, version_number, file_name, object_key,
                                                     mime_type, file_size, sha256, uploaded_by)
                        VALUES (?, 1, ?, ?, ?, ?, ?, ?)
                        """, created, name, objectKey, mediaType, content.length, hash, principal.getUsername());
                database.update("""
                        INSERT INTO audit_log(user_id, username, action, entity_type, entity_id, new_values)
                        VALUES (?, ?, 'CREATE', 'DOCUMENT', ?, jsonb_build_object('name', ?, 'branch', ?))
                        """, principal.getId(), principal.getUsername(), String.valueOf(created), name, owner);
                return created;
            });
            return document(principal, String.valueOf(id));
        } catch (RuntimeException failure) {
            storage.deleteFile(objectKey);
            throw failure;
        }
    }

    private String mediaType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/octet-stream";
        };
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private int versionNumber(String version) {
        long value = positiveId(version);
        if (value > Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return (int) value;
    }

    private long positiveId(String input) {
        if (!input.matches("[1-9][0-9]{0,17}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return Long.parseLong(input);
    }

    private void requireReader(UserPrincipal principal) {
        if (principal == null || (!"ROLE_ADMIN".equals(principal.getRoleCode())
                && (principal.getBranchId() == null || principal.getBranchId().isBlank()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void requireWriter(UserPrincipal principal, boolean managerOnly) {
        requireReader(principal);
        if (!"ROLE_ADMIN".equals(principal.getRoleCode())
                && !"ROLE_MANAGER".equals(principal.getRoleCode())
                && (managerOnly || !"ROLE_EDITOR".equals(principal.getRoleCode()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private String scope(UserPrincipal principal) {
        return "ROLE_ADMIN".equals(principal.getRoleCode()) ? CENTRAL : principal.getBranchId();
    }
}
