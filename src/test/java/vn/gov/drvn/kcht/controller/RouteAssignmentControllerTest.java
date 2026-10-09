package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RouteAssignmentControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final RouteAssignmentController controller = new RouteAssignmentController(database);

    @Test
    void onlyAdministratorCanAssignOrRevokeRoutes() {
        UserPrincipal manager = principal("ROLE_MANAGER");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.assign(manager,
                new RouteAssignmentController.AssignmentRequest(2L, "Tuyến nguồn", null, null))).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.revoke(manager, 1)).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void invalidBoundsAndPaginationNeverReachTheDatabase() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.assign(principal("ROLE_ADMIN"),
                new RouteAssignmentController.AssignmentRequest(2L, "Tuyến nguồn", new BigDecimal("200"),
                        new BigDecimal("100")))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.list(principal("ROLE_ADMIN"), 0, 101)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> controller.routes(principal("ROLE_ADMIN"), "x".repeat(201))).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void targetMustBeAnActiveScopedAccount() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(2L))).thenReturn(0L);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.assign(principal("ROLE_ADMIN"),
                new RouteAssignmentController.AssignmentRequest(2L, "Tuyến nguồn", null, null))).getStatusCode().value());
    }

    @Test
    void unknownSourceRoutesAreNotCreatedFromUserText() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(2L))).thenReturn(1L);
        when(database.queryForObject(anyString(), eq(Long.class), eq("Tuyến không tồn tại"))).thenReturn(0L);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.assign(principal("ROLE_ADMIN"),
                new RouteAssignmentController.AssignmentRequest(2L, "Tuyến không tồn tại", null, null))).getStatusCode().value());
    }

    @Test
    void sourceRouteSearchIsBoundToTheAuthenticatedAccount() {
        controller.routes(principal("ROLE_EDITOR"), "QL1");
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(database).query(query.capture(), org.mockito.ArgumentMatchers.<RowMapper<RouteAssignmentController.RouteOption>>any(),
                eq(7L), eq("%QL1%"));
        assertTrue(query.getValue().contains("vroad_record_visible(item.id, ?)"));
        assertTrue(query.getValue().contains("LIMIT 100"));
    }

    private UserPrincipal principal(String role) {
        return new UserPrincipal(7L, "qa-user", null, null, "QA", role, role, "demo", "kqldb_1", true,
                List.of(), List.of(new SimpleGrantedAuthority(role)));
    }
}
