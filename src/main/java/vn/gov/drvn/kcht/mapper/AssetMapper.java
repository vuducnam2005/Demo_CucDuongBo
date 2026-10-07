package vn.gov.drvn.kcht.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.dto.GeoJsonFeatureDto;
import vn.gov.drvn.kcht.dto.GeoJsonGeometryDto;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.repository.projection.AssetSpatialProjection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mapper chuyển đổi giữa thực thể Asset và các DTO API.
 */
@Component
public class AssetMapper {

    private static final Logger log = LoggerFactory.getLogger(AssetMapper.class);
    private final ObjectMapper objectMapper;

    public AssetMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Ánh xạ AssetRecordEntity sang RecordItemDto.
     */
    public RecordItemDto toRecordItemDto(AssetRecordEntity entity) {
        if (entity == null) return null;

        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("id", entity.getRecordId());
        payloadMap.put("gid", entity.getGid());
        payloadMap.put("name", entity.getName());
        payloadMap.put("dataset_code", entity.getDatasetCode());
        payloadMap.put("asset_type", entity.getAssetType());
        payloadMap.put("route_code", entity.getRouteCode());
        payloadMap.put("route_name", entity.getRouteName());
        payloadMap.put("km_from", entity.getKmFrom());
        payloadMap.put("km_to", entity.getKmTo());
        payloadMap.put("lytrinh", entity.getLytrinh());
        payloadMap.put("province_id", entity.getProvinceId());
        payloadMap.put("province_name", entity.getProvinceName());
        payloadMap.put("district_name", entity.getDistrictName());
        payloadMap.put("town_name", entity.getTownName());
        payloadMap.put("road_class", entity.getRoadClass());
        payloadMap.put("road_type", entity.getRoadType());
        payloadMap.put("organization_id", entity.getOrganizationId());
        payloadMap.put("branch_id", entity.getBranchId());
        payloadMap.put("branch_name", entity.getBranchName());
        payloadMap.put("management_agency", entity.getManagementAgency());
        payloadMap.put("state", entity.getState());
        payloadMap.put("state_name", entity.getStateName());
        payloadMap.put("level_state", entity.getLevelState());
        payloadMap.put("active_status", entity.getActiveStatus());
        payloadMap.put("maintain_value", entity.getMaintainValue());
        payloadMap.put("construction_year", entity.getConstructionYear());
        payloadMap.put("parent_id", entity.getParentId());
        payloadMap.put("version", entity.getVersion());

        // Parse attributes JSONB
        if (entity.getAttributes() != null && !entity.getAttributes().isBlank()) {
            try {
                Map<String, Object> attrs = objectMapper.readValue(entity.getAttributes(), new TypeReference<>() {});
                payloadMap.put("attributes", attrs);
            } catch (Exception e) {
                payloadMap.put("attributes", entity.getAttributes());
            }
        }

        JsonNode payloadNode = objectMapper.valueToTree(payloadMap);

        return new RecordItemDto(
                entity.getId(),
                entity.getDatasetCode(),
                entity.getRecordId(),
                payloadNode,
                entity.getState() != null ? entity.getState() : "CURATED",
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Ánh xạ AssetSpatialProjection sang GeoJsonFeatureDto theo chuẩn RFC 7946.
     */
    public GeoJsonFeatureDto toGeoJsonFeatureDto(AssetSpatialProjection proj) {
        if (proj == null) return null;

        GeoJsonGeometryDto geomDto = null;
        if (proj.getGeoJson() != null && !proj.getGeoJson().isBlank()) {
            try {
                JsonNode geomNode = objectMapper.readTree(proj.getGeoJson());
                String type = geomNode.path("type").asText("Point");
                JsonNode coordsNode = geomNode.path("coordinates");
                Object coords = objectMapper.convertValue(coordsNode, Object.class);
                geomDto = new GeoJsonGeometryDto(type, coords);
            } catch (Exception e) {
                log.warn("Lỗi parse GeoJSON cho asset {}: {}", proj.getRecordId(), e.getMessage());
            }
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("assetId", proj.getAssetId());
        props.put("recordId", proj.getRecordId());
        props.put("name", proj.getName());
        props.put("datasetCode", proj.getDatasetCode());
        props.put("assetType", proj.getAssetType());
        props.put("routeCode", proj.getRouteCode());
        props.put("lytrinh", proj.getLytrinh());
        props.put("provinceName", proj.getProvinceName());
        props.put("branchId", proj.getBranchId());
        props.put("state", proj.getState());
        props.put("geomType", proj.getGeomType());
        props.put("bboxXmin", proj.getBboxXmin());
        props.put("bboxYmin", proj.getBboxYmin());
        props.put("bboxXmax", proj.getBboxXmax());
        props.put("bboxYmax", proj.getBboxYmax());

        return new GeoJsonFeatureDto(proj.getRecordId(), geomDto, props);
    }
}
