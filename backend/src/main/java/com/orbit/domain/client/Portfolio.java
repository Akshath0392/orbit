package com.orbit.domain.client;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name;
    private String description;
    private Boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "portfolio_clients",
        joinColumns = @JoinColumn(name = "portfolio_id"),
        inverseJoinColumns = @JoinColumn(name = "client_id")
    )
    private Set<Client> clients = new LinkedHashSet<>();

    public Portfolio() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean v) { this.active = v; }
    public Set<Client> getClients() { return clients; }
    public void setClients(Set<Client> v) { this.clients = v; }
}
