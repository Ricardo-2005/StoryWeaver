package com.storyweaver.production.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
                ? new RollingOutlineView(
                        projectId,
                        1,
                        5,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        false,
                        0,
                        clock.instant())
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
                    "UPDATE rolling_outline SET current_chapter_no=?,window_size=?,summary=?,goals_json=CAST(? AS jsonb),risks_json=CAST(? AS jsonb),content_hash=NULL,stale=FALSE,version=version+1,updated_at=? WHERE project_id=? AND version=?",
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
                    Timestamp.from(now));
        }
        return get(projectId, userId);
    }

    @Transactional
    public boolean applyReconstruction(
            UUID projectId,
            UUID userId,
            int currentChapterNo,
            UUID baseChapterId,
            int fromChapterNo,
            List<UUID> sourceChapterIds,
            String summary,
            String contentHash) {
        access.requireOwnedProject(projectId, userId);
        ReconstructionOwnership current = jdbc.query(
                "SELECT summary,content_hash FROM rolling_outline WHERE project_id=?",
                rs -> rs.next() ? new ReconstructionOwnership(rs.getString(1), rs.getString(2)) : null,
                projectId);
        if (current != null
                && current.contentHash() == null
                && current.summary() != null
                && !current.summary().isBlank()) {
            return false;
        }

        Instant now = clock.instant();
        String sourceIds = "{"
                + String.join(",", sourceChapterIds.stream().map(UUID::toString).toList()) + "}";
        if (current == null) {
            jdbc.update(
                    """
                    INSERT INTO rolling_outline(
                        project_id,current_chapter_no,window_size,summary,goals_json,risks_json,
                        base_chapter_id,from_chapter_no,to_chapter_no,source_chapter_ids,
                        content_hash,stale,updated_at)
                    VALUES (?,?,5,?,'[]'::jsonb,'[]'::jsonb,?,?,?,CAST(? AS uuid[]),?,FALSE,?)
                    """,
                    projectId,
                    currentChapterNo,
                    summary,
                    baseChapterId,
                    fromChapterNo,
                    currentChapterNo,
                    sourceIds,
                    contentHash,
                    Timestamp.from(now));
        } else {
            jdbc.update(
                    """
                    UPDATE rolling_outline SET current_chapter_no=?,summary=?,base_chapter_id=?,
                        from_chapter_no=?,to_chapter_no=?,source_chapter_ids=CAST(? AS uuid[]),content_hash=?,
                        stale=FALSE,version=version+1,updated_at=? WHERE project_id=?
                    """,
                    currentChapterNo,
                    summary,
                    baseChapterId,
                    fromChapterNo,
                    currentChapterNo,
                    sourceIds,
                    contentHash,
                    Timestamp.from(now),
                    projectId);
        }
        return true;
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
                rs.getObject("base_chapter_id", UUID.class),
                (Integer) rs.getObject("from_chapter_no"),
                (Integer) rs.getObject("to_chapter_no"),
                json.readValue(rs.getString("open_threads_json"), List.class),
                json.readValue(rs.getString("current_locations_json"), List.class),
                json.readValue(rs.getString("active_items_json"), List.class),
                json.readValue(rs.getString("active_foreshadow_json"), List.class),
                json.readValue(rs.getString("next_constraints_json"), List.class),
                rs.getBoolean("stale"),
                rs.getLong("version"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private ConflictException stale() {
        return new ConflictException("stale_version", "Rolling outline was changed by another request");
    }

    private record ReconstructionOwnership(String summary, String contentHash) {}

    public record RollingOutlineView(
            UUID projectId,
            int currentChapterNo,
            int windowSize,
            String summary,
            List<String> goals,
            List<String> risks,
            UUID baseChapterId,
            Integer fromChapterNo,
            Integer toChapterNo,
            List<String> openThreads,
            List<String> currentLocations,
            List<String> activeItems,
            List<String> activeForeshadow,
            List<String> nextConstraints,
            boolean stale,
            long version,
            Instant updatedAt) {}
}
