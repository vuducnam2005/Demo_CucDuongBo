package vn.gov.drvn.kcht.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Value("${kcht.security.allow-anonymous-admin:false}")
    private boolean allowAnonymousAdmin;

    @Value("${kcht.security.legacy-test-headers:false}")
    private boolean legacyTestHeaders;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Claims claims = jwtTokenProvider.getClaimsFromToken(jwt);
                String username = claims.getSubject();

                UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(username);
                if (!principal.isEnabled()) {
                    throw new org.springframework.security.authentication.DisabledException("Tài khoản đã bị khóa");
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Tương thích ngược với các bài kiểm thử Giai đoạn 4: Hỗ trợ header X-User-Role
                String legacyRoleHeader = request.getHeader("X-User-Role");
                if (activeProfile.contains("test") && legacyTestHeaders && StringUtils.hasText(legacyRoleHeader)) {
                    String role = legacyRoleHeader.trim().toUpperCase();
                    if (!role.startsWith("ROLE_")) {
                        role = "ROLE_" + role;
                    }
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
                    UserPrincipal principal = new UserPrincipal(
                            0L, "legacy_" + role.toLowerCase(), null, null, "Legacy Test User",
                            role, null, "moc_dbvn", null, true, List.of(), authorities
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (activeProfile.contains("test") && allowAnonymousAdmin) {
                    // Môi trường dev/test khi bật cờ ẩn danh cho phép đọc/ghi công khai
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    UserPrincipal principal = new UserPrincipal(
                            0L, "anonymous_admin", null, null, "Anonymous Dev Admin",
                            "ROLE_ADMIN", null, "moc_dbvn", null, true, List.of(), authorities
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            logger.error("Không thể thiết lập xác thực người dùng trong security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
