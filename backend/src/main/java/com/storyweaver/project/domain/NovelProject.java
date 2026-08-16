package com.storyweaver.project.domain;

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
@Table(name = "novel_project")
public class NovelProject {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 80)
    private String genre;

    @Column(name = "custom_genre", length = 20)
    private String customGenre;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 20)
    private TargetAudience targetAudience;

    @Enumerated(EnumType.STRING)
    @Column(name = "narrative_perspective", nullable = false, length = 20)
    private NarrativePerspective narrativePerspective;

    @Enumerated(EnumType.STRING)
    @Column(name = "length_type", nullable = false, length = 20)
    private LengthType lengthType;

    @Column(length = 500)
    private String premise;

    @Column(length = 300)
    private String description;

    @Column(name = "author_intent")
    private String authorIntent;

    @Column(name = "current_focus")
    private String currentFocus;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "world_rules", nullable = false, columnDefinition = "text[]")
    private String[] worldRules = new String[0];

    @Column(name = "target_word_count")
    private Integer targetWordCount;

    @Column(name = "chapter_word_target")
    private Integer chapterWordTarget;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "creation_source", nullable = false, length = 24)
    private String creationSource = "MANUAL";

    @Column(name = "reconstruction_status", nullable = false, length = 24)
    private String reconstructionStatus = "NOT_ANALYZED";

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NovelProject() {}

    public NovelProject(
            UUID ownerId,
            String name,
            String genre,
            String customGenre,
            TargetAudience targetAudience,
            NarrativePerspective narrativePerspective,
            LengthType lengthType,
            String premise,
            String description,
            String authorIntent,
            String currentFocus,
            String[] worldRules,
            Integer targetWordCount,
            Integer chapterWordTarget,
            Instant now) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.genre = genre;
        this.customGenre = customGenre;
        this.targetAudience = targetAudience;
        this.narrativePerspective = narrativePerspective;
        this.lengthType = lengthType;
        this.premise = premise;
        this.description = description;
        this.authorIntent = authorIntent;
        this.currentFocus = currentFocus;
        this.worldRules = copy(worldRules);
        this.targetWordCount = targetWordCount;
        this.chapterWordTarget = chapterWordTarget;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String name,
            String genre,
            String customGenre,
            TargetAudience targetAudience,
            NarrativePerspective narrativePerspective,
            LengthType lengthType,
            String premise,
            String description,
            String authorIntent,
            String currentFocus,
            String[] worldRules,
            Integer targetWordCount,
            Integer chapterWordTarget,
            boolean archived,
            Instant now) {
        this.name = name;
        this.genre = genre;
        this.customGenre = customGenre;
        this.targetAudience = targetAudience;
        this.narrativePerspective = narrativePerspective;
        this.lengthType = lengthType;
        this.premise = premise;
        this.description = description;
        this.authorIntent = authorIntent;
        this.currentFocus = currentFocus;
        this.worldRules = copy(worldRules);
        this.targetWordCount = targetWordCount;
        this.chapterWordTarget = chapterWordTarget;
        this.archived = archived;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getGenre() {
        return genre;
    }

    public String getCustomGenre() {
        return customGenre;
    }

    public TargetAudience getTargetAudience() {
        return targetAudience;
    }

    public NarrativePerspective getNarrativePerspective() {
        return narrativePerspective;
    }

    public LengthType getLengthType() {
        return lengthType;
    }

    public String getPremise() {
        return premise;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthorIntent() {
        return authorIntent;
    }

    public String getCurrentFocus() {
        return currentFocus;
    }

    public String[] getWorldRules() {
        return copy(worldRules);
    }

    public Integer getTargetWordCount() {
        return targetWordCount;
    }

    public Integer getChapterWordTarget() {
        return chapterWordTarget;
    }

    public boolean isArchived() {
        return archived;
    }

    public String getCreationSource() {
        return creationSource;
    }

    public String getReconstructionStatus() {
        return reconstructionStatus;
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

    private String[] copy(String[] values) {
        return values == null ? new String[0] : Arrays.copyOf(values, values.length);
    }
}
