package vn.gov.drvn.kcht;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.adapter.AssetQueryAdapter;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.*;
import vn.gov.drvn.kcht.repository.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:postgresql://localhost:5436/kcht_db",
        "spring.datasource.username=kcht_user",
        "spring.datasource.password=${POSTGRES_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=none"
})
public class DatabaseHandoffIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHandoffIntegrationTest.class);

    @Autowired
    private AssetRecordRepository assetRecordRepository;

    @Autowired
    private AssetGeometryRepository assetGeometryRepository;

    @Autowired
    private ReferenceCatalogRepository referenceCatalogRepository;

    @Autowired
    private DocumentFolderRepository documentFolderRepository;

    @Autowired
    private DocumentMetadataRepository documentMetadataRepository;

    @Autowired
    private AppRoleRepository appRoleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AssetQueryAdapter assetQueryAdapter;

    private static final String TEST_PREFIX = "test_handoff_";

    @AfterEach
    void cleanUp() {
        // Dọn dẹp dữ liệu test để đảm bảo tính cô lập
        try {
            List<DocumentMetadataEntity> docs = documentMetadataRepository.findByAssetRecordIdAndIsDeletedFalse(TEST_PREFIX + "bridge_01");
            documentMetadataRepository.deleteAll(docs);

            Optional<DocumentFolderEntity> folder = documentFolderRepository.findByFolderCodeAndIsDeletedFalse(TEST_PREFIX + "folder");
            folder.ifPresent(documentFolderRepository::delete);

            Optional<ReferenceCatalogEntity> cat = referenceCatalogRepository.findByCatalogCodeAndItemCodeAndIsDeletedFalse(TEST_PREFIX + "cat", "01");
            cat.ifPresent(referenceCatalogRepository::delete);

            Optional<AssetRecordEntity> asset = assetRecordRepository.findByRecordId(TEST_PREFIX + "bridge_01");
            if (asset.isPresent()) {
                assetGeometryRepository.deleteByAssetId(asset.get().getId());
                assetRecordRepository.delete(asset.get());
            }

            Optional<AppUserEntity> user = appUserRepository.findByUsername(TEST_PREFIX + "admin");
            user.ifPresent(appUserRepository::delete);

            Optional<AppRoleEntity> role = appRoleRepository.findByRoleCode(TEST_PREFIX + "ROLE_TEST");
            role.ifPresent(appRoleRepository::delete);
        } catch (Exception e) {
            log.warn("Không thể dọn dữ liệu test handoff: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("1. AssetRecord: CRUD, JSONB, Phiên bản Version và Xóa mềm")
    void testAssetRecordCrudAndVersioning() {
        // 1. Tạo mới
        AssetRecordEntity asset = new AssetRecordEntity();
        asset.setRecordId(TEST_PREFIX + "bridge_01");
        asset.setGid(999001L);
        asset.setDatasetCode("tbl_bridge");
        asset.setAssetType("BRIDGE");
        asset.setName("Cầu Thử Nghiệm Handoff");
        asset.setRouteCode("QL.1");
        asset.setKmFrom(new BigDecimal("100.500"));
        asset.setKmTo(new BigDecimal("100.650"));
        asset.setLytrinh("Km 100 + 500");
        asset.setProvinceName("Tỉnh Lạng Sơn");
        asset.setBranchId("kqldb_1");
        asset.setState("Approved");
        asset.setAttributes("{\"material\": \"Be tong cot thep\", \"lanes\": 4}");

        AssetRecordEntity saved = assetRecordRepository.save(asset);
        assertNotNull(saved.getId());
        assertEquals(1, saved.getVersion());
        assertFalse(saved.getIsDeleted());

        // 2. Tra cứu
        Optional<AssetRecordEntity> found = assetRecordRepository.findByRecordIdAndIsDeletedFalse(TEST_PREFIX + "bridge_01");
        assertTrue(found.isPresent());
        assertEquals("Cầu Thử Nghiệm Handoff", found.get().getName());
        assertTrue(found.get().getAttributes().contains("Be tong cot thep"));

        // 3. Cập nhật và kiểm tra tăng phiên bản (Optimistic Locking)
        found.get().setName("Cầu Thử Nghiệm Handoff Đã Nâng Cấp");
        AssetRecordEntity updated = assetRecordRepository.saveAndFlush(found.get());
        assertEquals(2, updated.getVersion());
        assertEquals("Cầu Thử Nghiệm Handoff Đã Nâng Cấp", updated.getName());

        // 4. Xóa mềm
        int affected = assetRecordRepository.softDeleteById(updated.getId(), OffsetDateTime.now(), "tester");
        assertEquals(1, affected);

        Optional<AssetRecordEntity> afterDelete = assetRecordRepository.findByRecordIdAndIsDeletedFalse(TEST_PREFIX + "bridge_01");
        assertTrue(afterDelete.isEmpty(), "Bản ghi đã xóa mềm không được xuất hiện trong truy vấn thông thường");
    }

    @Test
    @DisplayName("2. AssetGeometry: Liên kết 1-1 với AssetRecord và Bounding Box")
    void testAssetGeometryLink() {
        AssetRecordEntity asset = new AssetRecordEntity();
        asset.setRecordId(TEST_PREFIX + "bridge_01");
        asset.setDatasetCode("tbl_bridge");
        asset.setAssetType("BRIDGE");
        asset.setName("Cầu Đo Tọa Độ");
        AssetRecordEntity savedAsset = assetRecordRepository.save(asset);

        int inserted = assetGeometryRepository.upsertPointGeometry(
                savedAsset.getId(),
                new BigDecimal("106.75841200"),
                new BigDecimal("21.94231500")
        );
        assertEquals(1, inserted);

        Optional<AssetGeometryEntity> foundGeom = assetGeometryRepository.findByAssetId(savedAsset.getId());
        assertTrue(foundGeom.isPresent());
        assertEquals("POINT", foundGeom.get().getGeomType());
        assertEquals(new BigDecimal("106.75841200"), foundGeom.get().getBboxXmin());

        // Kiểm tra truy vấn không gian PostGIS theo Bounding Box
        var spatialList = assetGeometryRepository.findByDatasetAndBoundingBox(
                "tbl_bridge", 106.0, 21.0, 107.0, 22.0, 10
        );
        assertFalse(spatialList.isEmpty(), "Truy vấn BBOX PostGIS phải tìm thấy hình học vừa tạo");
        assertEquals(TEST_PREFIX + "bridge_01", spatialList.get(0).getRecordId());
        assertNotNull(spatialList.get(0).getGeoJson());
        assertTrue(spatialList.get(0).getGeoJson().contains("Point"));
    }

    @Test
    @DisplayName("3. ReferenceCatalog: Danh mục chuẩn hóa và JSONB extra_attributes")
    void testReferenceCatalog() {
        ReferenceCatalogEntity cat = new ReferenceCatalogEntity();
        cat.setCatalogCode(TEST_PREFIX + "cat");
        cat.setItemCode("01");
        cat.setItemName("Cấp I Đồng Bằng");
        cat.setSortOrder(1);
        cat.setIsActive(true);
        cat.setExtraAttributes("{\"toc_do_thiet_ke\": 120, \"be_rong_mat\": 24.5}");

        ReferenceCatalogEntity saved = referenceCatalogRepository.save(cat);
        assertNotNull(saved.getId());

        Optional<ReferenceCatalogEntity> found = referenceCatalogRepository.findByCatalogCodeAndItemCodeAndIsDeletedFalse(
                TEST_PREFIX + "cat", "01"
        );
        assertTrue(found.isPresent());
        assertEquals("Cấp I Đồng Bằng", found.get().getItemName());
        assertTrue(found.get().getExtraAttributes().contains("120"));
    }

    @Test
    @DisplayName("4. DocumentFolder & DocumentMetadata: Quản lý thư mục và hồ sơ")
    void testDocumentFolderAndMetadata() {
        DocumentFolderEntity folder = new DocumentFolderEntity(
                TEST_PREFIX + "folder",
                "Thư mục Hồ sơ Cầu",
                null,
                "moc_dbvn",
                1
        );
        DocumentFolderEntity savedFolder = documentFolderRepository.save(folder);
        assertNotNull(savedFolder.getId());

        DocumentMetadataEntity doc = new DocumentMetadataEntity();
        doc.setFileEntryId(TEST_PREFIX + "file_999");
        doc.setOriginalName("HoSoHoanCong_CauKyCung.pdf");
        doc.setFileExtension("pdf");
        doc.setMimeType("application/pdf");
        doc.setFileSize(204800L);
        doc.setLocalPath("/storage/documents/HoSoHoanCong.pdf");
        doc.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        doc.setFolderId(savedFolder.getId());
        doc.setAssetRecordId(TEST_PREFIX + "bridge_01");

        DocumentMetadataEntity savedDoc = documentMetadataRepository.save(doc);
        assertNotNull(savedDoc.getId());

        List<DocumentMetadataEntity> byAsset = documentMetadataRepository.findByAssetRecordIdAndIsDeletedFalse(TEST_PREFIX + "bridge_01");
        assertFalse(byAsset.isEmpty());
        assertEquals("HoSoHoanCong_CauKyCung.pdf", byAsset.get(0).getOriginalName());
    }

    @Test
    @DisplayName("5. IAM & Audit: AppRole, AppUser và AuditLog")
    void testIamAndAuditLog() {
        AppRoleEntity role = new AppRoleEntity(TEST_PREFIX + "ROLE_TEST", "Vai trò kiểm thử", "Dành cho unit test");
        AppRoleEntity savedRole = appRoleRepository.save(role);

        AppUserEntity user = new AppUserEntity(
                TEST_PREFIX + "admin",
                TEST_PREFIX + "admin@drvn.gov.vn",
                "$2a$10$hashPasswordExample",
                "Quản Trị Viên Test",
                savedRole.getId(),
                "kqldb_1"
        );
        AppUserEntity savedUser = appUserRepository.save(user);
        assertNotNull(savedUser.getId());

        AuditLogEntity audit = new AuditLogEntity(
                savedUser.getId(),
                savedUser.getUsername(),
                "CREATE",
                "ASSET",
                TEST_PREFIX + "bridge_01",
                null,
                "{\"name\": \"Cầu Mới\"}",
                "127.0.0.1",
                "JUnit 5 Test Handoff"
        );
        AuditLogEntity savedAudit = auditLogRepository.save(audit);
        assertNotNull(savedAudit.getId());
        assertEquals("CREATE", savedAudit.getAction());
        assertEquals("ASSET", savedAudit.getEntityType());
    }

    @Test
    @DisplayName("6. AssetQueryAdapter: Ghi tài sản có audit log tự động và đọc dữ liệu")
    void testAssetQueryAdapterAuditing() {
        AssetRecordEntity asset = new AssetRecordEntity();
        asset.setRecordId(TEST_PREFIX + "bridge_01");
        asset.setDatasetCode("tbl_test_adapter");
        asset.setAssetType("BRIDGE");
        asset.setName("Cầu Test Qua Adapter");
        asset.setRouteCode("QL.1A");

        // Lưu qua Adapter
        AssetRecordEntity saved = assetQueryAdapter.saveCuratedAsset(asset, "test_agent", "Khởi tạo dữ liệu kiểm thử");
        assertNotNull(saved.getId());

        // Kiểm tra audit_log đã tự động được ghi nhận
        var auditPage = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("ASSET", TEST_PREFIX + "bridge_01", org.springframework.data.domain.PageRequest.of(0, 10));
        assertFalse(auditPage.isEmpty(), "Thao tác qua Adapter phải tự động tạo audit_log");
        assertEquals("CREATE", auditPage.getContent().get(0).getAction());

        // Đọc lại qua Adapter
        RecordItemDto dto = assetQueryAdapter.getRecordById("tbl_test_adapter", TEST_PREFIX + "bridge_01", "ROLE_ADMIN");
        assertNotNull(dto);
        assertEquals(TEST_PREFIX + "bridge_01", dto.getRecordKey());
    }
}
