package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.gov.drvn.kcht.dto.PagedResponse;
import vn.gov.drvn.kcht.entity.AuditLogEntity;
import vn.gov.drvn.kcht.repository.AuditLogRepository;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken",
            "secret", "secretkey", "credential", "credentials", "authorization"
    );

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ghi nhận sự kiện xác thực (Đăng nhập thành công, thất bại, đăng xuất, làm mới token).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuthEvent(String username, Long userId, String action, String ipAddress,
                                String userAgent, Map<String, Object> details) {
        try {
            String sanitizedDetails = sanitizeAndSerialize(details);

            AuditLogEntity logEntity = new AuditLogEntity(
                    userId,
                    username != null ? username : "anonymous",
                    action,
                    "AUTH",
                    username != null ? username : "unknown",
                    null,
                    sanitizedDetails,
                    ipAddress,
                    userAgent
            );

            auditLogRepository.save(logEntity);
            log.info("Ghi nhận nhật ký xác thực: User='{}', Action='{}', IP='{}'", username, action, ipAddress);
        } catch (Exception ex) {
            log.error("Lỗi khi ghi nhật ký kiểm toán xác thực", ex);
        }
    }

    /**
     * Ghi nhận sự kiện thay đổi dữ liệu thực thể.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEntityAction(String username, Long userId, String action, String entityType,
                                   String entityId, Object oldValues, Object newValues,
                                   String ipAddress, String userAgent) {
        try {
            String sanitizedOld = sanitizeAndSerialize(oldValues);
            String sanitizedNew = sanitizeAndSerialize(newValues);

            AuditLogEntity logEntity = new AuditLogEntity(
                    userId,
                    username,
                    action,
                    entityType,
                    entityId,
                    sanitizedOld,
                    sanitizedNew,
                    ipAddress,
                    userAgent
            );

            auditLogRepository.save(logEntity);
            log.info("Ghi nhận nhật ký kiểm toán thực thể: User='{}', Action='{}', Entity='{}:{}'",
                    username, action, entityType, entityId);
        } catch (Exception ex) {
            log.error("Lỗi khi ghi nhật ký kiểm toán thực thể", ex);
        }
    }

    /**
     * Tra cứu danh sách nhật ký kiểm toán có phân trang.
     */
    @Transactional(readOnly = true)
    public PagedResponse<AuditLogEntity> getAuditLogs(String username, String entityType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogEntity> resultPage;

        if (username != null && !username.isBlank()) {
            resultPage = auditLogRepository.findByUsernameOrderByCreatedAtDesc(username, pageable);
        } else if (entityType != null && !entityType.isBlank()) {
            resultPage = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, "%", pageable);
        } else {
            resultPage = auditLogRepository.findAll(pageable);
        }

        return PagedResponse.of(resultPage);
    }

    /**
     * Khử bỏ triệt để mọi thông tin nhạy cảm (mật khẩu, secret, token) trước khi lưu JSON vào CSDL.
     */
    private String sanitizeAndSerialize(Object data) {
        if (data == null) {
            return null;
        }
        try {
            JsonNode tree = objectMapper.valueToTree(data);
            removeSensitiveFields(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (Exception ex) {
            return "{\"error\":\"masking_failed\"}";
        }
    }

    private void removeSensitiveFields(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey().toLowerCase().replace("_", "").replace("-", "");
                if (SENSITIVE_KEYS.contains(key)) {
                    fields.remove();
                } else {
                    removeSensitiveFields(field.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                removeSensitiveFields(item);
            }
        }
    }
}
