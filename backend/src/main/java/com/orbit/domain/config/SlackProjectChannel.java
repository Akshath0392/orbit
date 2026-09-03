package com.orbit.domain.config;

import jakarta.persistence.*;

@Entity
@Table(name = "slack_project_channels")
public class SlackProjectChannel {

    @Id
    private Long projectId;

    private String channelId;

    private String channelName;

    public SlackProjectChannel() {}

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String v) { this.channelId = v; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String v) { this.channelName = v; }
}
