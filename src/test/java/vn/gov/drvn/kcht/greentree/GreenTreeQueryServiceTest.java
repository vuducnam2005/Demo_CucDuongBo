package vn.gov.drvn.kcht.greentree;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.gov.drvn.kcht.mapper.GreenTreeMapper;
import vn.gov.drvn.kcht.service.DatasetPermissionService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GreenTreeQueryServiceTest {

    @Test
    void legacyReadTreatsAttributeCoordinateKeysAsACompletePrecedenceBoundary() {
        JdbcTemplate database = mock(JdbcTemplate.class);
        GreenTreeAccessService accessService = mock(GreenTreeAccessService.class);
        when(accessService.currentScope("ROLE_ADMIN"))
                .thenReturn(new GreenTreeAccessService.AccessScope("ROLE_ADMIN", 1L, null, "admin"));
        when(database.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class))).thenReturn(List.of());
        GreenTreeQueryService service = new GreenTreeQueryService(
                database, new GreenTreeStorageProperties("LEGACY"), accessService,
                mock(DatasetPermissionService.class), mock(GreenTreeMapper.class));

        service.getRecords(null, Map.of(), null, 0, 20, "ROLE_ADMIN");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                any(Object[].class));
        assertTrue(sql.getValue().contains(
                "WHEN tree.attributes ? 'longitude' OR tree.attributes ? 'latitude' THEN CASE"));
        assertTrue(sql.getValue().contains("WHEN (CASE WHEN raw.raw_payload->>'longitude'"));
        assertFalse(sql.getValue().contains("COALESCE(CASE WHEN geometry.point_geom"));
    }
}
