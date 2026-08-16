package com.storyweaver.consistency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "character_knowledge")
public class CharacterKnowledge {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "character_id", nullable = false)
    private UUID characterId;

    @Column(name = "fact_key", nullable = false, length = 160)
    private String factKey;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeCertainty certainty;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "acquired_chapter_id", nullable = false)
    private UUID acquiredChapterId;

    @Column(nullable = false)
    private String evidence;

    @Column(name = "learned_at_chapter_no", nullable = false)
    private int learnedAtChapterNo;

    @Column(name = "forgotten_at_chapter_no")
    private Integer forgottenAtChapterNo;

    @Column(name = "lifecycle_status", nullable = false, length = 16)
    private String lifecycleStatus;

    @Column(name = "retrieval_eligible", nullable = false)
    private boolean retrievalEligible;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CharacterKnowledge() {}

    public CharacterKnowledge(
            UUID projectId,
            UUID characterId,
            String factKey,
            String content,
            KnowledgeCertainty certainty,
            UUID sourceEventId,
            UUID acquiredChapterId,
            String evidence,
            Instant now) {
        this(projectId, characterId, factKey, content, certainty, sourceEventId, acquiredChapterId, 1, evidence, now);
    }

    public CharacterKnowledge(
            UUID projectId,
            UUID characterId,
            String factKey,
            String content,
            KnowledgeCertainty certainty,
            UUID sourceEventId,
            UUID acquiredChapterId,
            int learnedAtChapterNo,
            String evidence,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.characterId = characterId;
        this.factKey = factKey;
        this.learnedAtChapterNo = learnedAtChapterNo;
        this.lifecycleStatus = "ACTIVE";
        this.retrievalEligible = true;
        this.createdAt = now;
        update(content, certainty, sourceEventId, acquiredChapterId, evidence, now);
    }

    public void supersede(int validToChapterNo, UUID supersededBy, Instant now) {
        this.forgottenAtChapterNo = validToChapterNo;
        this.lifecycleStatus = "SUPERSEDED";
        this.retrievalEligible = false;
        this.supersededBy = supersededBy;
        this.updatedAt = now;
    }

    public void update(
            String content,
            KnowledgeCertainty certainty,
            UUID sourceEventId,
            UUID acquiredChapterId,
            String evidence,
            Instant now) {
        this.content = content;
        this.certainty = certainty;
        this.sourceEventId = sourceEventId;
        this.acquiredChapterId = acquiredChapterId;
        this.evidence = evidence;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getCharacterId() {
        return characterId;
    }

    public String getFactKey() {
        return factKey;
    }

    public String getContent() {
        return content;
    }

    public KnowledgeCertainty getCertainty() {
        return certainty;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public UUID getAcquiredChapterId() {
        return acquiredChapterId;
    }

    public String getEvidence() {
        return evidence;
    }

    public long getVersion() {
        return version;
    }

    public int getLearnedAtChapterNo() {
        return learnedAtChapterNo;
    }

    public Integer getForgottenAtChapterNo() {
        return forgottenAtChapterNo;
    }

    public boolean isRetrievalEligible() {
        return retrievalEligible && "ACTIVE".equals(lifecycleStatus);
    }
}
