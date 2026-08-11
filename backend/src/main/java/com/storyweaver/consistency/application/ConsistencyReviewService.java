package com.storyweaver.consistency.application;

import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.domain.ReviewIssue;
import com.storyweaver.consistency.domain.ReviewSeverity;
import com.storyweaver.consistency.domain.ReviewSource;
import com.storyweaver.consistency.domain.StoryFact;
import com.storyweaver.consistency.repository.ReviewIssueRepository;
import com.storyweaver.consistency.repository.StoryFactRepository;
import com.storyweaver.llm.application.AgentContracts.ReviewResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsistencyReviewService {
    private final StoryFactRepository facts;
    private final ReviewIssueRepository issues;
    private final Clock clock;
    private final MeterRegistry meters;

    public ConsistencyReviewService(
            StoryFactRepository facts, ReviewIssueRepository issues, MeterRegistry meters, Clock clock) {
        this.facts = facts;
        this.issues = issues;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional
    public void replaceCandidates(
            UUID projectId, UUID runId, UUID chapterId, Map<String, Object> extraction, String draft) {
        facts.deleteAllByWorkflowRunId(runId);
        List<String> candidates = strings(extraction.get("candidateFacts"));
        String evidence = firstParagraph(draft);
        List<StoryFact> values = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            String content = candidates.get(index);
            String key = UUID.nameUUIDFromBytes(content.getBytes(StandardCharsets.UTF_8))
                    .toString();
            values.add(
                    new StoryFact(projectId, runId, chapterId, index, key, content, evidence, "p-1", clock.instant()));
        }
        facts.saveAll(values);
    }

    @Transactional
    public void replaceJavaIssues(UUID projectId, UUID runId, List<Issue> values) {
        issues.deleteAllByWorkflowRunId(runId);
        issues.saveAll(values.stream()
                .map(value -> new ReviewIssue(
                        projectId,
                        runId,
                        ReviewSource.JAVA,
                        value.category(),
                        value.severity(),
                        value.message(),
                        safe(value.evidence()),
                        value.historicalEvidence(),
                        value.suggestion(),
                        value.blocking(),
                        clock.instant()))
                .toList());
        values.forEach(value -> meters.counter(
                        "storyweaver.review.issues",
                        "severity",
                        value.severity().name())
                .increment());
    }

    @Transactional
    public void appendReviewerIssues(UUID projectId, UUID runId, ReviewResult result) {
        issues.saveAll(result.issues().stream()
                .map(value -> new ReviewIssue(
                        projectId,
                        runId,
                        ReviewSource.LLM,
                        value.category(),
                        ReviewSeverity.valueOf(value.severity().name()),
                        value.message(),
                        value.evidence(),
                        null,
                        value.suggestion(),
                        value.blocking(),
                        clock.instant()))
                .toList());
        result.issues().forEach(value -> meters.counter(
                        "storyweaver.review.issues",
                        "severity",
                        value.severity().name())
                .increment());
    }

    @Transactional
    public void clearReviewArtifacts(UUID runId) {
        issues.deleteAllByWorkflowRunId(runId);
        facts.deleteAllByWorkflowRunId(runId);
    }

    @Transactional
    public void appendApprovalIssues(UUID projectId, UUID runId, List<Issue> values) {
        issues.saveAll(values.stream()
                .map(value -> new ReviewIssue(
                        projectId,
                        runId,
                        ReviewSource.JAVA,
                        value.category(),
                        value.severity(),
                        value.message(),
                        safe(value.evidence()),
                        value.historicalEvidence(),
                        value.suggestion(),
                        value.blocking(),
                        clock.instant()))
                .toList());
        values.forEach(value -> meters.counter(
                        "storyweaver.review.issues",
                        "severity",
                        value.severity().name())
                .increment());
    }

    @Transactional(readOnly = true)
    public List<StoryFact> candidates(UUID runId) {
        return facts.findAllByWorkflowRunIdOrderByCandidateIndexAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<ReviewIssue> issues(UUID runId) {
        return issues.findAllByWorkflowRunIdOrderByCreatedAtAsc(runId);
    }

    @Transactional(readOnly = true)
    public boolean hasBlockers(UUID runId) {
        return issues.existsByWorkflowRunIdAndBlockingTrueAndResolvedFalse(runId);
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).toList();
    }

    private String firstParagraph(String draft) {
        String value = draft == null ? "" : draft.strip();
        int split = value.indexOf('\n');
        String paragraph = split < 0 ? value : value.substring(0, split);
        return paragraph.length() <= 1000 ? paragraph : paragraph.substring(0, 1000);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
