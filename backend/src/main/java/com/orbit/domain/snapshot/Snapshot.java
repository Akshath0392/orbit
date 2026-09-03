package com.orbit.domain.snapshot;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_snapshots")
public class Snapshot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_run_id")
    private Long agentRunId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(name = "portfolio_id")
    private Long portfolioId;

    @Column(nullable = false, length = 32)
    private String lens;

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SnapshotState state;

    @Column(name = "png_path", columnDefinition = "TEXT")
    private String pngPath;

    @Column(name = "pdf_path", columnDefinition = "TEXT")
    private String pdfPath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public Snapshot() {}

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Long getAgentRunId() { return agentRunId; }
    public void setAgentRunId(Long v) { this.agentRunId = v; }

    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }

    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String v) { this.dedupKey = v; }

    public String getKind() { return kind; }
    public void setKind(String v) { this.kind = v; }

    public Long getPortfolioId() { return portfolioId; }
    public void setPortfolioId(Long v) { this.portfolioId = v; }

    public String getLens() { return lens; }
    public void setLens(String v) { this.lens = v; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long v) { this.projectId = v; }

    public SnapshotState getState() { return state; }
    public void setState(SnapshotState v) { this.state = v; }

    public String getPngPath() { return pngPath; }
    public void setPngPath(String v) { this.pngPath = v; }

    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String v) { this.pdfPath = v; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime v) { this.completedAt = v; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
}
