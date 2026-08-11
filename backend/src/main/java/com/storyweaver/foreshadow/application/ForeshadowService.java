package com.storyweaver.foreshadow.application;

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
public class ForeshadowService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public ForeshadowService(JdbcTemplate jdbc, ProjectAccessService projectAccess, Clock clock) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @Transactional
    public ForeshadowView create(UUID projectId, UUID userId, ForeshadowInput input) {
        projectAccess.requireOwnedProject(projectId, userId);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO foreshadow(id,project_id,title,description,status,planted_chapter_id,target_chapter_no,resolved_chapter_id,notes,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id,
                projectId,
                input.title().trim(),
                input.description(),
                "PLANNED",
                input.plantedChapterId(),
                input.targetChapterNo(),
                null,
                input.notes(),
                now,
                now);
        return get(id, userId);
    }

    @Transactional(readOnly = true)
    public List<ForeshadowView> list(UUID projectId, UUID userId) {
        projectAccess.requireOwnedProject(projectId, userId);
        return jdbc.query(
                "SELECT * FROM foreshadow WHERE project_id=? ORDER BY updated_at DESC",
                (rs, row) -> map(rs),
                projectId);
    }

    @Transactional(readOnly = true)
    public ForeshadowView get(UUID id, UUID userId) {
        ForeshadowView value = jdbc.query("SELECT * FROM foreshadow WHERE id=?", rs -> rs.next() ? map(rs) : null, id);
        if (value == null) throw new NotFoundException("foreshadow_not_found", "Foreshadow was not found");
        projectAccess.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    @Transactional
    public ForeshadowView update(UUID id, UUID userId, long expectedVersion, ForeshadowInput input) {
        ForeshadowView current = get(id, userId);
        requireVersion(current.version(), expectedVersion);
        int updated = jdbc.update(
                "UPDATE foreshadow SET title=?,description=?,planted_chapter_id=?,target_chapter_no=?,notes=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                input.title().trim(),
                input.description(),
                input.plantedChapterId(),
                input.targetChapterNo(),
                input.notes(),
                clock.instant(),
                id,
                expectedVersion);
        if (updated == 0) throw stale();
        return get(id, userId);
    }

    @Transactional
    public ForeshadowView transition(
            UUID id, UUID userId, long expectedVersion, String status, UUID resolvedChapterId) {
        ForeshadowView current = get(id, userId);
        requireVersion(current.version(), expectedVersion);
        String next = status.toUpperCase();
        if (!allowed(current.status(), next))
            throw new ConflictException("foreshadow_transition_invalid", "Foreshadow status transition is not allowed");
        if ("RESOLVED".equals(next) && resolvedChapterId == null)
            throw new ConflictException("foreshadow_resolution_missing", "Resolved chapter is required");
        int updated = jdbc.update(
                "UPDATE foreshadow SET status=?,resolved_chapter_id=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                next,
                resolvedChapterId,
                clock.instant(),
                id,
                expectedVersion);
        if (updated == 0) throw stale();
        return get(id, userId);
    }

    private boolean allowed(String from, String to) {
        if ("ABANDONED".equals(to)) return !List.of("RESOLVED", "ABANDONED").contains(from);
        return switch (from) {
            case "PLANNED" -> "PLANTED".equals(to);
            case "PLANTED" -> List.of("ADVANCED", "RESOLVED").contains(to);
            case "ADVANCED" -> List.of("ADVANCED", "RESOLVED").contains(to);
            default -> false;
        };
    }

    private ForeshadowView map(ResultSet rs) throws SQLException {
        return new ForeshadowView(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getObject("planted_chapter_id", UUID.class),
                (Integer) rs.getObject("target_chapter_no"),
                rs.getObject("resolved_chapter_id", UUID.class),
                rs.getString("notes"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) throw stale();
    }

    private ConflictException stale() {
        return new ConflictException("stale_version", "Foreshadow was changed by another request");
    }

    public record ForeshadowInput(
            String title, String description, UUID plantedChapterId, Integer targetChapterNo, String notes) {}

    public record ForeshadowView(
            UUID id,
            UUID projectId,
            String title,
            String description,
            String status,
            UUID plantedChapterId,
            Integer targetChapterNo,
            UUID resolvedChapterId,
            String notes,
            long version,
            Instant createdAt,
            Instant updatedAt) {}
}
