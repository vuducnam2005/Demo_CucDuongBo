package vn.gov.drvn.kcht.greentree;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.gov.drvn.kcht.security.UserPrincipal;

import java.util.Locale;

@Component
public class GreenTreeAccessService {

    public AccessScope currentScope(String suppliedRole) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal) {
            String role = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .findFirst()
                    .orElse(principal.getRoleCode());
            return new AccessScope(normalizeRole(role), principal.getId(), principal.getBranchId(), principal.getUsername());
        }
        return new AccessScope(normalizeRole(suppliedRole), null, null, "user");
    }

    private String normalizeRole(String role) {
        String normalized = role == null || role.isBlank() ? "ROLE_VIEWER" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    public record AccessScope(String role, Long userId, String branchId, String username) {
        public boolean isAdmin() {
            return "ROLE_ADMIN".equals(role);
        }

        public boolean hasBranch() {
            return branchId != null && !branchId.isBlank();
        }
    }
}

