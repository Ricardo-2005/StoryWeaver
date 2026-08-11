package com.storyweaver.branching.application;

import com.storyweaver.impact.application.ImpactReportService;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterBranchService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final Clock clock;
    private final ImpactReportService impactReports;

    public ChapterBranchService(
            JdbcTemplate jdbc, ProjectAccessService access, Clock clock, ImpactReportService impactReports) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.impactReports = impactReports;
    }

    @Transactional
    public BranchView create(
            UUID chapterId,
            UUID userId,
            String name,
            String description,
            String title,
            String content,
            String changeSummary) {
        UUID projectId = project(chapterId);
        access.requireOwnedProject(projectId, userId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO chapter_branch(id,project_id,chapter_id,name,description,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id,
                projectId,
                chapterId,
                name.trim(),
                description,
                userId,
                now,
                now);
        if (content != null && !content.isBlank())
            addVersionInternal(id, userId, title == null || title.isBlank() ? name : title, content, changeSummary);
        return get(id, userId);
    }

    @Transactional(readOnly = true)
    public List<BranchView> list(UUID chapterId, UUID userId) {
        UUID projectId = project(chapterId);
        access.requireOwnedProject(projectId, userId);
        return jdbc.query(
                "SELECT * FROM chapter_branch WHERE chapter_id=? ORDER BY created_at DESC",
                (rs, row) -> map(rs, versions(rs.getObject("id", UUID.class))),
                chapterId);
    }

    @Transactional(readOnly = true)
    public BranchView get(UUID id, UUID userId) {
        BranchView value = jdbc.query(
                "SELECT * FROM chapter_branch WHERE id=?", rs -> rs.next() ? map(rs, versions(id)) : null, id);
        if (value == null) throw new NotFoundException("chapter_branch_not_found", "Chapter branch was not found");
        access.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    @Transactional
    public BranchView addVersion(
            UUID id, UUID userId, long expectedVersion, String title, String content, String changeSummary) {
        BranchView current = get(id, userId);
        if (current.version() != expectedVersion) throw stale();
        addVersionInternal(id, userId, title, content, changeSummary);
        int changed = jdbc.update(
                "UPDATE chapter_branch SET version=version+1,updated_at=? WHERE id=? AND version=?",
                clock.instant(),
                id,
                expectedVersion);
        if (changed == 0) throw stale();
        return get(id, userId);
    }

    @Transactional
    public BranchView promoteImpact(UUID id, UUID userId, long expectedVersion) {
        BranchView current = get(id, userId);
        if (current.version() != expectedVersion) throw stale();
        int changed = jdbc.update(
                "UPDATE chapter_branch SET promoted=TRUE,version=version+1,updated_at=? WHERE id=? AND version=?",
                clock.instant(),
                id,
                expectedVersion);
        if (changed == 0) throw stale();
        impactReports.create(current.chapterId(), userId);
        return get(id, userId);
    }

    private void addVersionInternal(UUID id, UUID userId, String title, String content, String summary) {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no),0)+1 FROM chapter_branch_version WHERE branch_id=?",
                Integer.class,
                id);
        jdbc.update(
                "INSERT INTO chapter_branch_version(id,branch_id,version_no,title,content,change_summary,created_by,created_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                next,
                title.trim(),
                content,
                summary,
                userId,
                clock.instant());
    }

    private List<BranchVersion> versions(UUID id) {
        return jdbc.query(
                "SELECT * FROM chapter_branch_version WHERE branch_id=? ORDER BY version_no DESC",
                (rs, row) -> new BranchVersion(
                        rs.getObject("id", UUID.class),
                        rs.getInt("version_no"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("change_summary"),
                        rs.getTimestamp("created_at").toInstant()),
                id);
    }

    private UUID project(UUID chapterId) {
        UUID value = jdbc.query(
                "SELECT project_id FROM chapter WHERE id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                chapterId);
        if (value == null) throw new NotFoundException("chapter_not_found", "Chapter was not found");
        return value;
    }

    private BranchView map(ResultSet rs, List<BranchVersion> versions) throws SQLException {
        return new BranchView(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getBoolean("promoted"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                versions);
    }

    private ConflictException stale() {
        return new ConflictException("stale_version", "Chapter branch was changed by another request");
    }

    public record BranchVersion(
            UUID id, int versionNo, String title, String content, String changeSummary, Instant createdAt) {}

    public record BranchView(
            UUID id,
            UUID projectId,
            UUID chapterId,
            String name,
            String description,
            String status,
            boolean promoted,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<BranchVersion> versions) {}
}
