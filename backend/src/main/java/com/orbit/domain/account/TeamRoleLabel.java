package com.orbit.domain.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "team_role_labels")
public class TeamRoleLabel {
    @Id @Column(name = "role_key") private String roleKey;
    private String label;

    public TeamRoleLabel() {}
    public TeamRoleLabel(String roleKey, String label) { this.roleKey = roleKey; this.label = label; }
    public String getRoleKey() { return roleKey; }
    public void setRoleKey(String v) { this.roleKey = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
}
