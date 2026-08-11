package com.storyweaver.chapter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chapter_version")
public class ChapterVersion {
    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "chapter_id", nullable = false)
    private UUID chapterId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private String content;

    private String summary;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "restored_from_version_no")
    private Integer restoredFromVersionNo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChapterVersion() {}

    public ChapterVersion(
            UUID projectId,
            UUID chapterId,
            int versionNo,
            String title,
            String content,
            String summary,
            String changeSummary,
            Integer restoredFromVersionNo,
            UUID createdBy,
            Instant now) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.chapterId = chapterId;
        this.versionNo = versionNo;
        this.title = title;
        this.content = content;
        this.summary = summary;
        this.changeSummary = changeSummary;
        this.restoredFromVersionNo = restoredFromVersionNo;
        this.createdBy = createdBy;
        this.createdAt = now;
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

    public int getVersionNo() {
        return versionNo;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSummary() {
        return summary;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public Integer getRestoredFromVersionNo() {
        return restoredFromVersionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
