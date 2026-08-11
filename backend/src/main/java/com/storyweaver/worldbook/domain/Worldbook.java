package com.storyweaver.worldbook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worldbook")
public class Worldbook {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false, unique = true)
    private UUID projectId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "default_token_budget", nullable = false)
    private int defaultTokenBudget;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Worldbook() {}

    public Worldbook(UUID projectId, String name, int defaultTokenBudget, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.defaultTokenBudget = defaultTokenBudget;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getDefaultTokenBudget() {
        return defaultTokenBudget;
    }
}
