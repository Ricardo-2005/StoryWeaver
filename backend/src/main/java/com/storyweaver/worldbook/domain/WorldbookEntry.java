package com.storyweaver.worldbook.domain;

import com.storyweaver.llm.domain.EmbeddingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "worldbook_entry")
public class WorldbookEntry {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "worldbook_id", nullable = false)
    private UUID worldbookId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "constant_enabled", nullable = false)
    private boolean constantEnabled;

    @Column(name = "vector_enabled", nullable = false)
    private boolean vectorEnabled;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] keywords;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 24)
    private WorldbookScope scopeType;

    @Column(name = "scope_ref_id")
    private UUID scopeRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility_type", nullable = false, length = 24)
    private WorldbookVisibility visibilityType;

    @Column(name = "visibility_ref_id")
    private UUID visibilityRefId;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", nullable = false, length = 24)
    private EmbeddingStatus embeddingStatus;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorldbookEntry() {}

    public WorldbookEntry(
            UUID projectId,
            UUID worldbookId,
            String title,
            String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            String[] keywords,
            int priority,
            WorldbookScope scopeType,
            UUID scopeRefId,
            WorldbookVisibility visibilityType,
            UUID visibilityRefId,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.worldbookId = worldbookId;
        this.embeddingStatus = EmbeddingStatus.NOT_REQUESTED;
        revise(
                title,
                content,
                active,
                constantEnabled,
                vectorEnabled,
                keywords,
                priority,
                scopeType,
                scopeRefId,
                visibilityType,
                visibilityRefId,
                now);
        this.createdAt = now;
    }

    public void revise(
            String title,
            String content,
            boolean active,
            boolean constantEnabled,
            boolean vectorEnabled,
            String[] keywords,
            int priority,
            WorldbookScope scopeType,
            UUID scopeRefId,
            WorldbookVisibility visibilityType,
            UUID visibilityRefId,
            Instant now) {
        this.title = title;
        this.content = content;
        this.active = active;
        this.constantEnabled = constantEnabled;
        this.vectorEnabled = vectorEnabled;
        this.keywords = Arrays.copyOf(keywords, keywords.length);
        this.priority = priority;
        this.scopeType = scopeType;
        this.scopeRefId = scopeRefId;
        this.visibilityType = visibilityType;
        this.visibilityRefId = visibilityRefId;
        this.updatedAt = now;
    }

    public void embedding(EmbeddingStatus status, String model, Instant now) {
        this.embeddingStatus = status;
        this.embeddingModel = model;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isConstantEnabled() {
        return constantEnabled;
    }

    public boolean isVectorEnabled() {
        return vectorEnabled;
    }

    public String[] getKeywords() {
        return Arrays.copyOf(keywords, keywords.length);
    }

    public int getPriority() {
        return priority;
    }

    public WorldbookScope getScopeType() {
        return scopeType;
    }

    public UUID getScopeRefId() {
        return scopeRefId;
    }

    public WorldbookVisibility getVisibilityType() {
        return visibilityType;
    }

    public UUID getVisibilityRefId() {
        return visibilityRefId;
    }

    public EmbeddingStatus getEmbeddingStatus() {
        return embeddingStatus;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
