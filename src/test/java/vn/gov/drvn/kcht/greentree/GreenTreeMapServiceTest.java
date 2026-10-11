package vn.gov.drvn.kcht.greentree;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GreenTreeMapServiceTest {

    @Test
    void newModeUsesIndexedPostgisBoundingBoxQuery() {
        JdbcTemplate database = mock(JdbcTemplate.class);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<GreenTreeMapService.Marker>>any(),
                any(Object[].class))).thenReturn(List.of());
        GreenTreeMapService service = new GreenTreeMapService(database, new GreenTreeStorageProperties("NEW"));

        service.markers(admin(), 108, 13, 110, 15, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<GreenTreeMapService.Marker>>any(),
                any(Object[].class));
        assertTrue(sql.getValue().contains("FROM green_tree_record"));
        assertTrue(sql.getValue().contains("tree.geom && ST_MakeEnvelope"));
        assertTrue(sql.getValue().contains("tree.is_deleted = FALSE"));
    }

    @Test
    void legacyModeKeepsLegacyReadAvailableForRollback() {
        JdbcTemplate database = mock(JdbcTemplate.class);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<GreenTreeMapService.Marker>>any(),
                any(Object[].class))).thenReturn(List.of());
        GreenTreeMapService service = new GreenTreeMapService(database, new GreenTreeStorageProperties("LEGACY"));

        service.markers(admin(), 108, 13, 110, 15, 100);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<GreenTreeMapService.Marker>>any(),
                any(Object[].class));
        assertTrue(sql.getValue().contains("FROM asset_record tree"));
        assertTrue(sql.getValue().contains("tree.dataset_code = 'green_trees'"));
        assertTrue(sql.getValue().contains("WHEN tree.attributes ? 'longitude' OR tree.attributes ? 'latitude'"));
    }

    private UserPrincipal admin() {
        return new UserPrincipal(1L, "admin", null, null, "Admin", "ROLE_ADMIN", "Admin",
                "moc_dbvn", null, true, List.of(), List.of());
    }
}
