package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PayloadValidator {

    private final RecordKeyResolver keyResolver;

    public PayloadValidator(RecordKeyResolver keyResolver) {
        this.keyResolver = keyResolver;
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String recordKey;
        private final String payloadSha256;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, String recordKey, String payloadSha256,
                                List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.recordKey = recordKey;
            this.payloadSha256 = payloadSha256;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() { return valid; }
        public String getRecordKey() { return recordKey; }
        public String getPayloadSha256() { return payloadSha256; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }

    public ValidationResult validate(JsonNode recordNode) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (recordNode == null || recordNode.isNull()) {
            errors.add("Payload bản ghi là null hoặc rỗng");
            return new ValidationResult(false, null, null, errors, warnings);
        }

        if (!recordNode.isObject()) {
            errors.add("Bản ghi không phải là một JSON Object hợp lệ");
            return new ValidationResult(false, null, null, errors, warnings);
        }

        if (recordNode.isEmpty()) {
            errors.add("JSON Object không chứa bất kỳ trường dữ liệu nào");
            return new ValidationResult(false, null, null, errors, warnings);
        }

        String rawJsonString = recordNode.toString();
        String payloadSha256 = RecordKeyResolver.computeSha256(rawJsonString);

        String recordKey = null;
        try {
            recordKey = keyResolver.resolveRecordKey(recordNode);
            if (recordKey == null || recordKey.trim().isEmpty()) {
                errors.add("Không thể xác định record_key cho bản ghi");
            }
        } catch (Exception e) {
            errors.add("Lỗi khi trích xuất record_key: " + e.getMessage());
        }

        // Kiểm tra tọa độ cảnh báo (nếu có trường x_min / y_min)
        if (recordNode.has("x_min") && !recordNode.get("x_min").isNull()) {
            try {
                double x = recordNode.get("x_min").asDouble();
                double y = recordNode.has("y_min") ? recordNode.get("y_min").asDouble() : 0.0;
                if (x == 0.0 && y == 0.0) {
                    warnings.add("Tọa độ x_min và y_min đều bằng 0");
                } else if (x < 100.0 || x > 115.0 || y < 6.0 || y > 26.0) {
                    warnings.add(String.format("Tọa độ (%.4f, %.4f) nằm ngoài phạm vi lãnh thổ Việt Nam", x, y));
                }
            } catch (Exception e) {
                warnings.add("Không thể phân tích tọa độ x_min/y_min: " + e.getMessage());
            }
        }

        boolean isValid = errors.isEmpty();
        return new ValidationResult(isValid, recordKey, payloadSha256, errors, warnings);
    }
}
