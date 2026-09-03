package com.orbit.domain.config;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stages")
public class Stage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 50;

    @Column(name = "category", nullable = false, length = 20)
    private String category = "in-progress";   // backlog | in-progress | qa | uat | blocked | ready | released | closed

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer v) { this.displayOrder = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}
