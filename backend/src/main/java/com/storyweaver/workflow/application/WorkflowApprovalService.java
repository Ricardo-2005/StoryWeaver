package com.storyweaver.workflow.application;

import com.storyweaver.consistency.application.ConsistencyModels.CommitProposal;
import com.storyweaver.consistency.application.ConsistencyReviewService;
import com.storyweaver.consistency.application.ConsistencyValidatorEngine;
import com.storyweaver.shared.error.ApiException;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.workflow.domain.ContextPacket;
import com.storyweaver.workflow.domain.WorkflowRun;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkflowApprovalService {
    private final WorkflowStore store;
    private final ConsistencyValidatorEngine validators;
    private final ConsistencyReviewService reviews;
    private final WorkflowAtomicCommitter committer;
    private final Clock clock;
    private final ObservationRegistry observations;

    public WorkflowApprovalService(
            WorkflowStore store,
            ConsistencyValidatorEngine validators,
            ConsistencyReviewService reviews,
            WorkflowAtomicCommitter committer,
            ObservationRegistry observations,
            Clock clock) {
        this.store = store;
        this.validators = validators;
        this.reviews = reviews;
        this.committer = committer;
        this.observations = observations;
        this.clock = clock;
    }

    public WorkflowRun approve(
            UUID runId, UUID userId, long expectedVersion, String changeSummary, CommitProposal proposal) {
        WorkflowRun run = store.requireOwned(runId, userId);
        ContextPacket packet = store.packet(runId);
        if (packet == null || packet.isStale(clock.instant())) {
            throw new ConflictException("context_packet_stale", "Context Packet must be rebuilt before approval");
        }
        var proposalIssues = validators.validateProposal(run.getProjectId(), proposal);
        if (!proposalIssues.isEmpty()) reviews.appendApprovalIssues(run.getProjectId(), runId, proposalIssues);
        if (reviews.hasBlockers(runId)) {
            throw new ConflictException("workflow_blocked_by_review", "BLOCKER review issues must be resolved");
        }
        try {
            return Observation.createNotStarted("storyweaver.workflow.commit", observations)
                    .lowCardinalityKeyValue("step", "COMMIT")
                    .observe(() -> committer.commit(runId, userId, expectedVersion, changeSummary, proposal));
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String message =
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            store.rolledBack(runId, message.length() <= 500 ? message : message.substring(0, 500));
            throw exception;
        }
    }
}
