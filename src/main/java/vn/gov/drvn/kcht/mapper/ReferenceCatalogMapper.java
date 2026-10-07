package vn.gov.drvn.kcht.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.dto.ReferenceCatalogItemDto;
import vn.gov.drvn.kcht.entity.ReferenceCatalogEntity;

import java.util.Collections;
import java.util.Map;

/**
 * Mapper chuyển đổi giữa ReferenceCatalogEntity và ReferenceCatalogItemDto.
 */
@Component
public class ReferenceCatalogMapper {

    private static final Logger log = LoggerFactory.getLogger(ReferenceCatalogMapper.class);

    private final ObjectMapper objectMapper;

    public ReferenceCatalogMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReferenceCatalogItemDto toDto(ReferenceCatalogEntity entity) {
        if (entity == null) return null;

        Map<String, Object> extra = Collections.emptyMap();
        if (entity.getExtraAttributes() != null && !entity.getExtraAttributes().isBlank()) {
            try {
                extra = objectMapper.readValue(entity.getExtraAttributes(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Không thể đọc extra_attributes của mục danh mục {} / {}: {}",
                        entity.getCatalogCode(), entity.getItemCode(), e.getMessage());
            }
        }

        return new ReferenceCatalogItemDto(
                entity.getCatalogCode(),
                entity.getItemCode(),
                entity.getItemName(),
                entity.getParentCode(),
                entity.getSortOrder(),
                entity.getIsActive(),
                extra
        );
    }
}
