package com.storyweaver.evals;

import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
import com.storyweaver.consistency.application.CanonReferenceValidator;
import com.storyweaver.consistency.application.CharacterStateValidator;
import com.storyweaver.consistency.application.ConsistencyModels.Issue;
import com.storyweaver.consistency.application.ConsistencyModels.TimelineEvent;
import com.storyweaver.consistency.application.ItemOwnershipValidator;
import com.storyweaver.consistency.application.KnowledgeBoundaryValidator;
import com.storyweaver.consistency.application.TimelineValidator;
import com.storyweaver.consistency.domain.CharacterKnowledge;
import com.storyweaver.consistency.domain.ItemOwnership;
import com.storyweaver.consistency.domain.ItemStatus;
import com.storyweaver.consistency.domain.KnowledgeCertainty;
import com.storyweaver.consistency.domain.ReviewSeverity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

final class ConsistencyEvaluator {
    private static final UUID PROJECT_ID = stable("eval-project-v1");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final Path repoRoot;
    private final String datasetVersion;
    private final CharacterStateValidator characterState = new CharacterStateValidator();
    private final ItemOwnershipValidator itemOwnership = new ItemOwnershipValidator();
    private final TimelineValidator timeline = new TimelineValidator();
    private final KnowledgeBoundaryValidator knowledge = new KnowledgeBoundaryValidator();
    private final CanonReferenceValidator canon = new CanonReferenceValidator();

    ConsistencyEvaluator(Path repoRoot, String datasetVersion) {
        this.repoRoot = repoRoot;
        this.datasetVersion = datasetVersion;
    }

    Map<String, Object> evaluate() throws Exception {
        List<JsonNode> cases = EvalSupport.readJsonl(
                repoRoot.resolve("evals/datasets/consistency/consistency_cases.jsonl"), datasetVersion);
        long tp = 0, tn = 0, fp = 0, fn = 0;
        long expectedBlockers = 0, detectedBlockers = 0;
        Map<String, long[]> byCategory = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();

        for (JsonNode value : cases) {
            long started = System.nanoTime();
            String category = value.path("input").path("validatorCategory").asText();
            boolean expectedPass = value.path("expected").path("shouldPass").asBoolean();
            boolean expectedConflict = !expectedPass;
            List<Issue> issues;
            try {
                issues = validate(value, category);
            } catch (RuntimeException exception) {
                long latency = (System.nanoTime() - started) / 1_000_000;
                results.add(EvalSupport.caseResult(
                        value,
                        false,
                        Map.of(),
                        latency,
                        exception.toString(),
                        Map.of("evaluationType", "DETERMINISTIC", "layer", "JAVA_VALIDATOR")));
                continue;
            }
            long latency = (System.nanoTime() - started) / 1_000_000;
            boolean predictedConflict = issues.stream().anyMatch(issue -> issue.severity() == ReviewSeverity.BLOCKER);
            if (expectedConflict && predictedConflict) tp++;
            else if (!expectedConflict && !predictedConflict) tn++;
            else if (!expectedConflict) fp++;
            else fn++;
            long[] categoryCounts = byCategory.computeIfAbsent(category, ignored -> new long[4]);
            if (expectedConflict && predictedConflict) categoryCounts[0]++;
            else if (!expectedConflict && !predictedConflict) categoryCounts[1]++;
            else if (!expectedConflict) categoryCounts[2]++;
            else categoryCounts[3]++;
            if (expectedConflict) {
                expectedBlockers++;
                if (predictedConflict) detectedBlockers++;
            }

            List<Map<String, Object>> actualIssues = issues.stream()
                    .map(issue -> Map.<String, Object>of(
                            "type", issue.category(),
                            "severity", issue.severity().name(),
                            "message", issue.message(),
                            "evidence", issue.evidence() == null ? "" : issue.evidence()))
                    .toList();
            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("shouldPass", !predictedConflict);
            actual.put("violations", actualIssues);
            boolean passed = expectedConflict == predictedConflict
                    && (!expectedConflict || actualIssues.stream()
                            .anyMatch(issue -> category.equals(issue.get("type"))
                                    && "BLOCKER".equals(issue.get("severity"))));
            results.add(EvalSupport.caseResult(
                    value,
                    passed,
                    actual,
                    latency,
                    null,
                    Map.of("evaluationType", "DETERMINISTIC", "layer", "JAVA_VALIDATOR")));
        }

        Metrics.Classification classification = Metrics.classification(tp, tn, fp, fn);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("truePositive", tp);
        metrics.put("trueNegative", tn);
        metrics.put("falsePositive", fp);
        metrics.put("falseNegative", fn);
        metrics.put("consistencyPassRate", classification.accuracy());
        metrics.put("conflictPrecision", classification.precision());
        metrics.put("conflictRecall", classification.recall());
        metrics.put("conflictF1", classification.f1());
        metrics.put("cleanChapterPassRate", Metrics.ratio(tn, tn + fp));
        metrics.put("conflictDetectionRate", Metrics.ratio(tp, tp + fn));
        metrics.put("blockerRecall", Metrics.ratio(detectedBlockers, expectedBlockers));
        Map<String, Object> categoryMetrics = new LinkedHashMap<>();
        byCategory.forEach((category, counts) -> {
            Metrics.Classification item = Metrics.classification(counts[0], counts[1], counts[2], counts[3]);
            categoryMetrics.put(category, Map.of(
                    "accuracy", item.accuracy(),
                    "precision", item.precision(),
                    "recall", item.recall(),
                    "f1", item.f1()));
        });
        metrics.put("byCategory", categoryMetrics);
        metrics.put("llmReviewer", null);
        metrics.put("combinedPipeline", null);

        Map<String, Object> section = new LinkedHashMap<>();
        section.put("datasetVersion", datasetVersion);
        section.put("evaluationType", "DETERMINISTIC");
        section.put("layer", "JAVA_VALIDATOR");
        section.put("caseCount", cases.size());
        section.put("metrics", metrics);
        section.put("failedCaseCount", results.stream().filter(item -> !Boolean.TRUE.equals(item.get("passed"))).count());
        section.put("cases", results);
        return section;
    }

