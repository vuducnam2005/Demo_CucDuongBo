package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordKeyResolverTest {

    private RecordKeyResolver resolver;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        resolver = new RecordKeyResolver();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Nghiệm thu: Ưu tiên trích xuất khóa 'id' khi tồn tại")
    void testResolveId() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", "duonggon_sxd_tq_ql.2_2");
        node.put("gid", "1294603");

        String key = resolver.resolveRecordKey(node);
        assertEquals("duonggon_sxd_tq_ql.2_2", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Trích xuất 'gid' khi trường 'id' không có")
    void testResolveGid() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("gid", "1294603");

        String key = resolver.resolveRecordKey(node);
        assertEquals("1294603", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Trích xuất 'vidagis_id' cho các bảng phụ trợ")
    void testResolveVidagisId() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("vidagis_id", "guardrail_521041");

        String key = resolver.resolveRecordKey(node);
        assertEquals("guardrail_521041", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Trích xuất 'file_id' cho tệp hồ sơ tài liệu")
    void testResolveFileId() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("file_id", "cbfc95cca388487497048566b5d93252");

        String key = resolver.resolveRecordKey(node);
        assertEquals("cbfc95cca388487497048566b5d93252", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Trích xuất 'oid' cho bảng danh mục tham chiếu")
    void testResolveOid() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("oid", 16867);

        String key = resolver.resolveRecordKey(node);
        assertEquals("16867", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Trích xuất 'table_number' cho bảng cẩm nang")
    void testResolveTableNumber() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("table_number", 0);

        String key = resolver.resolveRecordKey(node);
        assertEquals("table_0", key);
    }

    @Test
    @DisplayName("Nghiệm thu: Băm SHA-256 fallback khi không có khóa định danh")
    void testFallbackSha256() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", "Vật thể không tên");
        node.put("value", 123);

        String key = resolver.resolveRecordKey(node);
        assertNotNull(key);
        assertEquals(64, key.length(), "Mã băm SHA-256 phải có 64 ký tự hex");
    }

    @Test
    @DisplayName("Nghiệm thu: Bắt lỗi khi node không phải JSON Object")
    void testInvalidNodeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRecordKey(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRecordKey(objectMapper.createArrayNode()));
    }
}
