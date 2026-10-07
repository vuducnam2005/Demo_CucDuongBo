package vn.gov.drvn.kcht.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.gov.drvn.kcht.dto.AssetCreateUpdateDto;
import vn.gov.drvn.kcht.dto.AssetDeleteRequestDto;
import vn.gov.drvn.kcht.dto.ImportExecutionRequestDto;
import vn.gov.drvn.kcht.dto.ImportPreviewRequestDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.repository.AssetRecordRepository;
import vn.gov.drvn.kcht.repository.AuditLogRepository;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://localhost:5436/kcht_db",
        "spring.datasource.username=kcht_user",
        "spring.datasource.password=${POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
@DisplayName("Kiểm thử tích hợp Giai đoạn 11 - Quản trị Dữ liệu, CRUD, Khóa lạc quan, Xóa mềm & Nạp an toàn")
class Phase11CrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AssetRecordRepository assetRecordRepository;

    @Autowired
    private RawDatasetRecordRepository rawDatasetRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void tearDown() {
        assetRecordRepository.deleteAll();
    }

    @Test
    @DisplayName("1. Phân quyền RBAC: ROLE_VIEWER bị chặn 403 Forbidden khi Create, Update, Delete và Import")
    void testViewerForbiddenOnCrud() throws Exception {
        AssetCreateUpdateDto createDto = new AssetCreateUpdateDto(
                "Biển báo thử nghiệm bởi Viewer", "QL.1", "Quốc lộ 1",
                BigDecimal.valueOf(10.0), BigDecimal.valueOf(10.1), "Km 10",
                null, "Hà Nội", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2026, null, null, null
        );

        // CREATE: 403 Forbidden
        mockMvc.perform(post("/api/datasets/tbl_road_sign/records")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // UPDATE: 403 Forbidden
        mockMvc.perform(put("/api/datasets/tbl_road_sign/records/any_record_id")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // DELETE: 403 Forbidden
        mockMvc.perform(delete("/api/datasets/tbl_road_sign/records/any_record_id")
                        .header("X-User-Role", "ROLE_VIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // IMPORT: 403 Forbidden
        ImportExecutionRequestDto importReq = new ImportExecutionRequestDto(
                "name,route_code\nTest Sign,QL.1", "CSV", false, 500
        );
        mockMvc.perform(post("/api/datasets/tbl_road_sign/import")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    @Test
    @DisplayName("2. Phân quyền RBAC: ROLE_EDITOR có quyền Create và Update, nhưng bị chặn 403 khi Delete")
    void testEditorCanCreateAndUpdateButForbiddenOnDelete() throws Exception {
        AssetCreateUpdateDto createDto = new AssetCreateUpdateDto(
                "Biển báo tạo bởi Editor", "QL.1", "Quốc lộ 1",
                BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.2), "Km 50",
                null, "Hải Dương", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2025, null, Map.of("shape_sign_id", "1"), null
        );

        // 1. Editor tạo mới thành công (201 Created)
        MvcResult createResult = mockMvc.perform(post("/api/datasets/tbl_road_sign/records")
                        .header("X-User-Role", "ROLE_EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recordKey", notNullValue()))
                .andExpect(jsonPath("$.payload.name", is("Biển báo tạo bởi Editor")))
                .andExpect(jsonPath("$.payload.version", is(1)))
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String createdRecordKey = createdNode.get("recordKey").asText();

        // 2. Editor cập nhật thông tin thành công (200 OK)
        AssetCreateUpdateDto updateDto = new AssetCreateUpdateDto(
                "Biển báo đã sửa bởi Editor", "QL.1", "Quốc lộ 1",
                BigDecimal.valueOf(50.0), BigDecimal.valueOf(50.5), "Km 50 + 500",
                null, "Hải Dương", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                BigDecimal.valueOf(15000000), 2025, null, Map.of("shape_sign_id", "2"),
                1 // version khớp
        );

        mockMvc.perform(put("/api/datasets/tbl_road_sign/records/" + createdRecordKey)
                        .header("X-User-Role", "ROLE_EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.name", is("Biển báo đã sửa bởi Editor")))
                .andExpect(jsonPath("$.payload.version", is(2)));

        // 3. Editor xóa bản ghi -> BỊ TỪ CHỐI 403 Forbidden
        mockMvc.perform(delete("/api/datasets/tbl_road_sign/records/" + createdRecordKey)
                        .header("X-User-Role", "ROLE_EDITOR")
                        .param("version", "2")
                        .param("reason", "Editor thử xóa"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        // Dọn dẹp bản ghi test (bằng ADMIN)
        assetRecordRepository.deleteById(createdNode.get("id").asLong());
    }

    @Test
    @DisplayName("3. Xóa mềm (Soft Delete): ROLE_MANAGER xóa thành công, CSDL đánh dấu is_deleted=true, tầng Raw bất biến")
    void testManagerSoftDeleteAndRawImmutability() throws Exception {
        // Tạo một bản ghi mẫu
        AssetCreateUpdateDto createDto = new AssetCreateUpdateDto(
                "Cầu thử nghiệm xóa mềm", "QL.2", "Quốc lộ 2",
                BigDecimal.valueOf(100.0), BigDecimal.valueOf(100.3), "Km 100",
                null, "Vĩnh Phúc", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2020, null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/datasets/tbl_bridge/records")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long createdId = createdNode.get("id").asLong();
        String createdKey = createdNode.get("recordKey").asText();

        // Đếm số lượng raw records trước khi xóa
        long rawCountBefore = rawDatasetRecordRepository.count();

        // Thực hiện xóa mềm bằng ROLE_MANAGER (204 No Content)
        mockMvc.perform(delete("/api/datasets/tbl_bridge/records/" + createdKey)
                        .header("X-User-Role", "ROLE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssetDeleteRequestDto(1, "Tháo dỡ theo quyết định"))))
                .andExpect(status().isNoContent());

        // Kiểm tra trong CSDL: bản ghi vẫn tồn tại nhưng is_deleted = true
        Optional<AssetRecordEntity> dbRecord = assetRecordRepository.findById(createdId);
        assertTrue(dbRecord.isPresent());
        assertTrue(dbRecord.get().getIsDeleted());
        assertNotNull(dbRecord.get().getDeletedAt());

        // Kiểm tra qua API đọc getRecords: bản ghi đã bị loại trừ khỏi danh sách hiển thị
        mockMvc.perform(get("/api/datasets/tbl_bridge/records")
                        .header("X-User-Role", "ROLE_VIEWER")
                        .param("q", "Cầu thử nghiệm xóa mềm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Bảng RAW tuyệt đối không bị suy giảm số lượng dòng
        long rawCountAfter = rawDatasetRecordRepository.count();
        assertEquals(rawCountBefore, rawCountAfter);

        // Dọn dẹp
        assetRecordRepository.deleteById(createdId);
    }

    @Test
    @DisplayName("4. Khóa lạc quan (Optimistic Locking): Sửa hoặc xóa với version cũ trả về 409 Conflict")
    void testOptimisticLockingConflictReturns409() throws Exception {
        AssetCreateUpdateDto createDto = new AssetCreateUpdateDto(
                "Biển báo kiểm tra Khóa lạc quan", "QL.5", "Quốc lộ 5",
                BigDecimal.valueOf(15.0), BigDecimal.valueOf(15.1), "Km 15",
                null, "Hải Dương", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2024, null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/datasets/tbl_road_sign/records")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode createdNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long id = createdNode.get("id").asLong();
        String recordKey = createdNode.get("recordKey").asText();

        // Lần cập nhật 1: version 1 -> thành công, version tăng lên 2
        AssetCreateUpdateDto update1 = new AssetCreateUpdateDto(
                "Biển báo cập nhật lần 1", "QL.5", "Quốc lộ 5",
                BigDecimal.valueOf(15.0), BigDecimal.valueOf(15.1), "Km 15",
                null, "Hải Dương", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2024, null, null, 1
        );
        mockMvc.perform(put("/api/datasets/tbl_road_sign/records/" + recordKey)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.version", is(2)));

        // Lần cập nhật 2: gửi kèm version 1 cũ -> XUNG ĐỘT 409 CONFLICT
        AssetCreateUpdateDto updateConflicted = new AssetCreateUpdateDto(
                "Biển báo cập nhật phiên bản lỗi", "QL.5", "Quốc lộ 5",
                BigDecimal.valueOf(15.0), BigDecimal.valueOf(15.1), "Km 15",
                null, "Hải Dương", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2024, null, null, 1 // version 1 bị lệch so với 2 trong DB!
        );
        mockMvc.perform(put("/api/datasets/tbl_road_sign/records/" + recordKey)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateConflicted)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("Xung đột phiên bản")));

        // Thử xóa với version 1 cũ -> XUNG ĐỘT 409 CONFLICT
        mockMvc.perform(delete("/api/datasets/tbl_road_sign/records/" + recordKey)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .param("version", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));

        // Dọn dẹp
        assetRecordRepository.deleteById(id);
    }

    @Test
    @DisplayName("5. Nhập CSV/JSON: Xem trước (Preview) và Chạy thử (Dry-run) không ghi CSDL")
    void testImportPreviewAndDryRun() throws Exception {
        String validCsv = "name,route_code,km_from,km_to,branch_id\n" +
                "Biển P.101 Cấm xe thô sơ,QL.1,10.0,10.5,kqldb_1\n" +
                "Biển P.102 Cấm đi ngược chiều,QL.1,11.0,11.2,kqldb_1";

        // 1. Preview thành công
        ImportPreviewRequestDto previewReq = new ImportPreviewRequestDto(validCsv, "CSV");
        mockMvc.perform(post("/api/datasets/tbl_road_sign/import/preview")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(2)))
                .andExpect(jsonPath("$.validRows", is(2)))
                .andExpect(jsonPath("$.errorRows", is(0)))
                .andExpect(jsonPath("$.canProceed", is(true)))
                .andExpect(jsonPath("$.previewRows", hasSize(2)));

        // 2. Chạy thử Dry-run
        long countBefore = assetRecordRepository.count();
        ImportExecutionRequestDto dryRunReq = new ImportExecutionRequestDto(validCsv, "CSV", true, 500);

        mockMvc.perform(post("/api/datasets/tbl_road_sign/import")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dryRunReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun", is(true)))
                .andExpect(jsonPath("$.totalProcessed", is(2)))
                .andExpect(jsonPath("$.rolledBack", is(false)))
                .andExpect(jsonPath("$.message", containsString("Dry-run")));

        // CSDL giữ nguyên không đổi
        assertEquals(countBefore, assetRecordRepository.count());
    }

    @Test
    @DisplayName("6. Nhập dữ liệu theo lô: Lỗi validation hoặc ràng buộc dừng lại an toàn")
    void testImportValidationFailure() throws Exception {
        // Tệp CSV chứa lỗi: thiếu name ở dòng 1 và km_from > km_to ở dòng 2
        String invalidCsv = "name,route_code,km_from,km_to\n" +
                ",QL.1,10.0,10.5\n" +
                "Biển báo sai km,QL.1,20.0,15.0";

        ImportExecutionRequestDto req = new ImportExecutionRequestDto(invalidCsv, "CSV", false, 500);

        mockMvc.perform(post("/api/datasets/tbl_road_sign/import")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount", greaterThan(0)))
                .andExpect(jsonPath("$.errorMessages", not(empty())));
    }

    @Test
    @DisplayName("7. Audit Log: Thao tác Thêm mới và Xóa ghi nhận đầy đủ nhật ký kiểm toán")
    void testAuditLogRecordedForCrud() throws Exception {
        AssetCreateUpdateDto createDto = new AssetCreateUpdateDto(
                "Biển báo kiểm tra Audit Log", "QL.3", "Quốc lộ 3",
                BigDecimal.valueOf(25.0), BigDecimal.valueOf(25.2), "Km 25",
                null, "Thái Nguyên", null, null, null, null, null,
                "kqldb_1", null, null, "Approved", null, null, null,
                null, 2026, null, null, null
        );

        // Tạo bản ghi
        MvcResult res = mockMvc.perform(post("/api/datasets/tbl_road_sign/records")
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = objectMapper.readTree(res.getResponse().getContentAsString());
        Long id = created.get("id").asLong();
        String recordKey = created.get("recordKey").asText();

        // Xóa bản ghi
        mockMvc.perform(delete("/api/datasets/tbl_road_sign/records/" + recordKey)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .param("version", "1")
                        .param("reason", "Kiểm toán thao tác xóa"))
                .andExpect(status().isNoContent());

        // Kiểm tra audit_log có bản ghi ASSET_CREATE và ASSET_DELETE
        boolean hasCreateLog = auditLogRepository.findAll().stream()
                .anyMatch(a -> "ASSET_CREATE".equals(a.getAction()) && recordKey.equals(a.getEntityId()));
        boolean hasDeleteLog = auditLogRepository.findAll().stream()
                .anyMatch(a -> "ASSET_DELETE".equals(a.getAction()) && recordKey.equals(a.getEntityId()));

        assertTrue(hasCreateLog, "Audit Log phải ghi nhận hành động ASSET_CREATE");
        assertTrue(hasDeleteLog, "Audit Log phải ghi nhận hành động ASSET_DELETE");

        // Dọn dẹp
        assetRecordRepository.deleteById(id);
    }
}
