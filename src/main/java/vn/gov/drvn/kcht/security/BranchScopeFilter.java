package vn.gov.drvn.kcht.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class BranchScopeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                && !"ROLE_ADMIN".equals(principal.getRoleCode())) {
            String path = request.getServletPath();
            boolean accountEndpoint = path.equals("/api/auth/me") || path.equals("/api/auth/logout")
                    || path.equals("/api/auth/refresh");
            boolean scopedRead = "GET".equals(request.getMethod())
                    && (path.equals("/api/region/summary") || path.equals("/api/region/assets")
                    || path.matches("/api/region/assets/[0-9]+")
                    || path.matches("/api/region/assets/[0-9]+/review")
                    || path.equals("/api/vroad/dashboard")
                    || path.equals("/api/vroad/route-assignments")
                    || path.equals("/api/vroad/route-assignments/routes")
                    || path.equals("/api/vroad/traffic/stations")
                    || path.equals("/api/vroad/traffic/summary")
                    || path.equals("/api/local-documents")
                    || path.equals("/api/local-documents/folders")
                    || path.equals("/api/local-documents/branches")
                    || path.matches("/api/local-documents/[0-9]+(/file)?")
                    || path.matches("/api/local-documents/[0-9]+/versions(/[0-9]+/file)?")
                    || path.equals("/api/vroad/map/points") || path.equals("/api/vroad/map/near")
                    || path.equals("/api/vroad/map/assets")
                    || path.equals("/api/vroad/cases")
                    || path.matches("/api/vroad/assets/[0-9]+/map")
                    || path.matches("/api/vroad/defects/[0-9]+/map")
                    || path.matches("/api/vroad/defects/[0-9]+/evidence"));
            boolean scopedWrite = "POST".equals(request.getMethod())
                    && (path.matches("/api/region/assets/[0-9]+/(submit|decision)")
                    || path.matches("/api/vroad/defects/[0-9]+/resolve"));
            scopedWrite = scopedWrite || ("POST".equals(request.getMethod())
                    && (path.equals("/api/local-documents/folders")
                    || path.equals("/api/local-documents/upload")
                    || path.matches("/api/local-documents/[0-9]+/versions")))
                    || ("DELETE".equals(request.getMethod())
                    && (path.matches("/api/local-documents/[0-9]+")
                    || path.matches("/api/local-documents/folders/[0-9]+")))
                    || ("PATCH".equals(request.getMethod())
                    && (path.matches("/api/local-documents/[0-9]+")
                    || path.matches("/api/local-documents/folders/[0-9]+")));
            boolean hasBranch = principal.getBranchId() != null && !principal.getBranchId().isBlank();
            if (path.startsWith("/api/") && !accountEndpoint && (!hasBranch || (!scopedRead && !scopedWrite))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Phạm vi đơn vị chưa hỗ trợ phân hệ này\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
