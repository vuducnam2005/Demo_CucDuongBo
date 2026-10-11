package vn.gov.drvn.kcht.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.dto.RecordItemDto;
import vn.gov.drvn.kcht.entity.GreenTreeRecordEntity;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GreenTreeMapper {

    private final ObjectMapper objectMapper;

    public GreenTreeMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RecordItemDto toRecordItemDto(GreenTreeRecordEntity entity) {
        Map<String, Object> attributes = parseAttributes(entity.getAttributes());
        return toRecordItemDto(
                entity.getId(), entity.getRecordId(), entity.getGid(), entity.getName(),
                entity.getTreeCode(), entity.getSpeciesName(), entity.getScientificName(),
                entity.getRouteCode(), entity.getRouteName(), entity.getKmFrom(), entity.getKmTo(), entity.getLytrinh(),
                entity.getProvinceId(), entity.getProvinceName(), entity.getDistrictName(), entity.getTownName(),
                entity.getRoadClass(), entity.getRoadType(),
                entity.getOrganizationId(), entity.getBranchId(), entity.getBranchName(), entity.getManagementAgency(),
                entity.getState(), entity.getStateName(), entity.getLevelState(), entity.getActiveStatus(),
                entity.getMaintainValue(), entity.getConstructionYear(), entity.getParentId(),
                entity.getDiameterCm(), entity.getHeightM(), entity.getCanopyDiameterM(), entity.getPlantingYear(),
                entity.getHealthStatus(), entity.getRiskLevel(), entity.getNotes(),
                entity.getLongitude(), entity.getLatitude(), attributes, entity.getVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public RecordItemDto mapRow(ResultSet result, int rowNumber) throws SQLException {
        return toRecordItemDto(
                result.getLong("id"), result.getString("record_id"), nullableLong(result, "gid"),
                result.getString("name"), result.getString("tree_code"), result.getString("species_name"),
                result.getString("scientific_name"), result.getString("route_code"), result.getString("route_name"),
                result.getBigDecimal("km_from"), result.getBigDecimal("km_to"), result.getString("lytrinh"),
                result.getString("province_id"), result.getString("province_name"), result.getString("district_name"),
                result.getString("town_name"), result.getString("road_class"), result.getString("road_type"),
                result.getString("organization_id"), result.getString("branch_id"),
                result.getString("branch_name"), result.getString("management_agency"), result.getString("state"),
                result.getString("state_name"), result.getString("level_state"), result.getString("active_status"),
                result.getBigDecimal("maintain_value"), nullableInteger(result, "construction_year"),
                result.getString("parent_id"),
                result.getBigDecimal("diameter_cm"), result.getBigDecimal("height_m"),
                result.getBigDecimal("canopy_diameter_m"), nullableInteger(result, "planting_year"),
                result.getString("health_status"), result.getString("risk_level"), result.getString("notes"),
                result.getBigDecimal("longitude"), result.getBigDecimal("latitude"),
                parseAttributes(result.getString("attributes")), result.getInt("version"),
                result.getObject("created_at", OffsetDateTime.class), result.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private RecordItemDto toRecordItemDto(
            Long id, String recordId, Long gid, String name,
            String treeCode, String speciesName, String scientificName,
            String routeCode, String routeName, BigDecimal kmFrom, BigDecimal kmTo, String lytrinh,
            String provinceId, String provinceName, String districtName, String townName,
            String roadClass, String roadType,
            String organizationId, String branchId, String branchName, String managementAgency,
            String state, String stateName, String levelState, String activeStatus,
            BigDecimal maintainValue, Integer constructionYear, String parentId,
            BigDecimal diameterCm, BigDecimal heightM, BigDecimal canopyDiameterM, Integer plantingYear,
            String healthStatus, String riskLevel, String notes,
            BigDecimal longitude, BigDecimal latitude, Map<String, Object> attributes, Integer version,
            OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", recordId);
        payload.put("gid", gid);
        payload.put("name", name);
        payload.put("dataset_code", "green_trees");
        payload.put("asset_type", "TREE");
        payload.put("tree_code", treeCode);
        payload.put("species_name", speciesName);
        payload.put("scientific_name", scientificName);
        payload.put("route_code", routeCode);
        payload.put("route_name", routeName);
        payload.put("km_from", kmFrom);
        payload.put("km_to", kmTo);
        payload.put("lytrinh", lytrinh);
        payload.put("province_id", provinceId);
        payload.put("province_name", provinceName);
        payload.put("district_name", districtName);
        payload.put("town_name", townName);
        payload.put("road_class", roadClass);
        payload.put("road_type", roadType);
        payload.put("organization_id", organizationId);
        payload.put("branch_id", branchId);
        payload.put("branch_name", branchName);
        payload.put("management_agency", managementAgency);
        payload.put("state", state);
        payload.put("state_name", stateName);
        payload.put("level_state", levelState);
        payload.put("active_status", activeStatus);
        payload.put("maintain_value", maintainValue);
        payload.put("construction_year", constructionYear);
        payload.put("parent_id", parentId);
        payload.put("diameter_cm", diameterCm);
        payload.put("height_m", heightM);
        payload.put("canopy_diameter_m", canopyDiameterM);
        payload.put("planting_year", plantingYear);
        payload.put("health_status", healthStatus);
        payload.put("risk_level", riskLevel);
        payload.put("notes", notes);
        payload.put("longitude", longitude);
        payload.put("latitude", latitude);
        payload.put("version", version);
        attributes.forEach(payload::putIfAbsent);
        payload.put("attributes", attributes);
        JsonNode payloadNode = objectMapper.valueToTree(payload);
        return new RecordItemDto(id, "green_trees", recordId, payloadNode,
                state != null ? state : "CURATED", createdAt, updatedAt);
    }

    private Map<String, Object> parseAttributes(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> attributes = objectMapper.readValue(json, new TypeReference<>() {});
            return attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }
}

