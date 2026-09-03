package com.orbit.domain.client;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="client_dependencies")
public class ClientDependency {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="client_id") private Client client;
    private String title;
    @Column(columnDefinition="TEXT") private String description;
    private String depType;
    private LocalDate raisedAt;
    private LocalDate resolvedAt;
    private String status = "OPEN";

    public ClientDependency() {}
    public Long getId() { return id; }
    public Client getClient() { return client; }
    public void setClient(Client v) { this.client=v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title=v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description=v; }
    public String getDepType() { return depType; }
    public void setDepType(String v) { this.depType=v; }
    public LocalDate getRaisedAt() { return raisedAt; }
    public void setRaisedAt(LocalDate v) { this.raisedAt=v; }
    public LocalDate getResolvedAt() { return resolvedAt; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status=v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private ClientDependency d = new ClientDependency();
        public Builder client(Client v) { d.client=v; return this; }
        public Builder title(String v) { d.title=v; return this; }
        public Builder description(String v) { d.description=v; return this; }
        public Builder depType(String v) { d.depType=v; return this; }
        public Builder raisedAt(LocalDate v) { d.raisedAt=v; return this; }
        public Builder status(String v) { d.status=v; return this; }
        public ClientDependency build() { return d; }
    }
}
