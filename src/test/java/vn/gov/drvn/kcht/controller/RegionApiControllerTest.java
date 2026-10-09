package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class RegionApiControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final RegionApiController controller = new RegionApiController(database);

    @Test
    void guessedIdFromOtherBranchReturnsNotFound() {
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<RegionApiController.RegionAsset>>any(),
                eq(1L), eq(77L))).thenReturn(List.of());

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.asset(principal("ROLE_MANAGER", "kqldb_1"), 77L));

        assertEquals(404, failure.getStatusCode().value());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(database).query(query.capture(), org.mockito.ArgumentMatchers.<RowMapper<RegionApiController.RegionAsset>>any(),
                eq(1L), eq(77L));
        assertTrue(query.getValue().contains("vroad_record_visible(r.id, ?)"));
        assertTrue(query.getValue().contains("r.id = ?"));
    }

    @Test
    void missingBranchAndViewerCannotReadUnreviewedSourceRecords() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.asset(principal("ROLE_MANAGER", null), 1L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.asset(principal("ROLE_VIEWER", "kqldb_1"), 1L)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.asset(principal("ROLE_OPERATOR", "kqldb_1"), 1L)).getStatusCode().value());
    }

    private UserPrincipal principal(String role, String branchId) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
        return new UserPrincipal(1L, "test", null, null, "Test", role, role,
                "demo", branchId, true, List.of(), List.of(authority));
    }
}
