package vn.gov.drvn.kcht.greentree;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class GreenTreeMapService {

    private final JdbcTemplate database;
    private final GreenTreeStorageProperties storageProperties;

    public GreenTreeMapService(JdbcTemplate database, GreenTreeStorageProperties storageProperties) {
        this.database = database;
        this.storageProperties = storageProperties;
    }

    public MarkerPage markers(UserPrincipal principal, double minLon, double minLat,
                              double maxLon, double maxLat, int limit) {
        boolean newTable = storageProperties.getMode().readsNewTable();
        String sql = newTable ? """
                SELECT tree.id, tree.record_id, ST_X(tree.geom) AS longitude, ST_Y(tree.geom) AS latitude
                FROM green_tree_record tree
                WHERE tree.is_deleted = FALSE
                  AND tree.geom IS NOT NULL
                  AND tree.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)
                  AND (? = 'ROLE_ADMIN'
                       OR (tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, ?))
                       OR (tree.raw_record_id IS NULL AND tree.branch_id = ?))
                ORDER BY tree.id
                LIMIT ?
                """ : legacyLocated() + """
                SELECT id, record_id, longitude, latitude
                FROM located
                WHERE longitude BETWEEN ? AND ? AND latitude BETWEEN ? AND ?
                ORDER BY id
                LIMIT ?
                """;
        List<Marker> rows;
        if (newTable) {
            rows = database.query(sql, (result, row) -> new Marker(result.getLong("id"),
                            result.getString("record_id"), result.getDouble("longitude"), result.getDouble("latitude")),
                    minLon, minLat, maxLon, maxLat, principal.getRoleCode(), principal.getId(),
                    principal.getBranchId(), limit + 1);
        } else {
            rows = database.query(sql, (result, row) -> new Marker(result.getLong("id"),
                            result.getString("record_id"), result.getDouble("longitude"), result.getDouble("latitude")),
                    principal.getRoleCode(), principal.getId(), principal.getBranchId(),
                    minLon, maxLon, minLat, maxLat, limit + 1);
        }
        return new MarkerPage(rows.subList(0, Math.min(rows.size(), limit)), rows.size() > limit);
    }

    public TreePoint detail(UserPrincipal principal, long id) {
        boolean newTable = storageProperties.getMode().readsNewTable();
        String sql = newTable ? """
                SELECT tree.id, tree.record_id, tree.name, ST_X(tree.geom) AS longitude,
                       ST_Y(tree.geom) AS latitude, tree.route_name, tree.lytrinh,
                       tree.species_name, tree.scientific_name, tree.diameter_cm::text AS diameter_cm,
                       tree.height_m::text AS height_m, tree.health_status, tree.risk_level,
                       photo.source_url AS source_image_url
                FROM green_tree_record tree
                LEFT JOIN raw_dataset_record raw ON raw.id = tree.raw_record_id
                LEFT JOIN vroad_image_reference photo
                  ON photo.dataset_key = 'vroad_assets'
                 AND photo.record_key = COALESCE(NULLIF(tree.attributes->>'source_record_key', ''), raw.record_key)
                WHERE tree.id = ? AND tree.is_deleted = FALSE AND tree.geom IS NOT NULL
                  AND (? = 'ROLE_ADMIN'
                       OR (tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, ?))
                       OR (tree.raw_record_id IS NULL AND tree.branch_id = ?))
                """ : legacyLocated() + """
                SELECT item.id, item.record_id, item.name, item.longitude, item.latitude,
                       item.route_name, item.lytrinh,
                       COALESCE(NULLIF(item.attributes->>'species_name', ''),
                                NULLIF(item.raw_payload->>'species_name', '')) AS species_name,
                       COALESCE(NULLIF(item.attributes->>'scientific_name', ''),
                                NULLIF(item.raw_payload->>'scientific_name', '')) AS scientific_name,
                       COALESCE(NULLIF(item.attributes->>'diameter_cm', ''),
                                NULLIF(item.raw_payload->>'diameter_cm', '')) AS diameter_cm,
                       COALESCE(NULLIF(item.attributes->>'height_m', ''),
                                NULLIF(item.raw_payload->>'height_m', '')) AS height_m,
                       COALESCE(NULLIF(item.attributes->>'health_status', ''),
                                NULLIF(item.attributes->>'condition', ''),
                                NULLIF(item.raw_payload->>'health_status', ''),
                                NULLIF(item.raw_payload->>'condition', '')) AS health_status,
                       COALESCE(NULLIF(item.attributes->>'risk_level', ''),
                                NULLIF(item.raw_payload->>'risk_level', '')) AS risk_level,
                       photo.source_url AS source_image_url
                FROM located item
                LEFT JOIN vroad_image_reference photo
                  ON photo.dataset_key = 'vroad_assets'
                 AND photo.record_key = COALESCE(NULLIF(item.attributes->>'source_record_key', ''), item.source_record_key)
                WHERE item.id = ? AND item.longitude IS NOT NULL AND item.latitude IS NOT NULL
                """;
        Object[] params = newTable
                ? new Object[]{id, principal.getRoleCode(), principal.getId(), principal.getBranchId()}
                : new Object[]{principal.getRoleCode(), principal.getId(), principal.getBranchId(), id};
        return database.query(sql, (result, row) -> new TreePoint(
                        result.getLong("id"), result.getString("record_id"), result.getString("name"),
                        result.getDouble("longitude"), result.getDouble("latitude"), result.getString("route_name"),
                        result.getString("lytrinh"), result.getString("species_name"),
                        result.getString("scientific_name"), result.getString("diameter_cm"),
                        result.getString("height_m"), result.getString("health_status"),
                        result.getString("risk_level"), result.getString("source_image_url")), params)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Không thấy cây xanh có tọa độ trong phạm vi được cấp"));
    }

    private String legacyLocated() {
        return """
                WITH located AS (
                    SELECT tree.id, tree.record_id, tree.name, tree.route_name, tree.lytrinh,
                           tree.attributes, raw.raw_payload, raw.record_key AS source_record_key,
                           CASE
                               WHEN geometry.point_geom IS NOT NULL AND ST_IsValid(geometry.point_geom)
                                AND ST_X(geometry.point_geom) BETWEEN -180 AND 180
                                AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90
                                   THEN ST_X(geometry.point_geom)
                               WHEN (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                               AND ST_IsValid(geometry.geom) THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180
                                AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                               AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90
                                   THEN ST_X(geometry.geom)
                               WHEN (CASE WHEN tree.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (tree.attributes->>'longitude')::double precision END) BETWEEN -180 AND 180
                                AND (CASE WHEN tree.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (tree.attributes->>'latitude')::double precision END) BETWEEN -90 AND 90
                                   THEN (tree.attributes->>'longitude')::double precision
                               WHEN tree.attributes ? 'longitude' OR tree.attributes ? 'latitude'
                                   THEN NULL
                               WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (raw.raw_payload->>'longitude')::double precision END) BETWEEN -180 AND 180
                                AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (raw.raw_payload->>'latitude')::double precision END) BETWEEN -90 AND 90
                                   THEN (raw.raw_payload->>'longitude')::double precision
                           END AS longitude,
                           CASE
                               WHEN geometry.point_geom IS NOT NULL AND ST_IsValid(geometry.point_geom)
                                AND ST_X(geometry.point_geom) BETWEEN -180 AND 180
                                AND ST_Y(geometry.point_geom) BETWEEN -90 AND 90
                                   THEN ST_Y(geometry.point_geom)
                               WHEN (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                               AND ST_IsValid(geometry.geom) THEN ST_X(geometry.geom) END) BETWEEN -180 AND 180
                                AND (CASE WHEN geometry.geom IS NOT NULL AND GeometryType(geometry.geom) = 'POINT'
                                               AND ST_IsValid(geometry.geom) THEN ST_Y(geometry.geom) END) BETWEEN -90 AND 90
                                   THEN ST_Y(geometry.geom)
                               WHEN (CASE WHEN tree.attributes->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (tree.attributes->>'longitude')::double precision END) BETWEEN -180 AND 180
                                AND (CASE WHEN tree.attributes->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (tree.attributes->>'latitude')::double precision END) BETWEEN -90 AND 90
                                   THEN (tree.attributes->>'latitude')::double precision
                               WHEN tree.attributes ? 'longitude' OR tree.attributes ? 'latitude'
                                   THEN NULL
                               WHEN (CASE WHEN raw.raw_payload->>'longitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (raw.raw_payload->>'longitude')::double precision END) BETWEEN -180 AND 180
                                AND (CASE WHEN raw.raw_payload->>'latitude' ~ '^-?[0-9]+([.][0-9]+)?$'
                                          THEN (raw.raw_payload->>'latitude')::double precision END) BETWEEN -90 AND 90
                                   THEN (raw.raw_payload->>'latitude')::double precision
                           END AS latitude
                    FROM asset_record tree
                    LEFT JOIN raw_dataset_record raw ON raw.id = tree.raw_record_id
                    LEFT JOIN asset_geometry geometry ON geometry.asset_id = tree.id
                    WHERE tree.dataset_code = 'green_trees' AND tree.asset_type = 'TREE'
                      AND tree.is_deleted = FALSE
                      AND (? = 'ROLE_ADMIN'
                           OR (tree.raw_record_id IS NOT NULL AND vroad_record_visible(tree.raw_record_id, ?))
                           OR (tree.raw_record_id IS NULL AND tree.branch_id = ?))
                )
                """;
    }

    public record Marker(long recordId, String recordKey, double longitude, double latitude) {
    }

    public record MarkerPage(List<Marker> content, boolean truncated) {
    }

    public record TreePoint(long recordId, String recordKey, String name, double longitude, double latitude,
                            String routeName, String chainage, String speciesName, String scientificName,
                            String diameterCm, String heightM, String healthStatus, String riskLevel,
                            String sourceImageUrl) {
    }
}

