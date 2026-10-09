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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class RegionReviewControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final RegionReviewController controller = new RegionReviewController(database);

    @Test
    void onlyEditorCanSubmitAndViewerCannotSeeReview() {
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.submit(principal(2L, "ROLE_MANAGER", "kqldb_1"), 42L)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.review(principal(3L, "ROLE_VIEWER", "kqldb_1"), 42L)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> controller.decide(principal(1L, "ROLE_EDITOR", "kqldb_1"), 42L,
                        new RegionReviewController.DecisionRequest("APPROVED", ""))).getStatusCode().value());
        verifyNoInteractions(database);
    }

    @Test
    void crossBranchRecordCannotBeSubmittedOrReviewed() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), org.mockito.ArgumentMatchers.anyLong())).thenReturn(0L);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.submit(principal(1L, "ROLE_EDITOR", "kqldb_2"), 42L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> controller.review(principal(2L, "ROLE_MANAGER", "kqldb_2"), 42L)).getStatusCode().value());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(database, org.mockito.Mockito.times(2)).queryForObject(query.capture(), eq(Long.class), eq(42L), org.mockito.ArgumentMatchers.anyLong());
        assertTrue(query.getValue().contains("vroad_record_visible(id, ?)"));
        verify(database, never()).update(anyString());
    }

    @Test
    void returnRequiresReasonAndInvalidDecisionsAreRejected() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq(2L))).thenReturn(1L);
        UserPrincipal manager = principal(2L, "ROLE_MANAGER", "kqldb_1");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.decide(manager, 42L, new RegionReviewController.DecisionRequest("RETURNED", "  "))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.decide(manager, 42L, new RegionReviewController.DecisionRequest("UNKNOWN", ""))).getStatusCode().value());
    }

    @Test
    void managerCannotApproveOwnSubmissionOrUnsubmittedRecord() {
        when(database.queryForObject(anyString(), eq(Long.class), eq(42L), eq(2L))).thenReturn(1L);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq("APPROVED"), eq(""), eq(2L), eq(42L), eq("kqldb_1"), eq(2L))).thenReturn(List.of());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.decide(principal(2L, "ROLE_MANAGER", "kqldb_1"), 42L,
                        new RegionReviewController.DecisionRequest("APPROVED", ""))).getStatusCode().value());
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(database).query(query.capture(), org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),
                eq("APPROVED"), eq(""), eq(2L), eq(42L), eq("kqldb_1"), eq(2L));
        assertTrue(query.getValue().contains("status = 'IN_REVIEW' AND submitted_by <> ?"));
    }

    private UserPrincipal principal(long id, String role, String branch) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
        return new UserPrincipal(id, "test", null, null, "Test", role, role,
                "demo", branch, true, List.of(), List.of(authority));
    }
}
