package com.storyweaver.chapter.domain;

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
@Table(name = "chapter")
public class Chapter {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "outline_node_id")
    private UUID outlineNodeId;

    @Column(name = "chapter_no", nullable = false)
    private int chapterNo;

    @Column(nullable = false, length = 160)
    private String title;

    private String outline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChapterStatus status;

    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Chapter() {}

    public Chapter(UUID projectId, UUID outlineNodeId, int chapterNo, String title, String outline, Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.outlineNodeId = outlineNodeId;
        this.chapterNo = chapterNo;
        this.title = title;
        this.outline = outline;
        this.status = ChapterStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateOutline(UUID outlineNodeId, String title, String outline, Instant now) {
        this.outlineNodeId = outlineNodeId;
        this.title = title;
        this.outline = outline;
        this.updatedAt = now;
    }

    public int addVersion(String title, Instant now) {
        this.title = title;
        this.currentVersionNo += 1;
        this.status = ChapterStatus.DRAFT;
        this.updatedAt = now;
        return currentVersionNo;
    }

    public int commitVersion(String title, Instant now) {
        this.title = title;
        this.currentVersionNo += 1;
        this.status = ChapterStatus.CONFIRMED;
        this.updatedAt = now;
        return currentVersionNo;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getOutlineNodeId() {
        return outlineNodeId;
    }

    public int getChapterNo() {
        return chapterNo;
    }

    public String getTitle() {
        return title;
    }

    public String getOutline() {
        return outline;
    }

    public ChapterStatus getStatus() {
        return status;
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
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
