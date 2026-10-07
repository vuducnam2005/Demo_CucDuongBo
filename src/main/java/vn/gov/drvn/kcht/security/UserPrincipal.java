package vn.gov.drvn.kcht.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.gov.drvn.kcht.entity.AppPermissionEntity;
import vn.gov.drvn.kcht.entity.AppRoleEntity;
import vn.gov.drvn.kcht.entity.AppUserEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    @JsonIgnore
    private final String password;
    private final String fullName;
    private final String roleCode;
    private final String roleName;
    private final String organizationId;
    private final String branchId;
    private final boolean active;
    private final List<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, String email, String password, String fullName,
                         String roleCode, String roleName, String organizationId, String branchId,
                         boolean active, List<String> permissions,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.organizationId = organizationId;
        this.branchId = branchId;
        this.active = active;
        this.permissions = permissions != null ? permissions : List.of();
        this.authorities = authorities;
    }

    public static UserPrincipal create(AppUserEntity user, AppRoleEntity role, List<AppPermissionEntity> perms) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String roleCode = role != null ? role.getRoleCode() : "ROLE_VIEWER";
        if (!roleCode.startsWith("ROLE_")) {
            roleCode = "ROLE_" + roleCode.toUpperCase();
        }
        authorities.add(new SimpleGrantedAuthority(roleCode));

        List<String> permissionCodes = new ArrayList<>();
        if (perms != null) {
            for (AppPermissionEntity p : perms) {
                if (p.getPermissionCode() != null && !p.getPermissionCode().isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(p.getPermissionCode()));
                    permissionCodes.add(p.getPermissionCode());
                }
            }
        }

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                roleCode,
                role != null ? role.getRoleName() : roleCode,
                user.getOrganizationId(),
                user.getBranchId(),
                user.getIsActive() != null && user.getIsActive(),
                permissionCodes,
                authorities
        );
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getRoleCode() { return roleCode; }
    public String getRoleName() { return roleName; }
    public String getOrganizationId() { return organizationId; }
    public String getBranchId() { return branchId; }
    public List<String> getPermissions() { return permissions; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
