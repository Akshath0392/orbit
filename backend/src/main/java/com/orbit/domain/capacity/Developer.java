package com.orbit.domain.capacity;

import jakarta.persistence.*;

@Entity
@Table(name="developers")
public class Developer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String name;
    private String team;
    private Integer utilization = 0;
    private Integer activeTasks = 0;
    private Boolean onLeave = false;
    private String leavePeriod;
    private String initials;
    private String avatarColor;

    public Developer() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTeam() { return team; }
    public Integer getUtilization() { return utilization; }
    public Integer getActiveTasks() { return activeTasks; }
    public Boolean getOnLeave() { return onLeave; }
    public String getLeavePeriod() { return leavePeriod; }
    public String getInitials() { return initials; }
    public String getAvatarColor() { return avatarColor; }
}
