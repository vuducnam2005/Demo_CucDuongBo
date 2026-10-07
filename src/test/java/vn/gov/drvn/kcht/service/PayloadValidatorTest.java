package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayloadValidatorTest {

    private PayloadValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RecordKeyResolver resolver = new RecordKeyResolver();
        validator = new PayloadValidator(resolver);
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Nghiệm thu: Bản ghi hợp lệ với đầy đủ trường định danh và tọa độ")
    void testValidPayload() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", "duonggom_01");
        node.put("gid", "1294603");
        node.put("fielddisplay", "Đường gom QL.2");
        node.put("x_min", 106.7095);
        node.put("y_min", 21.9671);

        PayloadValidator.ValidationResult result = validator.validate(node);
        assertTrue(result.isValid());
        assertEquals("duonggom_01", result.getRecordKey());
        assertNotNull(result.getPayloadSha256());
        assertEquals(64, result.getPayloadSha256().length());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Nghiệm thu: Bản ghi rỗng không có trường nào bị báo lỗi")
    void testEmptyObjectPayload() {
        ObjectNode node = objectMapper.createObjectNode();
        PayloadValidator.ValidationResult result = validator.validate(node);

        assertFalse(result.isValid());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("Nghiệm thu: Cảnh báo khi tọa độ bằng 0")
    void testZeroCoordinatesWarning() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", "asset_zero_coords");
        node.put("x_min", 0.0);
        node.put("y_min", 0.0);

        PayloadValidator.ValidationResult result = validator.validate(node);
        assertTrue(result.isValid(), "Bản ghi vẫn được nạp vào raw nhưng có cảnh báo");
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("bằng 0")));
    }

    @Test
    @DisplayName("Nghiệm thu: Cảnh báo khi tọa độ nằm ngoài lãnh thổ Việt Nam")
    void testOutOfBoundsCoordinatesWarning() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", "asset_foreign_coords");
        node.put("x_min", 45.0); // Kinh độ ở Trung Đông/Châu Âu
        node.put("y_min", 50.0);

        PayloadValidator.ValidationResult result = validator.validate(node);
        assertTrue(result.isValid());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("ngoài phạm vi")));
    }
}
