package com.storyweaver.workflow.application;

import com.storyweaver.chapter.domain.Chapter;
import com.storyweaver.chapter.domain.ChapterVersion;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.chapter.repository.ChapterVersionRepository;
import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.repository.CharacterStateRepository;
import com.storyweaver.consistency.application.ConsistencyModels.CommitProposal;
import com.storyweaver.consistency.domain.CharacterKnowledge;
import com.storyweaver.consistency.domain.ItemOwnership;
import com.storyweaver.consistency.domain.StoryFact;
import com.storyweaver.consistency.repository.CharacterKnowledgeRepository;
import com.storyweaver.consistency.repository.ItemOwnershipRepository;
import com.storyweaver.consistency.repository.ReviewIssueRepository;
import com.storyweaver.consistency.repository.StoryFactRepository;
import com.storyweaver.memory.domain.StoryEvent;
import com.storyweaver.memory.domain.StoryEvent.EventValues;
import com.storyweaver.memory.repository.StoryEventRepository;
import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.shared.error.NotFoundException;
import com.storyweaver.workflow.domain.WorkflowEvent;
import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.workflow.domain.WorkflowStateMachine;
import com.storyweaver.workflow.domain.WorkflowStatus;
import com.storyweaver.workflow.repository.WorkflowEventRepository;
import com.storyweaver.workflow.repository.WorkflowRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowAtomicCommitter {
    private final WorkflowRunRepository runs;
    private final WorkflowEventRepository workflowEvents;
    private final ChapterRepository chapters;
    private final ChapterVersionRepository versions;
    private final CharacterStateRepository states;
    private final StoryFactRepository facts;
    private final ItemOwnershipRepository items;
    private final CharacterKnowledgeRepository knowledge;
    private final StoryEventRepository events;
    private final ReviewIssueRepository issues;
    private final WorkflowStateMachine stateMachine;
    private final AtomicCommitFaultInjector faultInjector;
    private final Clock clock;

    public WorkflowAtomicCommitter(
            WorkflowRunRepository runs,
            WorkflowEventRepository workflowEvents,
            ChapterRepository chapters,
            ChapterVersionRepository versions,
            CharacterStateRepository states,
            StoryFactRepository facts,
            ItemOwnershipRepository items,
            CharacterKnowledgeRepository knowledge,
            StoryEventRepository events,
            ReviewIssueRepository issues,
            WorkflowStateMachine stateMachine,
            AtomicCommitFaultInjector faultInjector,
            Clock clock) {
        this.runs = runs;
        this.workflowEvents = workflowEvents;
        this.chapters = chapters;
        this.versions = versions;
        this.states = states;
        this.facts = facts;
        this.items = items;
        this.knowledge = knowledge;
        this.events = events;
        this.issues = issues;
        this.stateMachine = stateMachine;
        this.faultInjector = faultInjector;
        this.clock = clock;
    }

    @Transactional
    public WorkflowRun commit(
            UUID runId, UUID userId, long expectedVersion, String changeSummary, CommitProposal proposal) {
        WorkflowRun run = runs.findWithLockById(runId)
                .orElseThrow(() -> new NotFoundException("workflow_not_found", "Workflow was not found"));
        if (run.getUserId().equals(userId) && run.getVersion() != expectedVersion) {
            throw new ConflictException("workflow_version_conflict", "Workflow changed before approval");
        }
        if (!run.getUserId().equals(userId)) {
            throw new NotFoundException("workflow_not_found", "Workflow was not found");
        }
        if (run.getStatus() != WorkflowStatus.WAITING_APPROVAL) {
            throw new ConflictException("workflow_not_approvable", "Workflow is not waiting for approval");
        }
        if (issues.existsByWorkflowRunIdAndBlockingTrueAndResolvedFalse(runId)) {
            throw new ConflictException("workflow_blocked_by_review", "BLOCKER review issues must be resolved");
        }

        Instant now = clock.instant();
        run.transition(WorkflowStatus.COMMITTING, stateMachine, now);
        workflowEvent(run, "workflow.step", WorkflowStatus.COMMITTING);
        Chapter chapter = chapters.findById(run.getChapterId())
                .orElseThrow(() -> new IllegalStateException("Workflow chapter is missing"));
        String title = chapterTitle(run, chapter);
        int versionNo = chapter.commitVersion(title, now);
        versions.save(new ChapterVersion(
                run.getProjectId(),
                chapter.getId(),
                versionNo,
                title,
                run.getDraftContent(),
                summary(run),
                normalize(changeSummary),
                null,
                userId,
                now));

        decideFacts(run, proposal.acceptedFactIndexes(), userId, now);
        applyCharacterStates(proposal, now);
        applyItems(run, proposal, now);
        applyTimeline(run, chapter, proposal, now);
        applyKnowledge(run, chapter, proposal, now);

        faultInjector.beforeFinalize(run);
        run.committed(versionNo, userId, now);
        run.transition(WorkflowStatus.COMPLETED, stateMachine, now);
        workflowEvent(run, "workflow.completed", WorkflowStatus.COMPLETED);
        chapters.flush();
        runs.flush();
        return run;
    }

    private void decideFacts(WorkflowRun run, List<Integer> acceptedIndexes, UUID userId, Instant now) {
        Set<Integer> accepted = new HashSet<>(acceptedIndexes);
        List<StoryFact> candidates = facts.findAllByWorkflowRunIdOrderByCandidateIndexAsc(run.getId());
        Set<Integer> available =
                candidates.stream().map(StoryFact::getCandidateIndex).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(accepted)) {
            throw new ConflictException("story_fact_not_found", "Accepted fact index is not part of this workflow");
        }
        candidates.forEach(fact -> fact.decide(accepted.contains(fact.getCandidateIndex()), userId, now));
    }

    private void applyCharacterStates(CommitProposal proposal, Instant now) {
        proposal.characterStateChanges().forEach(change -> {
            CharacterState state = states.findByCharacterId(change.characterId())
                    .orElseThrow(
                            () -> new ConflictException("character_state_not_found", "Character state is missing"));
            if (state.getVersion() != change.expectedVersion()) {
                throw new ConflictException(
                        "character_state_version_conflict", "Character state changed before commit");
            }
            state.update(
                    change.lifeStatus(),
                    normalize(change.currentLocation()),
                    normalize(change.physicalCondition()),
                    normalize(change.emotionalState()),
                    normalize(change.abilities()),
                    normalize(change.inventoryNotes()),
                    normalize(change.notes()),
                    now);
        });
    }

    private void applyItems(WorkflowRun run, CommitProposal proposal, Instant now) {
        proposal.itemChanges().forEach(change -> {
            ItemOwnership item = items.findByProjectIdAndItemKey(run.getProjectId(), change.itemKey())
                    .orElseGet(() -> new ItemOwnership(
                            run.getProjectId(),
                            change.itemKey(),
                            change.itemName(),
                            change.toOwnerCharacterId(),
                            change.status(),
                            run.getChapterId(),
                            change.evidence(),
                            now));
            item.update(
                    change.itemName(),
                    change.toOwnerCharacterId(),
                    change.status(),
                    run.getChapterId(),
                    change.evidence(),
                    now);
            items.save(item);
        });
    }

    private void applyTimeline(WorkflowRun run, Chapter chapter, CommitProposal proposal, Instant now) {
        proposal.timelineEvents()
                .forEach(value -> events.save(new StoryEvent(
                        run.getProjectId(),
                        new EventValues(
                                chapter.getId(),
                                chapter.getChapterNo(),
                                value.participantIds().toArray(UUID[]::new),
                                value.knownByIds().toArray(UUID[]::new),
                                normalize(value.location()),
                                normalize(value.storyTime()),
                                value.action(),
                                value.result(),
                                value.importance(),
                                value.evidence()),
                        now)));
    }

    private void applyKnowledge(WorkflowRun run, Chapter chapter, CommitProposal proposal, Instant now) {
        proposal.knowledgeChanges().forEach(change -> {
            CharacterKnowledge value = knowledge
                    .findByProjectIdAndCharacterIdAndFactKey(run.getProjectId(), change.characterId(), change.factKey())
                    .orElseGet(() -> new CharacterKnowledge(
                            run.getProjectId(),
                            change.characterId(),
                            change.factKey(),
                            change.content(),
                            change.certainty(),
                            change.sourceEventId(),
                            chapter.getId(),
                            change.evidence(),
                            now));
            value.update(
                    change.content(),
                    change.certainty(),
                    change.sourceEventId(),
                    chapter.getId(),
                    change.evidence(),
                    now);
            knowledge.save(value);
        });
    }

    private String chapterTitle(WorkflowRun run, Chapter chapter) {
        Map<String, Object> plan = run.getPlan();
        if (plan == null || plan.get("chapterTitle") == null) return chapter.getTitle();
        String value = String.valueOf(plan.get("chapterTitle")).strip();
        return value.isEmpty() ? chapter.getTitle() : value.substring(0, Math.min(160, value.length()));
    }

    private String summary(WorkflowRun run) {
        Map<String, Object> extraction = run.getExtraction();
        return extraction == null || extraction.get("summary") == null
                ? null
                : String.valueOf(extraction.get("summary"));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void workflowEvent(WorkflowRun run, String eventType, WorkflowStatus status) {
        workflowEvents.save(new WorkflowEvent(
                run.getProjectId(),
                run.getId(),
                eventType,
                status.name(),
                Map.of("status", status.name()),
                clock.instant()));
    }
}
