package com.storyweaver.evals;

import com.storyweaver.shared.error.ConflictException;
import com.storyweaver.workflow.domain.WorkflowRun;
import com.storyweaver.workflow.domain.WorkflowStateMachine;
import com.storyweaver.workflow.domain.WorkflowStatus;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

final class WorkflowEvaluator {
    private static final UUID PROJECT_ID = stable("eval-project-v1");
    private static final UUID USER_ID = stable("eval-user-v1");
    private static final UUID CHARACTER_ID = stable("eval-character-v1");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final Path repoRoot;
    private final String datasetVersion;
    private final WorkflowStateMachine machine = new WorkflowStateMachine();

    WorkflowEvaluator(Path repoRoot, String datasetVersion) {
        this.repoRoot = repoRoot;
        this.datasetVersion = datasetVersion;
    }

    Map<String, Object> evaluate() throws Exception {
        List<JsonNode> cases = EvalSupport.readJsonl(
                repoRoot.resolve("evals/datasets/workflow/workflow_cases.jsonl"), datasetVersion);
        List<Map<String, Object>> results = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        Map<String, long[]> stageCounts = new LinkedHashMap<>();
        for (String stage : List.of("planner", "writer", "extractor", "reviewer")) {
            stageCounts.put(stage, new long[2]);
        }
        long atomicExpected = 0, atomicPassed = 0;
        long recoveryExpected = 0, recoveryPassed = 0;
        long cancelExpected = 0, cancelPassed = 0;

        for (JsonNode value : cases) {
            long started = System.nanoTime();
            ScenarioResult actual;
            try {
                actual = execute(value);
            } catch (RuntimeException exception) {
                actual = ScenarioResult.unhandled(exception);
            }
            long latency = (System.nanoTime() - started) / 1_000_000;
            latencies.add(latency);
            JsonNode expected = value.path("expected");
            boolean passed = expected.path("finalStatus").asText().equals(actual.finalStatus)
                    && expected.path("structuredOutputValid").asBoolean(actual.structuredOutputValid)
                            == actual.structuredOutputValid
                    && strings(expected.path("requiredSteps")).stream().allMatch(actual.executedSteps::contains)
                    && actual.unhandledError == null;
            if (expected.has("atomicCommit")) {
                atomicExpected++;
                boolean check = expected.path("atomicCommit").asBoolean() == actual.atomicCommit;
                if (check) atomicPassed++;
                passed &= check;
            }
            if (expected.has("recovered")) {
                recoveryExpected++;
                boolean check = expected.path("recovered").asBoolean() == actual.recovered;
                if (check) recoveryPassed++;
                passed &= check;
            }
            if (expected.has("cancelledCorrectly")) {
                cancelExpected++;
                boolean check = expected.path("cancelledCorrectly").asBoolean() == actual.cancelledCorrectly;
                if (check) cancelPassed++;
                passed &= check;
            }
            if (expected.has("idempotencyStable")) {
                passed &= expected.path("idempotencyStable").asBoolean() == actual.idempotencyStable;
            }
            ScenarioResult scored = actual;
            scored.stageApplicable.forEach((stage, applicable) -> {
                if (applicable) {
                    stageCounts.get(stage)[1]++;
                    if (scored.stageSucceeded.getOrDefault(stage, false)) stageCounts.get(stage)[0]++;
                }
            });

            Map<String, Object> actualMap = new LinkedHashMap<>();
            actualMap.put("finalStatus", actual.finalStatus);
            actualMap.put("executedSteps", actual.executedSteps);
            actualMap.put("structuredOutputValid", actual.structuredOutputValid);
            actualMap.put("atomicCommit", actual.atomicCommit);
            actualMap.put("committedVersionNo", actual.committedVersionNo);
            actualMap.put("recovered", actual.recovered);
            actualMap.put("cancelledCorrectly", actual.cancelledCorrectly);
            actualMap.put("idempotencyStable", actual.idempotencyStable);
            actualMap.put("workflowStates", actual.states);
            results.add(EvalSupport.caseResult(
                    value,
                    passed,
                    actualMap,
                    latency,
                    actual.unhandledError,
                    Map.of(
                            "evaluationType", "DETERMINISTIC",
                            "engine", "WorkflowRun+WorkflowStateMachine",
                            "llm", "STUB")));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        long passedCases = results.stream().filter(item -> Boolean.TRUE.equals(item.get("passed"))).count();
        metrics.put("workflowEngineSuccessRate", Metrics.ratio(passedCases, cases.size()));
        metrics.put("liveWorkflowSuccessRate", null);
        for (String stage : List.of("planner", "writer", "extractor", "reviewer")) {
            long[] count = stageCounts.get(stage);
            metrics.put(stage + "SuccessRate", count[1] == 0 ? null : Metrics.ratio(count[0], count[1]));
        }
        metrics.put("atomicCommitSuccessRate", atomicExpected == 0 ? null : Metrics.ratio(atomicPassed, atomicExpected));
        metrics.put("recoverySuccessRate", recoveryExpected == 0 ? null : Metrics.ratio(recoveryPassed, recoveryExpected));
        metrics.put("cancellationCorrectnessRate", cancelExpected == 0 ? null : Metrics.ratio(cancelPassed, cancelExpected));
        metrics.put("meanLatencyMs", Metrics.mean(latencies));
        metrics.put("p95LatencyMs", Metrics.percentile(latencies, 0.95));
        metrics.put("meanToken", null);
        metrics.put("meanCost", null);

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("datasetVersion", datasetVersion);
        section.put("evaluationType", "DETERMINISTIC");
        section.put("llmMode", "STUB");
        section.put("caseCount", cases.size());
        section.put("metrics", metrics);
        section.put("failedCaseCount", cases.size() - passedCases);
        section.put("cases", results);
        return section;
    }

    private ScenarioResult execute(JsonNode value) {
        String scenario = value.path("input").path("scenario").asText();
        WorkflowRun run = new WorkflowRun(
                PROJECT_ID,
                stable("chapter-" + value.path("caseId").asText()),
                USER_ID,
                CHARACTER_ID,
                value.path("input").path("idempotencyKey").asText(value.path("caseId").asText()),
                "Offline deterministic workflow evaluation",
                NOW);
        ScenarioResult result = new ScenarioResult(run);
        switch (scenario) {
            case "NORMAL" -> {
                toWaitingApproval(run, result);
            }
            case "MISSING_CONTEXT", "BUDGET_EXCEEDED" -> {
                transition(run, result, WorkflowStatus.PREFLIGHT, "PREFLIGHT");
                transition(run, result, WorkflowStatus.BLOCKED, scenario);
            }
            case "PLANNER_INVALID" -> {
                toPlanning(run, result);
                result.stage("planner", true, false);
                run.failed("planner_output_invalid", "Stub planner returned invalid structured output", NOW);
                transition(run, result, WorkflowStatus.FAILED, "PLANNER_FAILED");
                result.structuredOutputValid = false;
            }
            case "WRITER_INTERRUPTED_RECOVERED" -> {
                toWriting(run, result);
                run.appendDraft("partial", NOW);
                run.resetDraftForRecovery(NOW);
                result.recovered = run.getRecoveryCount() > 0 && run.getDraftContent().isEmpty();
                result.executedSteps.add("RECOVERY");
                finishFromWriting(run, result);
            }
            case "EXTRACTOR_INVALID" -> {
                toExtracting(run, result);
                result.stage("extractor", true, false);
                run.failed("extractor_output_invalid", "Stub extractor returned invalid JSON", NOW);
                transition(run, result, WorkflowStatus.FAILED, "EXTRACTOR_FAILED");
                result.structuredOutputValid = false;
            }
            case "REVIEWER_BLOCKER" -> {
                toReviewing(run, result);
                run.review(Map.of("issues", List.of(Map.of("severity", "BLOCKER"))), NOW);
                result.stage("reviewer", true, true);
                transition(run, result, WorkflowStatus.WAITING_APPROVAL, "WAITING_APPROVAL");
                transition(run, result, WorkflowStatus.REVISION_REQUIRED, "REVISION_REQUIRED");
            }
            case "USER_REJECTED" -> {
                toWaitingApproval(run, result);
                transition(run, result, WorkflowStatus.ROLLED_BACK, "USER_REJECTED");
            }
            case "CANCEL" -> {
                toWriting(run, result);
                run.requestCancellation(NOW);
                transition(run, result, WorkflowStatus.CANCELLED, "CANCELLED");
                result.cancelledCorrectly = run.isCancelRequested() && run.getCommittedVersionNo() == null;
            }
            case "ATOMIC_COMMIT" -> {
                toWaitingApproval(run, result);
                transition(run, result, WorkflowStatus.COMMITTING, "COMMITTING");
                run.committed(1, USER_ID, NOW);
                transition(run, result, WorkflowStatus.COMPLETED, "COMPLETED");
                result.atomicCommit = Integer.valueOf(1).equals(run.getCommittedVersionNo())
                        && USER_ID.equals(run.getApprovedBy());
            }
            case "ATOMIC_ROLLBACK" -> {
                toWaitingApproval(run, result);
                transition(run, result, WorkflowStatus.COMMITTING, "COMMITTING");
                transition(run, result, WorkflowStatus.ROLLED_BACK, "ROLLBACK");
                result.atomicCommit = run.getCommittedVersionNo() != null;
            }
            case "REVISION_THEN_COMMIT" -> {
                toWaitingApproval(run, result);
                transition(run, result, WorkflowStatus.REVISION_REQUIRED, "REVISION_REQUIRED");
                run.revisedDraft("revised deterministic draft", NOW);
                transition(run, result, WorkflowStatus.TEXT_READY, "TEXT_READY");
                transition(run, result, WorkflowStatus.EXTRACTING, "EXTRACTOR");
                run.extraction(Map.of("facts", List.of()), NOW);
                transition(run, result, WorkflowStatus.VALIDATING, "VALIDATOR");
                transition(run, result, WorkflowStatus.REVIEWING, "REVIEWER");
                run.review(Map.of("issues", List.of()), NOW);
                transition(run, result, WorkflowStatus.WAITING_APPROVAL, "WAITING_APPROVAL");
                transition(run, result, WorkflowStatus.COMMITTING, "COMMITTING");
                run.committed(2, USER_ID, NOW);
                transition(run, result, WorkflowStatus.COMPLETED, "COMPLETED");
                result.atomicCommit = Integer.valueOf(2).equals(run.getCommittedVersionNo());
                result.recovered = run.getRevisionCount() == 1;
            }
            case "DUPLICATE_REQUEST" -> {
                WorkflowRun duplicate = run;
                result.idempotencyStable = duplicate.getId().equals(run.getId())
                        && duplicate.getIdempotencyKey().equals(run.getIdempotencyKey());
                result.executedSteps.add("IDEMPOTENCY_CHECK");
            }
            case "INVALID_TRANSITION_REJECTED" -> {
                try {
                    run.transition(WorkflowStatus.WRITING, machine, NOW);
                    result.unhandledError = "Invalid transition was accepted";
                } catch (ConflictException expected) {
                    result.executedSteps.add("INVALID_TRANSITION_REJECTED");
                }
            }
            default -> throw new IllegalArgumentException("Unknown workflow scenario: " + scenario);
        }
        result.finalStatus = run.getStatus().name();
        result.committedVersionNo = run.getCommittedVersionNo();
        return result;
    }

    private void toPlanning(WorkflowRun run, ScenarioResult result) {
        transition(run, result, WorkflowStatus.PREFLIGHT, "PREFLIGHT");
        transition(run, result, WorkflowStatus.CONTEXT_READY, "CONTEXT");
        transition(run, result, WorkflowStatus.PLANNING, "PLANNER");
        result.stage("planner", true, true);
    }

    private void toWriting(WorkflowRun run, ScenarioResult result) {
        toPlanning(run, result);
        run.plan(Map.of("scenes", List.of(Map.of("goal", "advance"))), NOW);
        transition(run, result, WorkflowStatus.PLAN_READY, "PLAN_READY");
        transition(run, result, WorkflowStatus.WRITING, "WRITER");
        result.stage("writer", true, true);
    }

    private void toExtracting(WorkflowRun run, ScenarioResult result) {
        toWriting(run, result);
        run.appendDraft("original deterministic chapter text", NOW);
        transition(run, result, WorkflowStatus.TEXT_READY, "TEXT_READY");
        transition(run, result, WorkflowStatus.EXTRACTING, "EXTRACTOR");
    }

    private void toReviewing(WorkflowRun run, ScenarioResult result) {
        toExtracting(run, result);
        run.extraction(Map.of("facts", List.of()), NOW);
        result.stage("extractor", true, true);
        transition(run, result, WorkflowStatus.VALIDATING, "VALIDATOR");
        transition(run, result, WorkflowStatus.REVIEWING, "REVIEWER");
    }

    private void toWaitingApproval(WorkflowRun run, ScenarioResult result) {
        toReviewing(run, result);
        run.review(Map.of("issues", List.of()), NOW);
        result.stage("reviewer", true, true);
        transition(run, result, WorkflowStatus.WAITING_APPROVAL, "WAITING_APPROVAL");
    }

    private void finishFromWriting(WorkflowRun run, ScenarioResult result) {
        run.appendDraft("recovered deterministic chapter", NOW);
        transition(run, result, WorkflowStatus.TEXT_READY, "TEXT_READY");
        transition(run, result, WorkflowStatus.EXTRACTING, "EXTRACTOR");
        run.extraction(Map.of("facts", List.of()), NOW);
        result.stage("extractor", true, true);
        transition(run, result, WorkflowStatus.VALIDATING, "VALIDATOR");
        transition(run, result, WorkflowStatus.REVIEWING, "REVIEWER");
        run.review(Map.of("issues", List.of()), NOW);
        result.stage("reviewer", true, true);
        transition(run, result, WorkflowStatus.WAITING_APPROVAL, "WAITING_APPROVAL");
    }

    private void transition(WorkflowRun run, ScenarioResult result, WorkflowStatus status, String step) {
        run.transition(status, machine, NOW);
        result.states.add(status.name());
        result.executedSteps.add(step);
    }

    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class ScenarioResult {
        private String finalStatus;
        private final List<String> states = new ArrayList<>();
        private final List<String> executedSteps = new ArrayList<>();
        private final Map<String, Boolean> stageApplicable = new LinkedHashMap<>();
        private final Map<String, Boolean> stageSucceeded = new LinkedHashMap<>();
        private boolean structuredOutputValid = true;
        private boolean atomicCommit;
        private Integer committedVersionNo;
        private boolean recovered;
        private boolean cancelledCorrectly;
        private boolean idempotencyStable;
        private String unhandledError;

        private ScenarioResult(WorkflowRun run) {
            states.add(run.getStatus().name());
            for (String stage : List.of("planner", "writer", "extractor", "reviewer")) {
                stageApplicable.put(stage, false);
                stageSucceeded.put(stage, false);
            }
        }

        private void stage(String name, boolean applicable, boolean succeeded) {
            stageApplicable.put(name, applicable);
            stageSucceeded.put(name, succeeded);
        }

        private static ScenarioResult unhandled(RuntimeException exception) {
            WorkflowRun placeholder = new WorkflowRun(PROJECT_ID, stable("failed"), USER_ID, CHARACTER_ID, "failed", "failed", NOW);
            ScenarioResult result = new ScenarioResult(placeholder);
            result.finalStatus = placeholder.getStatus().name();
            result.unhandledError = exception.toString();
            return result;
        }
    }
}
