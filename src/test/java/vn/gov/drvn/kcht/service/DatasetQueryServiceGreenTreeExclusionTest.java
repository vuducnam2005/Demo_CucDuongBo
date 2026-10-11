package vn.gov.drvn.kcht.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import vn.gov.drvn.kcht.dto.RecordItemDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasetQueryServiceGreenTreeExclusionTest {

    @Test
    void genericVroadAssetDetailCannotReturnATree() {
        JdbcTemplate database = mock(JdbcTemplate.class);
        DatasetPermissionService permissions = mock(DatasetPermissionService.class);
        when(database.queryForObject(anyString(), eq(Long.class), eq("vroad_assets"))).thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<RecordItemDto>>any(),
                any(Object[].class))).thenReturn(List.of());
        DatasetQueryService service = new DatasetQueryService(database, new ObjectMapper(), permissions);

        assertThrows(RuntimeException.class,
                () -> service.getRecordById("vroad_assets", "123", "ROLE_ADMIN"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(database).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<RecordItemDto>>any(),
                any(Object[].class));
        assertTrue(sql.getValue().contains("AND NOT"));
        assertTrue(sql.getValue().contains("'green tree'"));
    }
}