    private List<Issue> validate(JsonNode value, String category) {
        JsonNode input = value.path("input");
        String text = input.path("chapterText").asText("");
        String subject = input.path("subject").asText("测试对象");
        UUID characterId = stable(value.path("caseId").asText());
        return switch (category) {
            case "CHARACTER_STATE" -> {
                CharacterState state = new CharacterState(PROJECT_ID, characterId, NOW);
                state.update(LifeStatus.DEAD, "雾港", null, null, null, null, null, NOW);
                yield characterState.validateDraft(subject, state, text);
            }
            case "ITEM_OWNERSHIP" -> {
                ItemOwnership item = new ItemOwnership(
                        PROJECT_ID,
                        "item-" + value.path("caseId").asText(),
                        subject,
                        characterId,
                        ItemStatus.DESTROYED,
                        stable("source-chapter"),
                        "物品已在上一章销毁",
                        NOW);
                yield itemOwnership.validateDraft(item, text);
            }
            case "TIMELINE" -> timeline.validate(
                    input.path("latestStoryTime").asText("2026-08-10"),
                    new TimelineEvent(
                            List.of(characterId),
                            List.of(characterId),
                            "雾港",
                            input.path("proposedStoryTime").asText(),
                            text,
                            "记录事件",
                            0.8,
                            "段落-" + value.path("caseId").asText()));
            case "KNOWLEDGE_BOUNDARY" -> {
                CharacterKnowledge fact = new CharacterKnowledge(
                        PROJECT_ID,
                        stable("other-character"),
                        "secret-" + value.path("caseId").asText(),
                        subject,
                        KnowledgeCertainty.CONFIRMED,
                        null,
                        stable("source-chapter"),
                        "秘密只在密室中被记录",
                        NOW);
                yield knowledge.validateDraft(fact, false, text);
            }
            case "WORLD_RULE" -> canon.requireEvidence(
                    "WORLD_RULE", input.path("evidence").isMissingNode() ? null : input.path("evidence").asText());
            default -> throw new IllegalArgumentException("Unsupported production validator category: " + category);
        };
    }

    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
