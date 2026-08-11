package com.storyweaver.impact.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ImpactReportService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccess;
    private final ObjectMapper json;
    private final Clock clock;

    public ImpactReportService(JdbcTemplate jdbc, ProjectAccessService projectAccess, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.projectAccess = projectAccess;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ImpactReport create(UUID chapterId, UUID userId) {
        UUID projectId = projectForChapter(chapterId);
        projectAccess.requireOwnedProject(projectId, userId);
        Map<String, Object> affected = new LinkedHashMap<>();
        affected.put(
                "foreshadows",
                jdbc.queryForList(
                        "SELECT id,title,status FROM foreshadow WHERE project_id=? AND (planted_chapter_id=? OR resolved_chapter_id=?)",
                        projectId,
                        chapterId,
                        chapterId));
        affected.put(
                "workflowFacts",
                jdbc.queryForList(
                        "SELECT sf.fact_key,sf.content,sf.status FROM story_fact sf JOIN workflow_run wr ON wr.id=sf.workflow_run_id WHERE wr.chapter_id=?",
                        chapterId));
        affected.put(
                "laterChapters",
                jdbc.queryForList(
                        "SELECT id,chapter_no,title,status FROM chapter WHERE project_id=? AND chapter_no>(SELECT chapter_no FROM chapter WHERE id=?) ORDER BY chapter_no LIMIT 20",
                        projectId,
                        chapterId));
        String summary = "Found %d foreshadow links, %d workflow facts and %d later chapters to review"
                .formatted(
                        ((List<?>) affected.get("foreshadows")).size(),
                        ((List<?>) affected.get("workflowFacts")).size(),
                        ((List<?>) affected.get("laterChapters")).size());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO impact_report(id,project_id,chapter_id,status,summary,affected_json,created_by,created_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?,?)",
                id,
                projectId,
                chapterId,
                "READY",
                summary,
                toJson(affected),
                userId,
                now);
        return get(id, userId);
    }

    @Transactional(readOnly = true)
    public List<ImpactReport> list(UUID chapterId, UUID userId) {
        UUID projectId = projectForChapter(chapterId);
        projectAccess.requireOwnedProject(projectId, userId);
        return jdbc.query(
                "SELECT * FROM impact_report WHERE chapter_id=? ORDER BY created_at DESC",
                (rs, row) -> map(rs),
                chapterId);
    }

    @Transactional(readOnly = true)
    public ImpactReport get(UUID id, UUID userId) {
        ImpactReport value = jdbc.query("SELECT * FROM impact_report WHERE id=?", rs -> rs.next() ? map(rs) : null, id);
        if (value == null) throw new NotFoundException("impact_report_not_found", "Impact report was not found");
        projectAccess.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    private UUID projectForChapter(UUID chapterId) {
        UUID value = jdbc.query(
                "SELECT project_id FROM chapter WHERE id=?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                chapterId);
        if (value == null) throw new NotFoundException("chapter_not_found", "Chapter was not found");
        return value;
    }

    @SuppressWarnings("unchecked")
    private ImpactReport map(ResultSet rs) throws SQLException {
        return new ImpactReport(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getString("status"),
                rs.getString("summary"),
                json.readValue(rs.getString("affected_json"), Map.class),
                rs.getTimestamp("created_at").toInstant());
    }

    private String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    public record ImpactReport(
            UUID id,
            UUID projectId,
            UUID chapterId,
            String status,
            String summary,
            Map<String, Object> affected,
            Instant createdAt) {}
}
