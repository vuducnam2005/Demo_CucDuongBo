package vn.gov.drvn.kcht.greentree;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.entity.AssetRecordEntity;
import vn.gov.drvn.kcht.entity.RawDatasetRecordEntity;
import vn.gov.drvn.kcht.repository.RawDatasetRecordRepository;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class GreenTreeDualWriteCoordinator {

    private final NamedParameterJdbcTemplate namedDatabase;
    private final JdbcTemplate database;
    private final RawDatasetRecordRepository rawRepository;
    private final ObjectMapper objectMapper;

    public GreenTreeDualWriteCoordinator(NamedParameterJdbcTemplate namedDatabase,
                                         JdbcTemplate database,
                                         RawDatasetRecordRepository rawRepository,
                                         ObjectMapper objectMapper) {
        this.namedDatabase = namedDatabase;
        this.database = database;
        this.rawRepository = rawRepository;
        this.objectMapper = objectMapper;
    }

    public void mirror(AssetRecordEntity asset) {
        Map<String, Object> attributes = GreenTreeAttributeSupport.parseObject(objectMapper, asset.getAttributes());
        Map<String, Object> raw = asset.getRawRecordId() == null ? Map.of() : rawRepository.findById(asset.getRawRecordId())
                .map(RawDatasetRecordEntity::getRawPayload)
                .map(payload -> GreenTreeAttributeSupport.parseObject(objectMapper, payload))
                .orElse(Map.of());
        Coordinate coordinate = coordinate(asset.getId());

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", asset.getId()).addValue("rawRecordId", asset.getRawRecordId())
                .addValue("recordId", asset.getRecordId()).addValue("gid", asset.getGid())
                .addValue("name", asset.getName())
                .addValue("treeCode", GreenTreeAttributeSupport.text(attributes, raw, "tree_code"))
                .addValue("speciesName", GreenTreeAttributeSupport.text(attributes, raw, "species_name"))
                .addValue("scientificName", GreenTreeAttributeSupport.text(attributes, raw, "scientific_name"))
                .addValue("routeCode", asset.getRouteCode()).addValue("routeName", asset.getRouteName())
                .addValue("kmFrom", asset.getKmFrom()).addValue("kmTo", asset.getKmTo())
                .addValue("lytrinh", asset.getLytrinh()).addValue("provinceId", asset.getProvinceId())
                .addValue("provinceName", asset.getProvinceName()).addValue("districtName", asset.getDistrictName())
                .addValue("townName", asset.getTownName()).addValue("roadClass", asset.getRoadClass())
                .addValue("roadType", asset.getRoadType()).addValue("organizationId", asset.getOrganizationId())
                .addValue("branchId", asset.getBranchId()).addValue("branchName", asset.getBranchName())
                .addValue("managementAgency", asset.getManagementAgency()).addValue("state", asset.getState())
                .addValue("stateName", asset.getStateName()).addValue("levelState", asset.getLevelState())
                .addValue("activeStatus", asset.getActiveStatus()).addValue("maintainValue", asset.getMaintainValue())
                .addValue("constructionYear", asset.getConstructionYear()).addValue("parentId", asset.getParentId())
                .addValue("diameterCm", GreenTreeAttributeSupport.nonNegativeDecimal(attributes, raw, "diameter_cm"))
                .addValue("heightM", GreenTreeAttributeSupport.nonNegativeDecimal(attributes, raw, "height_m"))
                .addValue("canopyDiameterM", GreenTreeAttributeSupport.nonNegativeDecimal(attributes, raw, "canopy_diameter_m"))
                .addValue("plantingYear", GreenTreeAttributeSupport.integer(attributes, raw, "planting_year"))
                .addValue("healthStatus", firstNonBlank(
                        GreenTreeAttributeSupport.text(attributes, raw, "health_status"),
                        GreenTreeAttributeSupport.text(attributes, raw, "condition")))
                .addValue("riskLevel", GreenTreeAttributeSupport.text(attributes, raw, "risk_level"))
                .addValue("notes", GreenTreeAttributeSupport.text(attributes, raw, "notes"))
                .addValue("longitude", coordinate.longitude()).addValue("latitude", coordinate.latitude())
                .addValue("attributes", asset.getAttributes()).addValue("deleted", asset.getIsDeleted())
                .addValue("deletedAt", asset.getDeletedAt()).addValue("deletedBy", asset.getDeletedBy())
                .addValue("version", asset.getVersion()).addValue("createdAt", asset.getCreatedAt())
                .addValue("updatedAt", asset.getUpdatedAt());

        namedDatabase.update("""
                INSERT INTO green_tree_record (
                    id, raw_record_id, record_id, gid, name, tree_code, species_name, scientific_name,
                    route_code, route_name, km_from, km_to, lytrinh, province_id, province_name,
                    district_name, town_name, road_class, road_type, organization_id, branch_id, branch_name,
                    management_agency, state, state_name, level_state, active_status, maintain_value,
                    construction_year, parent_id, diameter_cm, height_m, canopy_diameter_m,
                    planting_year, health_status, risk_level, notes, geom, attributes, is_deleted,
                    deleted_at, deleted_by, version, created_at, updated_at
                ) VALUES (
                    :id, :rawRecordId, :recordId, :gid, :name, :treeCode, :speciesName, :scientificName,
                    :routeCode, :routeName, :kmFrom, :kmTo, :lytrinh, :provinceId, :provinceName,
                    :districtName, :townName, :roadClass, :roadType, :organizationId, :branchId, :branchName,
                    :managementAgency, :state, :stateName, :levelState, :activeStatus, :maintainValue,
                    :constructionYear, :parentId, :diameterCm, :heightM, :canopyDiameterM,
                    :plantingYear, :healthStatus, :riskLevel, :notes,
                    CASE WHEN :longitude IS NULL OR :latitude IS NULL THEN NULL
                         ELSE ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) END,
                    CAST(:attributes AS jsonb), :deleted, :deletedAt, :deletedBy, :version, :createdAt, :updatedAt
                )
                ON CONFLICT (id) DO UPDATE SET
                    raw_record_id = EXCLUDED.raw_record_id, record_id = EXCLUDED.record_id, gid = EXCLUDED.gid,
                    name = EXCLUDED.name, tree_code = EXCLUDED.tree_code, species_name = EXCLUDED.species_name,
                    scientific_name = EXCLUDED.scientific_name, route_code = EXCLUDED.route_code,
                    route_name = EXCLUDED.route_name, km_from = EXCLUDED.km_from, km_to = EXCLUDED.km_to,
                    lytrinh = EXCLUDED.lytrinh, province_id = EXCLUDED.province_id,
                    province_name = EXCLUDED.province_name, district_name = EXCLUDED.district_name,
                    town_name = EXCLUDED.town_name, road_class = EXCLUDED.road_class,
                    road_type = EXCLUDED.road_type, organization_id = EXCLUDED.organization_id,
                    branch_id = EXCLUDED.branch_id, branch_name = EXCLUDED.branch_name,
                    management_agency = EXCLUDED.management_agency, state = EXCLUDED.state,
                    state_name = EXCLUDED.state_name, level_state = EXCLUDED.level_state,
                    active_status = EXCLUDED.active_status, maintain_value = EXCLUDED.maintain_value,
                    construction_year = EXCLUDED.construction_year, parent_id = EXCLUDED.parent_id,
                    diameter_cm = EXCLUDED.diameter_cm,
                    height_m = EXCLUDED.height_m, canopy_diameter_m = EXCLUDED.canopy_diameter_m,
                    planting_year = EXCLUDED.planting_year, health_status = EXCLUDED.health_status,
                    risk_level = EXCLUDED.risk_level, notes = EXCLUDED.notes, geom = EXCLUDED.geom,
                    attributes = EXCLUDED.attributes, is_deleted = EXCLUDED.is_deleted,
                    deleted_at = EXCLUDED.deleted_at, deleted_by = EXCLUDED.deleted_by,
                    version = EXCLUDED.version, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at
                """, params);
    }

    private Coordinate coordinate(Long assetId) {
        return database.query("""
                SELECT
                    CASE
                        WHEN geometry.point_geom IS NOT NULL AND ST_IsValid(geometry.point_geom)
                             AND ST_X(geometry.point_geom) BETWEEN -180 AND 180
                             AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90
                            THEN ST_X(geometry.point_geom)::numeric
                        WHEN (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                        AND ST_IsValid(geometry.geom) THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180
                             AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                        AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90
                            THEN ST_X(geometry.geom)::numeric
                        WHEN asset.attributes ? 'longitude' OR asset.attributes ? 'latitude'
                            THEN CASE
                                WHEN (CASE WHEN asset.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                           THEN (asset.attributes->>'longitude')::numeric END) BETWEEN -180 AND 180
                                     AND (CASE WHEN asset.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                               THEN (asset.attributes->>'latitude')::numeric END) BETWEEN -90 AND 90
                                    THEN (asset.attributes->>'longitude')::numeric
                            END
                        WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                   THEN (raw.raw_payload->>'longitude')::numeric END) BETWEEN -180 AND 180
                             AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                       THEN (raw.raw_payload->>'latitude')::numeric END) BETWEEN -90 AND 90
                            THEN (raw.raw_payload->>'longitude')::numeric
                    END AS longitude,
                    CASE
                        WHEN geometry.point_geom IS NOT NULL AND ST_IsValid(geometry.point_geom)
                             AND ST_X(geometry.point_geom) BETWEEN -180 AND 180
                             AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90
                            THEN ST_Y(geometry.point_geom)::numeric
                        WHEN (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                        AND ST_IsValid(geometry.geom) THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180
                             AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                        AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90
                            THEN ST_Y(geometry.geom)::numeric
                        WHEN asset.attributes ? 'longitude' OR asset.attributes ? 'latitude'
                            THEN CASE
                                WHEN (CASE WHEN asset.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                           THEN (asset.attributes->>'longitude')::numeric END) BETWEEN -180 AND 180
                                     AND (CASE WHEN asset.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                               THEN (asset.attributes->>'latitude')::numeric END) BETWEEN -90 AND 90
                                    THEN (asset.attributes->>'latitude')::numeric
                            END
                        WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                   THEN (raw.raw_payload->>'longitude')::numeric END) BETWEEN -180 AND 180
                             AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                       THEN (raw.raw_payload->>'latitude')::numeric END) BETWEEN -90 AND 90
                            THEN (raw.raw_payload->>'latitude')::numeric
                    END AS latitude
                FROM asset_record asset
                LEFT JOIN asset_geometry geometry ON geometry.asset_id = asset.id
                LEFT JOIN raw_dataset_record raw ON raw.id = asset.raw_record_id
                WHERE asset.id = ?
                """, (result, row) -> new Coordinate(result.getBigDecimal("longitude"),
                result.getBigDecimal("latitude")), assetId).stream().findFirst().orElse(new Coordinate(null, null));
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private record Coordinate(BigDecimal longitude, BigDecimal latitude) {
    }
}

