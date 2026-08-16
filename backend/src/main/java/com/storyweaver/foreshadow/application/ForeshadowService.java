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
                "CANDIDATE",
                input.plantedChapterId(),
                input.targetChapterNo(),
                null,
                input.notes(),
                now,
                now);
        linkMatchingReconstructionCandidate(id, projectId, input.description(), now);
        return get(id, userId);
    }

    @Transactional
    public void cancel(UUID id, UUID userId) {
        ForeshadowView current = get(id, userId);
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE project_reconstruction_candidate candidate
                SET status='CANDIDATE',target_entity_id=NULL,applied_at=NULL,
                    retrieval_eligible=TRUE,revoked_at=NULL,revoked_by=NULL,
                    revocation_reason=NULL,superseded_by=NULL,
                    policy_reason='Foreshadow ledger entry was cancelled; candidate restored for review',updated_at=?
                FROM foreshadow f
                WHERE f.id=? AND f.project_id=?
                  AND candidate.candidate_type='FORESHADOW'
                  AND candidate.id=f.source_candidate_id
                  AND candidate.target_entity_id=f.id
                  AND candidate.status IN ('APPLIED','REVOKED')
                """,
                now,
                id,
                current.projectId());
        jdbc.update("DELETE FROM foreshadow WHERE id=? AND project_id=?", id, current.projectId());
    }

    @Transactional
    public List<ForeshadowView> list(UUID projectId, UUID userId) {
        projectAccess.requireOwnedProject(projectId, userId);
        jdbc.update(
                """
                UPDATE foreshadow f SET status='DUE',version=version+1,updated_at=?
                WHERE project_id=? AND status IN ('PLANTED','DEVELOPING')
                  AND target_chapter_no IS NOT NULL
                  AND target_chapter_no <= (SELECT COALESCE(MAX(chapter_no),0) FROM chapter WHERE project_id=f.project_id)
                """,
                clock.instant(),
                projectId);
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
        boolean retrievalEligible =
                !List.of("RESOLVED", "ABANDONED", "REJECTED").contains(next);
        int updated = jdbc.update(
                "UPDATE foreshadow SET status=?,resolved_chapter_id=?,retrieval_eligible=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                next,
                resolvedChapterId,
                retrievalEligible,
                clock.instant(),
                id,
                expectedVersion);
        if (updated == 0) throw stale();
        return get(id, userId);
    }

    private boolean allowed(String from, String to) {
        if (List.of("ABANDONED", "REJECTED").contains(to)) {
            return !List.of("RESOLVED", "ABANDONED", "REJECTED").contains(from);
        }
        return switch (from) {
            case "CANDIDATE" -> "PLANTED".equals(to);
            case "PLANTED" ->
                List.of("DEVELOPING", "DUE", "RESOLVED", "PARTIALLY_RESOLVED").contains(to);
            case "DEVELOPING" ->
                List.of("DEVELOPING", "DUE", "RESOLVED", "PARTIALLY_RESOLVED").contains(to);
            case "DUE" ->
                List.of("DEVELOPING", "RESOLVED", "PARTIALLY_RESOLVED").contains(to);
            case "PARTIALLY_RESOLVED" ->
                List.of("DEVELOPING", "DUE", "RESOLVED").contains(to);
            default -> false;
        };
    }

    private void linkMatchingReconstructionCandidate(
            UUID foreshadowId, UUID projectId, String description, Instant now) {
        if (description == null || description.isBlank()) return;
        UUID candidateId = jdbc.query(
                """
                SELECT id FROM project_reconstruction_candidate
                WHERE project_id=? AND candidate_type='FORESHADOW'
                  AND status IN ('CANDIDATE','ACCEPTED','REVOKED') AND content=?
                  AND NOT EXISTS (
                    SELECT 1 FROM foreshadow WHERE source_candidate_id=project_reconstruction_candidate.id)
                ORDER BY created_at DESC,id DESC LIMIT 1
                """,
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                projectId,
                description);
        if (candidateId == null) return;
        jdbc.update("UPDATE foreshadow SET source_candidate_id=? WHERE id=?", candidateId, foreshadowId);
        jdbc.update(
                """
                UPDATE project_reconstruction_candidate
                SET target_entity_id=?,status='APPLIED',applied_at=COALESCE(applied_at,?),
                    retrieval_eligible=TRUE,revoked_at=NULL,revoked_by=NULL,revocation_reason=NULL,
                    policy_reason='Saved to the foreshadow ledger by the user',updated_at=?
                WHERE id=? AND status IN ('CANDIDATE','ACCEPTED','REVOKED')
                """,
                foreshadowId,
                now,
                now,
                candidateId);
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
