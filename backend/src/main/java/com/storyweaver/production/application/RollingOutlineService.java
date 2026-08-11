package com.storyweaver.production.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RollingOutlineService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final ObjectMapper json;
    private final Clock clock;

    public RollingOutlineService(JdbcTemplate jdbc, ProjectAccessService access, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RollingOutlineView get(UUID projectId, UUID userId) {
        access.requireOwnedProject(projectId, userId);
        RollingOutlineView value = jdbc.query(
                "SELECT * FROM rolling_outline WHERE project_id=?", rs -> rs.next() ? map(rs) : null, projectId);
        return value == null
                ? new RollingOutlineView(projectId, 1, 5, null, List.of(), List.of(), 0, clock.instant())
                : value;
    }

    @Transactional
    public RollingOutlineView put(
            UUID projectId,
            UUID userId,
            long expectedVersion,
            int currentChapterNo,
            int windowSize,
            String summary,
            List<String> goals,
            List<String> risks) {
        access.requireOwnedProject(projectId, userId);
        RollingOutlineView current = get(projectId, userId);
        Instant now = clock.instant();
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rolling_outline WHERE project_id=?", Integer.class, projectId);
        if (exists != null && exists > 0) {
            if (current.version() != expectedVersion) throw stale();
            int changed = jdbc.update(
                    "UPDATE rolling_outline SET current_chapter_no=?,window_size=?,summary=?,goals_json=CAST(? AS jsonb),risks_json=CAST(? AS jsonb),version=version+1,updated_at=? WHERE project_id=? AND version=?",
                    currentChapterNo,
                    windowSize,
                    summary,
                    json.writeValueAsString(goals),
                    json.writeValueAsString(risks),
                    now,
                    projectId,
                    expectedVersion);
            if (changed == 0) throw stale();
        } else {
            if (expectedVersion != 0) throw stale();
            jdbc.update(
                    "INSERT INTO rolling_outline(project_id,current_chapter_no,window_size,summary,goals_json,risks_json,updated_at) VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?)",
                    projectId,
                    currentChapterNo,
                    windowSize,
                    summary,
                    json.writeValueAsString(goals),
                    json.writeValueAsString(risks),
                    now);
        }
        return get(projectId, userId);
    }

    @Transactional
    public RollingOutlineView advance(
            UUID projectId, UUID userId, long expectedVersion, String summary, List<String> goals, List<String> risks) {
        RollingOutlineView current = get(projectId, userId);
        return put(
                projectId,
                userId,
                expectedVersion,
                current.currentChapterNo() + 1,
                current.windowSize(),
                summary,
                goals,
                risks);
    }

    @SuppressWarnings("unchecked")
    private RollingOutlineView map(ResultSet rs) throws SQLException {
        return new RollingOutlineView(
                rs.getObject("project_id", UUID.class),
                rs.getInt("current_chapter_no"),
                rs.getInt("window_size"),
                rs.getString("summary"),
                json.readValue(rs.getString("goals_json"), List.class),
                json.readValue(rs.getString("risks_json"), List.class),
                rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private ConflictException stale() {
        return new ConflictException("stale_version", "Rolling outline was changed by another request");
    }

    public record RollingOutlineView(
            UUID projectId,
            int currentChapterNo,
            int windowSize,
            String summary,
            List<String> goals,
            List<String> risks,
            long version,
            Instant updatedAt) {}
}
