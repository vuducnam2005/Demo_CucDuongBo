package vn.gov.drvn.kcht.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BranchScopeFilterTest {
    private final BranchScopeFilter filter = new BranchScopeFilter();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void branchCannotReadGlobalIdDashboardExportOrUsers() throws Exception {
        authenticate("ROLE_MANAGER", "kqldb_1");
        for (String path : List.of("/api/dashboard/summary", "/api/datasets/tbl_bridge/123",
                "/api/datasets/tbl_bridge/export", "/api/auth/users", "/api/reports/road-lengths")) {
            MockHttpServletResponse response = request("GET", path);
            assertEquals(403, response.getStatus(), path);
        }
    }

    @Test
    void branchOnlyGetsScopedReadEndpoints() throws Exception {
        authenticate("ROLE_EDITOR", "kqldb_1");
        assertEquals(200, request("GET", "/api/region/assets/123").getStatus());
        assertEquals(200, request("GET", "/api/region/summary").getStatus());
        assertEquals(200, request("GET", "/api/vroad/dashboard").getStatus());
        assertEquals(200, request("GET", "/api/vroad/traffic/stations").getStatus());
        assertEquals(200, request("GET", "/api/vroad/traffic/summary").getStatus());
        assertEquals(200, request("GET", "/api/local-documents").getStatus());
        assertEquals(200, request("GET", "/api/local-documents/folders").getStatus());
        assertEquals(200, request("GET", "/api/local-documents/branches").getStatus());
        assertEquals(200, request("GET", "/api/local-documents/123/file").getStatus());
        assertEquals(200, request("GET", "/api/local-documents/123/versions").getStatus());
        assertEquals(200, request("GET", "/api/local-documents/123/versions/2/file").getStatus());
        assertEquals(200, request("POST", "/api/local-documents/upload").getStatus());
        assertEquals(200, request("POST", "/api/local-documents/123/versions").getStatus());
        assertEquals(200, request("POST", "/api/local-documents/folders").getStatus());
        assertEquals(200, request("DELETE", "/api/local-documents/123").getStatus());
        assertEquals(200, request("PATCH", "/api/local-documents/folders/123").getStatus());
        assertEquals(200, request("DELETE", "/api/local-documents/folders/123").getStatus());
        assertEquals(403, request("PATCH", "/api/local-documents/folders/123/file").getStatus());
        assertEquals(403, request("DELETE", "/api/local-documents/folders/all").getStatus());
        assertEquals(403, request("GET", "/api/documents").getStatus());
        assertEquals(403, request("GET", "/api/local-documents/123/versions/2/delete").getStatus());
        assertEquals(200, request("GET", "/api/region/assets/123/review").getStatus());
        assertEquals(200, request("GET", "/api/vroad/map/points").getStatus());
        assertEquals(200, request("GET", "/api/vroad/map/assets").getStatus());
        assertEquals(200, request("GET", "/api/vroad/map/near").getStatus());
        assertEquals(200, request("GET", "/api/vroad/cases").getStatus());
        assertEquals(200, request("GET", "/api/vroad/assets/123/map").getStatus());
        assertEquals(200, request("GET", "/api/vroad/defects/123/evidence").getStatus());
        assertEquals(200, request("POST", "/api/vroad/defects/123/resolve").getStatus());
        assertEquals(200, request("POST", "/api/region/assets/123/submit").getStatus());
        assertEquals(200, request("POST", "/api/region/assets/123/decision").getStatus());
        assertEquals(200, request("GET", "/api/auth/me").getStatus());
        assertEquals(403, request("POST", "/api/region/assets/123").getStatus());
        assertEquals(403, request("GET", "/api/region/../dashboard/summary").getStatus());
    }

    @Test
    void centralAdministratorCanUseExistingApis() throws Exception {
        authenticate("ROLE_ADMIN", null);
        assertEquals(200, request("GET", "/api/dashboard/summary").getStatus());
    }

    @Test
    void nonAdminWithoutBranchCannotFallThroughToNationalApis() throws Exception {
        authenticate("ROLE_MANAGER", null);
        assertEquals(403, request("GET", "/api/dashboard/summary").getStatus());
        assertEquals(403, request("GET", "/api/region/assets").getStatus());
        assertEquals(200, request("GET", "/api/auth/me").getStatus());
    }

    private MockHttpServletResponse request(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(response.getStatus() == 403, chain.getRequest() == null);
        return response;
    }

    private void authenticate(String role, String branchId) {
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role);
        UserPrincipal principal = new UserPrincipal(1L, "demo", null, null, "Demo", role, role,
                "demo", branchId, true, List.of(), List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(authority)));
    }
}
