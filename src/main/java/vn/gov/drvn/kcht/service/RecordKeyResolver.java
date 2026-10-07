package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RecordKeyResolver {

    /**
     * Trích xuất khóa định danh nghiệp vụ (record_key) từ một bản ghi JSON.
     * Thứ tự ưu tiên:
     * 1. 'id'
     * 2. 'gid'
     * 3. 'vidagis_id'
     * 4. 'file_id' hoặc 'vidagis_fileentryid'
     * 5. 'oid'
     * 6. 'table_number'
     * 7. Fallback: Băm SHA-256 của toàn bộ payload bản ghi
     */
    public String resolveRecordKey(JsonNode recordNode) {
        if (recordNode == null || recordNode.isNull() || !recordNode.isObject()) {
            throw new IllegalArgumentException("Bản ghi không phải là một JSON Object hợp lệ");
        }

        // 1. Kiểm tra trường 'id'
        if (recordNode.hasNonNull("id")) {
            String idVal = recordNode.get("id").asText().trim();
            if (!idVal.isEmpty()) {
                return idVal;
            }
        }

        // 2. Kiểm tra trường 'gid'
        if (recordNode.hasNonNull("gid")) {
            String gidVal = recordNode.get("gid").asText().trim();
            if (!gidVal.isEmpty()) {
                return gidVal;
            }
        }

        // 3. Kiểm tra trường 'vidagis_id'
        if (recordNode.hasNonNull("vidagis_id")) {
            String vidagisId = recordNode.get("vidagis_id").asText().trim();
            if (!vidagisId.isEmpty()) {
                return vidagisId;
            }
        }

        // 4. Kiểm tra file_id / vidagis_fileentryid
        if (recordNode.hasNonNull("file_id")) {
            String fileId = recordNode.get("file_id").asText().trim();
            if (!fileId.isEmpty()) {
                return fileId;
            }
        }
        if (recordNode.hasNonNull("vidagis_fileentryid")) {
            String fileEntryId = recordNode.get("vidagis_fileentryid").asText().trim();
            if (!fileEntryId.isEmpty()) {
                return fileEntryId;
            }
        }

        // 5. Kiểm tra trường 'oid'
        if (recordNode.hasNonNull("oid")) {
            String oidVal = recordNode.get("oid").asText().trim();
            if (!oidVal.isEmpty()) {
                return oidVal;
            }
        }

        // 6. Kiểm tra trường 'table_number' (trong user_guide_tables)
        if (recordNode.hasNonNull("table_number")) {
            String tblNum = recordNode.get("table_number").asText().trim();
            if (!tblNum.isEmpty()) {
                return "table_" + tblNum;
            }
        }

        // 7. Fallback: Tính mã băm SHA-256 của chuỗi JSON
        return computeSha256(recordNode.toString());
    }

    public static String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Không tìm thấy thuật toán SHA-256", e);
        }
    }
}
