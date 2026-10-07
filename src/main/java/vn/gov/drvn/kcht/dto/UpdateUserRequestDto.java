package vn.gov.drvn.kcht.dto;

import jakarta.validation.constraints.Email;

public class UpdateUserRequestDto {

    @Email(message = "Email không đúng định dạng")
    private String email;

    private String fullName;
    private String roleCode;
    private String branchId;
    private String organizationId;
    private Boolean active;

    public UpdateUserRequestDto() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) { this.branchId = branchId; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
