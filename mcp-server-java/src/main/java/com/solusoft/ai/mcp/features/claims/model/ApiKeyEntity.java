package com.solusoft.ai.mcp.features.claims.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("api_keys") // JDBC Annotation
public class ApiKeyEntity {

    @Id
    private Long id;

    @Column("key_hash")
    private String keyHash;

    private String role;
    private String owner;
    private boolean active = true;
    
    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // -- Standard Getters and Setters --
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}