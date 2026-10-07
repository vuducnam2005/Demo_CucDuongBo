package vn.gov.drvn.kcht.dto;

import java.util.List;

public class UserSummaryDto {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private String roleName;
    private String organizationId;
    private String branchId;
    private boolean active;
    private List<String> permissions;

    public UserSummaryDto() {}

    public UserSummaryDto(Long id, String username, String email, String fullName, String role,
                          String roleName, String organizationId, String branchId, boolean active,
                          List<String> permissions) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.roleName = roleName;
        this.organizationId = organizationId;
        this.branchId = branchId;
        this.active = active;
        this.permissions = permissions;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}
