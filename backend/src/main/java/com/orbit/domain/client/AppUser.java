package com.orbit.domain.client;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name="app_users")
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String name;
    private String email;
    private String password;
    private String role;
    private String initials;
    private String avatarColor;
    private Boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String darwinEmpId;

    @Column(name = "can_edit_budget")
    private Boolean canEditBudget = false;

    @Column(name = "slack_user_id")
    private String slackUserId;

    public AppUser() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name=v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email=v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { this.password=v; }
    public String getRole() { return role; }
    public void setRole(String v) { this.role=v; }
    public String getInitials() { return initials; }
    public void setInitials(String v) { this.initials=v; }
    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String v) { this.avatarColor=v; }
    public Boolean getActive() { return active; }
    public String getDarwinEmpId() { return darwinEmpId; }
    public void setDarwinEmpId(String v) { this.darwinEmpId=v; }
    public Boolean getCanEditBudget() { return canEditBudget; }
    public void setCanEditBudget(Boolean v) { this.canEditBudget = v; }
    public String getSlackUserId() { return slackUserId; }
    public void setSlackUserId(String v) { this.slackUserId = v; }
}
