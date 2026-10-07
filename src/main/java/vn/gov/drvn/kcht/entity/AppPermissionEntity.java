package vn.gov.drvn.kcht.entity;

import jakarta.persistence.*;

/**
 * Thực thể ánh xạ bảng phân quyền chi tiết (app_permission).
 */
@Entity
@Table(name = "app_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uq_app_permission", columnNames = {"role_id", "permission_code"})
})
public class AppPermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "resource", nullable = false, length = 100)
    private String resource;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    public AppPermissionEntity() {}

    public AppPermissionEntity(Long roleId, String permissionCode, String resource, String action) {
        this.roleId = roleId;
        this.permissionCode = permissionCode;
        this.resource = resource;
        this.action = action;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getPermissionCode() { return permissionCode; }
    public void setPermissionCode(String permissionCode) { this.permissionCode = permissionCode; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
