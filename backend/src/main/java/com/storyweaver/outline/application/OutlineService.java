package com.storyweaver.outline.application;

import com.storyweaver.outline.domain.OutlineNode;
import com.storyweaver.outline.domain.OutlineNodeType;
import com.storyweaver.outline.repository.OutlineNodeRepository;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutlineService {
    private final OutlineNodeRepository outlines;
    private final ProjectAccessService projectAccess;
    private final Clock clock;

    public OutlineService(OutlineNodeRepository outlines, ProjectAccessService projectAccess, Clock clock) {
        this.outlines = outlines;
        this.projectAccess = projectAccess;
        this.clock = clock;
    }

    @Transactional
    public OutlineNode create(
            UUID projectId,
            UUID ownerId,
            UUID parentId,
            OutlineNodeType type,
            String title,
            String summary,
            String objective,
            int sequenceNo) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        validateParent(projectId, parentId, type);
        return outlines.save(new OutlineNode(
                projectId,
                parentId,
                type,
                title.trim(),
                nullable(summary),
                nullable(objective),
                sequenceNo,
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<OutlineNode> list(UUID projectId, UUID ownerId) {
        projectAccess.requireOwnedProject(projectId, ownerId);
        return outlines.findAllByProjectIdOrderBySequenceNoAsc(projectId);
    }

    @Transactional(readOnly = true)
    public OutlineNode get(UUID outlineId, UUID ownerId) {
        return requireOwned(outlineId, ownerId);
    }

    @Transactional
    public OutlineNode update(
            UUID outlineId,
            UUID ownerId,
            long expectedVersion,
            String title,
            String summary,
            String objective,
            int sequenceNo) {
        OutlineNode outline = requireOwned(outlineId, ownerId);
        requireVersion(outline.getVersion(), expectedVersion);
        outline.update(title.trim(), nullable(summary), nullable(objective), sequenceNo, clock.instant());
        outlines.flush();
        return outline;
    }

    private void validateParent(UUID projectId, UUID parentId, OutlineNodeType type) {
        if (type == OutlineNodeType.MASTER && parentId != null) {
            throw new ConflictException("invalid_outline_parent", "A master outline cannot have a parent");
        }
        if (type != OutlineNodeType.MASTER && parentId == null) {
            throw new ConflictException("invalid_outline_parent", "A non-master outline requires a parent");
        }
        if (parentId != null) {
            OutlineNode parent = outlines.findById(parentId)
                    .orElseThrow(() -> new NotFoundException("outline_not_found", "Parent outline was not found"));
            if (!parent.getProjectId().equals(projectId)) {
                throw new NotFoundException("outline_not_found", "Parent outline was not found");
            }
        }
    }

    private OutlineNode requireOwned(UUID outlineId, UUID ownerId) {
        OutlineNode outline = outlines.findById(outlineId)
                .orElseThrow(() -> new NotFoundException("outline_not_found", "Outline was not found"));
        projectAccess.requireOwnedProject(outline.getProjectId(), ownerId);
        return outline;
    }

    private void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw new ConflictException("optimistic_lock_conflict", "The outline changed; reload it before retrying");
        }
    }

    private String nullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
