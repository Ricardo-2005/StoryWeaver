package com.storyweaver.memory.domain;

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
@Table(name = "story_event")
public class StoryEvent {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @Column(name = "chapter_no")
    private Integer chapterNo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "participant_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] participantIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "known_by_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] knownByIds;

    @Column(length = 200)
    private String location;

    @Column(name = "story_time", length = 200)
    private String storyTime;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String result;

    @Column(nullable = false)
    private double importance;

    @Column(name = "evidence_paragraph", length = 200)
    private String evidenceParagraph;

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

    protected StoryEvent() {}

    public StoryEvent(UUID projectId, EventValues values, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.embeddingStatus = EmbeddingStatus.NOT_REQUESTED;
        revise(values, now);
        this.createdAt = now;
    }

    public void revise(EventValues values, Instant now) {
        this.chapterId = values.chapterId();
        this.chapterNo = values.chapterNo();
        this.participantIds = Arrays.copyOf(values.participantIds(), values.participantIds().length);
        this.knownByIds = Arrays.copyOf(values.knownByIds(), values.knownByIds().length);
        this.location = values.location();
        this.storyTime = values.storyTime();
        this.action = values.action();
        this.result = values.result();
        this.importance = values.importance();
        this.evidenceParagraph = values.evidenceParagraph();
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

    public UUID getChapterId() {
        return chapterId;
    }

    public Integer getChapterNo() {
        return chapterNo;
    }

    public UUID[] getParticipantIds() {
        return Arrays.copyOf(participantIds, participantIds.length);
    }

    public UUID[] getKnownByIds() {
        return Arrays.copyOf(knownByIds, knownByIds.length);
    }

    public String getLocation() {
        return location;
    }

    public String getStoryTime() {
        return storyTime;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
    }

    public double getImportance() {
        return importance;
    }

    public String getEvidenceParagraph() {
        return evidenceParagraph;
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

    public record EventValues(
            UUID chapterId,
            Integer chapterNo,
            UUID[] participantIds,
            UUID[] knownByIds,
            String location,
            String storyTime,
            String action,
            String result,
            double importance,
            String evidenceParagraph) {}
}
