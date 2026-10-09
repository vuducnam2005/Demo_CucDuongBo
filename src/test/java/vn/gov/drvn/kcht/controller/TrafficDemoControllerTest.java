package vn.gov.drvn.kcht.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrafficDemoControllerTest {
    private final JdbcTemplate database = mock(JdbcTemplate.class);
    private final TrafficDemoController controller = new TrafficDemoController(database);

    @Test
    void rejectsMissingBranchAndViewerBeforeAccessingTraffic() {
        for (UserPrincipal principal : List.of(principal("ROLE_MANAGER", null),
                principal("ROLE_VIEWER", "region-1"))) {
            ResponseStatusException error = assertThrows(ResponseStatusException.class,
                    () -> controller.stations(principal));
            assertEquals(403, error.getStatusCode().value());
        }
    }

    @Test
    void validatesTrafficQueryBeforeDatabaseAccess() {
        UserPrincipal admin = principal("ROLE_ADMIN", null);
        OffsetDateTime start = OffsetDateTime.parse("2026-10-07T08:00:00+07:00");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.summary(admin, "", null, null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.summary(admin, "DEMO-TRAFFIC-01", start, null)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> controller.summary(admin, "DEMO-TRAFFIC-01", start, start.plusDays(32)))
                .getStatusCode().value());
    }

    @Test
    void returnsNotFoundWhenStationIsOutsideTheManagersBranch() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.summary(principal("ROLE_MANAGER", "region-1"),
                        "DEMO-TRAFFIC-01", null, null));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void returnsEmptyCountsWhenScopedStationHasNoObservations() {
        String code = "station-1";
        TrafficDemoController.Station station = new TrafficDemoController.Station(code, "Demo", "Route",
                true, "DEMO", null);
        when(database.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TrafficDemoController.Station>>any(),
                eq(code), eq("ROLE_ADMIN"), isNull())).thenReturn(List.of(station));
        TrafficDemoController.TrafficSummary summary = controller.summary(principal("ROLE_ADMIN", null),
                code, null, null);
        assertEquals(0, summary.totalVehicles());
        assertEquals(null, summary.rangeStart());
    }

    private UserPrincipal principal(String role, String branch) {
        return new UserPrincipal(1L, "demo", null, null, "Demo", role, role,
                null, branch, true, List.of(), List.of());
    }
}
