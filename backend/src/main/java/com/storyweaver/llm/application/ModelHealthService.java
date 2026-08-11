package com.storyweaver.llm.application;

import com.storyweaver.llm.config.DeepSeekProperties;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelHealthService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final DeepSeekProperties properties;
    private final Clock clock;

    public ModelHealthService(
            JdbcTemplate jdbc, ProjectAccessService access, DeepSeekProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.properties = properties;
        this.clock = clock;
    }

    public List<ModelAttemptView> attempts(UUID runId, UUID userId) {
        RunWindow run = jdbc.query(
                "SELECT project_id,started_at,COALESCE(finished_at,?) finished_at FROM workflow_run WHERE id=?",
                rs -> rs.next()
                        ? new RunWindow(
                                rs.getObject(1, UUID.class),
                                rs.getTimestamp(2) == null
                                        ? Instant.EPOCH
                                        : rs.getTimestamp(2).toInstant(),
                                rs.getTimestamp(3).toInstant())
                        : null,
                clock.instant(),
                runId);
        if (run == null) throw new NotFoundException("workflow_not_found", "Workflow was not found");
        access.requireOwnedProject(run.projectId(), userId);
        return jdbc.query(
                "SELECT * FROM usage_record WHERE project_id=? AND requested_at BETWEEN ? AND ? ORDER BY requested_at",
                (rs, row) -> map(rs),
                run.projectId(),
                run.startedAt(),
                run.finishedAt());
    }

    public ModelHealthView health() {
        Integer failures = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usage_record WHERE status='FAILED' AND requested_at>?",
                Integer.class,
                clock.instant().minusSeconds(300));
        return new ModelHealthView(
                "deepseek",
                properties.configured() ? "AVAILABLE" : "NOT_CONFIGURED",
                failures == null ? 0 : failures,
                true,
                clock.instant());
    }

    private ModelAttemptView map(ResultSet rs) throws SQLException {
        return new ModelAttemptView(
                rs.getObject("id", UUID.class),
                rs.getString("agent"),
                "deepseek",
                rs.getString("model"),
                rs.getInt("attempts"),
                rs.getString("status"),
                rs.getLong("duration_ms"),
                rs.getTimestamp("requested_at").toInstant());
    }

    private record RunWindow(UUID projectId, Instant startedAt, Instant finishedAt) {}

    public record ModelAttemptView(
            UUID id,
            String agent,
            String provider,
            String model,
            int attempts,
            String status,
            long durationMillis,
            Instant requestedAt) {}

    public record ModelHealthView(
            String provider, String status, int failuresLastFiveMinutes, boolean fallbackEnabled, Instant checkedAt) {}
}
