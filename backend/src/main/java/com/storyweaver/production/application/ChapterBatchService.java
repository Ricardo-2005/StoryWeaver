package com.storyweaver.production.application;

import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.BadRequestException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.workflow.application.WorkflowApprovedEvent;
import com.storyweaver.workflow.application.WorkflowOrchestrator;
import com.storyweaver.workflow.application.WorkflowStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChapterBatchService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessService access;
    private final WorkflowStore workflows;
    private final WorkflowOrchestrator orchestrator;
    private final Clock clock;

    public ChapterBatchService(
            JdbcTemplate jdbc,
            ProjectAccessService access,
            WorkflowStore workflows,
            WorkflowOrchestrator orchestrator,
            Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.workflows = workflows;
        this.orchestrator = orchestrator;
        this.clock = clock;
    }

    public BatchView create(
            UUID projectId,
            UUID userId,
            UUID viewpointCharacterId,
            String instruction,
            List<UUID> chapterIds,
            List<UUID> gatedChapterIds) {
        access.requireOwnedProject(projectId, userId);
        if (chapterIds == null || chapterIds.isEmpty() || chapterIds.size() > 3)
            throw new BadRequestException("batch_size_invalid", "Chapter batch must contain 1 to 3 chapters");
        if (chapterIds.stream().distinct().count() != chapterIds.size())
            throw new BadRequestException("batch_chapter_duplicate", "Chapter batch contains duplicate chapters");
        Integer characters = jdbc.queryForObject(
                "SELECT COUNT(*) FROM character WHERE id=? AND project_id=?",
                Integer.class,
                viewpointCharacterId,
                projectId);
        if (characters == null || characters == 0)
            throw new NotFoundException("character_not_found", "Viewpoint character was not found");
        Set<UUID> actual = jdbc
                .query(
                        "SELECT id FROM chapter WHERE project_id=? AND id IN (" + placeholders(chapterIds.size()) + ")",
                        (rs, row) -> rs.getObject(1, UUID.class),
                        args(projectId, chapterIds))
                .stream()
                .collect(Collectors.toSet());
        if (actual.size() != chapterIds.size())
            throw new NotFoundException("chapter_not_found", "One or more chapters were not found");
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chapter_batch WHERE project_id=? AND status IN ('QUEUED','RUNNING','PAUSED','WAITING_GATE')",
                Integer.class,
                projectId);
        if (active != null && active > 0)
            throw new ConflictException(
                    "chapter_batch_project_busy", "This project already has an active chapter batch");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                "INSERT INTO chapter_batch(id,project_id,viewpoint_character_id,instruction,status,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                id,
                projectId,
                viewpointCharacterId,
                instruction.trim(),
                "QUEUED",
                userId,
                now,
                now);
        int sequence = 1;
        for (UUID chapterId : chapterIds)
            jdbc.update(
                    "INSERT INTO chapter_batch_item(id,batch_id,sequence_no,chapter_id,status) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    id,
                    sequence++,
                    chapterId,
                    "QUEUED");
        Set<UUID> gates = gatedChapterIds == null ? Set.of() : Set.copyOf(gatedChapterIds);
        if (!actual.containsAll(gates))
            throw new BadRequestException("batch_gate_chapter_invalid", "Gate chapter must belong to the batch");
        for (UUID chapterId : gates)
            jdbc.update(
                    "INSERT INTO story_gate(id,project_id,batch_id,chapter_id,gate_type,title,rationale,status,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    projectId,
                    id,
                    chapterId,
                    "MAJOR_PLOT",
                    "Major plot confirmation",
                    "This chapter was explicitly marked as a major plot point when the batch was created",
                    "PENDING",
                    now);
        startNext(id, userId);
        return get(id, userId);
    }

    public List<BatchView> list(UUID projectId, UUID userId) {
        access.requireOwnedProject(projectId, userId);
        return jdbc.query(
                "SELECT id FROM chapter_batch WHERE project_id=? ORDER BY created_at DESC",
                (rs, row) -> get(rs.getObject(1, UUID.class), userId),
                projectId);
    }

    public BatchView get(UUID id, UUID userId) {
        BatchView value = jdbc.query("SELECT * FROM chapter_batch WHERE id=?", rs -> rs.next() ? map(rs) : null, id);
        if (value == null) throw new NotFoundException("chapter_batch_not_found", "Chapter batch was not found");
        access.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    public BatchView pause(UUID id, UUID userId) {
        BatchView value = get(id, userId);
        if (!List.of("RUNNING", "WAITING_GATE", "QUEUED").contains(value.status()))
            throw new ConflictException("chapter_batch_not_pausable", "Chapter batch cannot be paused");
        touch(id, "PAUSED");
        return get(id, userId);
    }

    public BatchView resume(UUID id, UUID userId) {
        BatchView value = get(id, userId);
        if (!"PAUSED".equals(value.status()))
            throw new ConflictException("chapter_batch_not_paused", "Chapter batch is not paused");
        touch(id, "RUNNING");
        if (value.items().stream().noneMatch(i -> "RUNNING".equals(i.status()))) startNext(id, userId);
        return get(id, userId);
    }

    public BatchView cancel(UUID id, UUID userId) {
        BatchView value = get(id, userId);
        if (List.of("COMPLETED", "CANCELLED").contains(value.status())) return value;
        value.items().stream()
                .filter(i -> "RUNNING".equals(i.status()) && i.workflowRunId() != null)
                .findFirst()
                .ifPresent(i -> {
                    try {
                        workflows.cancel(i.workflowRunId(), userId);
                    } catch (ConflictException ignored) {
                    }
                });
        jdbc.update("UPDATE chapter_batch_item SET status='CANCELLED' WHERE batch_id=? AND status='QUEUED'", id);
        touch(id, "CANCELLED");
        return get(id, userId);
    }

    public GateView decideGate(UUID gateId, UUID userId, boolean approve) {
        GateView gate = gate(gateId, userId);
        if (!"PENDING".equals(gate.status()))
            throw new ConflictException("story_gate_decided", "Story gate was already decided");
        jdbc.update(
                "UPDATE story_gate SET status=?,decided_by=?,decided_at=? WHERE id=?",
                approve ? "APPROVED" : "REJECTED",
                userId,
                clock.instant(),
                gateId);
        if (approve) {
            touch(gate.batchId(), "RUNNING");
            startNext(gate.batchId(), userId);
        } else {
            jdbc.update(
                    "UPDATE chapter_batch_item SET status='CANCELLED' WHERE batch_id=? AND status='QUEUED'",
                    gate.batchId());
            touch(gate.batchId(), "CANCELLED");
        }
        return gate(gateId, userId);
    }

    public List<GateView> gates(UUID batchId, UUID userId) {
        BatchView batch = get(batchId, userId);
        return jdbc.query(
                "SELECT * FROM story_gate WHERE batch_id=? ORDER BY created_at", (rs, row) -> mapGate(rs), batch.id());
    }

    public GateView gate(UUID id, UUID userId) {
        GateView value = jdbc.query("SELECT * FROM story_gate WHERE id=?", rs -> rs.next() ? mapGate(rs) : null, id);
        if (value == null) throw new NotFoundException("story_gate_not_found", "Story gate was not found");
        access.requireOwnedProject(value.projectId(), userId);
        return value;
    }

    @EventListener
    public void approved(WorkflowApprovedEvent event) {
        List<UUID> batchIds = jdbc.query(
                "SELECT batch_id FROM chapter_batch_item WHERE workflow_run_id=?",
                (rs, row) -> rs.getObject(1, UUID.class),
                event.runId());
        for (UUID batchId : batchIds) {
            jdbc.update(
                    "UPDATE chapter_batch_item SET status='COMPLETED' WHERE batch_id=? AND workflow_run_id=?",
                    batchId,
                    event.runId());
            jdbc.update(
                    "UPDATE chapter_batch SET current_index=current_index+1,version=version+1,updated_at=? WHERE id=?",
                    clock.instant(),
                    batchId);
            BatchView batch = get(batchId, event.userId());
            if ("PAUSED".equals(batch.status()) || "CANCELLED".equals(batch.status())) continue;
            if (batch.items().stream().allMatch(i -> "COMPLETED".equals(i.status()))) {
                touch(batchId, "COMPLETED");
            } else startNext(batchId, event.userId());
        }
    }

    private synchronized void startNext(UUID batchId, UUID userId) {
        BatchView batch = get(batchId, userId);
        if (List.of("PAUSED", "CANCELLED", "COMPLETED", "FAILED").contains(batch.status())) return;
        if (batch.items().stream().anyMatch(i -> "RUNNING".equals(i.status()))) return;
        BatchItem item = batch.items().stream()
                .filter(i -> "QUEUED".equals(i.status()))
                .findFirst()
                .orElse(null);
        if (item == null) {
            touch(batchId, "COMPLETED");
            return;
        }
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM story_gate WHERE batch_id=? AND chapter_id=? AND status='PENDING'",
                Integer.class,
                batchId,
                item.chapterId());
        if (pending != null && pending > 0) {
            touch(batchId, "WAITING_GATE");
            return;
        }
        var started = workflows.create(
                batch.projectId(),
                item.chapterId(),
                userId,
                batch.viewpointCharacterId(),
                batch.id() + "-" + item.sequenceNo(),
                batch.instruction());
        jdbc.update(
                "UPDATE chapter_batch_item SET workflow_run_id=?,status='RUNNING' WHERE id=? AND status='QUEUED'",
                started.run().getId(),
                item.id());
        touch(batchId, "RUNNING");
        if (started.created()) orchestrator.submit(started.run().getId());
    }

    private BatchView map(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<BatchItem> items = jdbc.query(
                "SELECT i.*,w.status workflow_status FROM chapter_batch_item i LEFT JOIN workflow_run w ON w.id=i.workflow_run_id WHERE i.batch_id=? ORDER BY i.sequence_no",
                (row, n) -> new BatchItem(
                        row.getObject("id", UUID.class),
                        row.getInt("sequence_no"),
                        row.getObject("chapter_id", UUID.class),
                        row.getObject("workflow_run_id", UUID.class),
                        effectiveStatus(row.getString("status"), row.getString("workflow_status"))),
                id);
        return new BatchView(
                id,
                rs.getObject("project_id", UUID.class),
                rs.getObject("viewpoint_character_id", UUID.class),
                rs.getString("instruction"),
                rs.getString("status"),
                rs.getInt("current_index"),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                items);
    }

    private String effectiveStatus(String item, String workflow) {
        if ("RUNNING".equals(item)
                && List.of("FAILED", "BLOCKED", "ROLLED_BACK", "CANCELLED").contains(workflow)) return "FAILED";
        return item;
    }

    private GateView mapGate(ResultSet rs) throws SQLException {
        return new GateView(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("batch_id", UUID.class),
                rs.getObject("chapter_id", UUID.class),
                rs.getObject("workflow_run_id", UUID.class),
                rs.getString("gate_type"),
                rs.getString("title"),
                rs.getString("rationale"),
                rs.getString("status"),
                rs.getObject("decided_by", UUID.class),
                rs.getTimestamp("decided_at") == null
                        ? null
                        : rs.getTimestamp("decided_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    private void touch(UUID id, String status) {
        jdbc.update(
                "UPDATE chapter_batch SET status=?,version=version+1,updated_at=? WHERE id=?",
                status,
                clock.instant(),
                id);
    }

    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private Object[] args(UUID projectId, List<UUID> ids) {
        Object[] values = new Object[ids.size() + 1];
        values[0] = projectId;
        for (int i = 0; i < ids.size(); i++) values[i + 1] = ids.get(i);
        return values;
    }

    public record BatchItem(UUID id, int sequenceNo, UUID chapterId, UUID workflowRunId, String status) {}

    public record BatchView(
            UUID id,
            UUID projectId,
            UUID viewpointCharacterId,
            String instruction,
            String status,
            int currentIndex,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<BatchItem> items) {}

    public record GateView(
            UUID id,
            UUID projectId,
            UUID batchId,
            UUID chapterId,
            UUID workflowRunId,
            String gateType,
            String title,
            String rationale,
            String status,
            UUID decidedBy,
            Instant decidedAt,
            Instant createdAt) {}
}
