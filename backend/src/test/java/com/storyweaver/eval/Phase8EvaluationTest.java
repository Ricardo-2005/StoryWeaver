package com.storyweaver.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.character.domain.CharacterState;
import com.storyweaver.character.domain.LifeStatus;
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
import com.storyweaver.worldbook.application.TokenEstimator;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Phase8EvaluationTest {
    private static final Path CONFLICTS = Path.of("eval", "datasets", "conflicts.jsonl");
    private static final Path DEMO_MANIFEST = Path.of("eval", "datasets", "demo-manifest.json");
    private static final Path DRAGON_SCENARIOS = Path.of("eval", "datasets", "dragon-template-scenarios.json");
    private static final Path SNAPSHOT = Path.of("eval", "results", "phase8-results.json");
    private static final Path OUTPUT = Path.of("target", "phase8-results", "phase8-results.json");
    private static final int PERFORMANCE_ITERATIONS = 100;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CharacterStateValidator characterState = new CharacterStateValidator();
    private final ItemOwnershipValidator itemOwnership = new ItemOwnershipValidator();
    private final TimelineValidator timeline = new TimelineValidator();
    private final KnowledgeBoundaryValidator knowledge = new KnowledgeBoundaryValidator();
    private final TokenEstimator tokens = new TokenEstimator();

    @Test
    void evaluatesFixedConflictCorpusContextBaselineAndLocalPerformance() throws Exception {
        List<ConflictCase> cases = readCases();
        DemoManifest manifest = objectMapper.readValue(DEMO_MANIFEST.toFile(), DemoManifest.class);
        List<DragonScenario> dragonScenarios = objectMapper.readValue(
                DRAGON_SCENARIOS.toFile(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, DragonScenario.class));

        assertDatasetShape(cases, manifest, dragonScenarios);
        Evaluation evaluation = evaluate(cases);
        ContextComparison context = compareContext(manifest);
        Performance performance = benchmark(cases, manifest);

        assertThat(evaluation.overall().truePositive()).isEqualTo(120);
        assertThat(evaluation.overall().falseNegative()).isZero();
        assertThat(evaluation.overall().falsePositive()).isZero();
        assertThat(evaluation.overall().trueNegative()).isEqualTo(120);
        assertThat(evaluation.overall().evidenceLocated()).isEqualTo(120);
        assertThat(context.storyWeaverTokens()).isLessThan(context.baselineTokens());
        assertThat(context.tokenSavingsPercent()).isGreaterThan(BigDecimal.ZERO);

        Result snapshot = objectMapper.readValue(SNAPSHOT.toFile(), Result.class);
        assertThat(snapshot.conflictDatasetSha256()).isEqualTo(sha256(CONFLICTS));
        assertThat(snapshot.demoManifestSha256()).isEqualTo(sha256(DEMO_MANIFEST));
        assertThat(snapshot.conflictEvaluation()).isEqualTo(evaluation);
        assertThat(snapshot.contextComparison()).isEqualTo(context);

        Result result = new Result(
                "phase8-eval-v1",
                Instant.now().toString(),
                sha256(CONFLICTS),
                sha256(DEMO_MANIFEST),
                new Environment(System.getProperty("java.version"), System.getProperty("os.name")),
                evaluation,
                context,
                performance,
                "Fixed deterministic Java-rule benchmark; not an open-domain or LLM generalization score.");
        Files.createDirectories(OUTPUT.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(), result);
    }

    private List<ConflictCase> readCases() throws IOException {
        try (Stream<String> lines = Files.lines(CONFLICTS, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).map(this::readCase).toList();
        }
    }

    private ConflictCase readCase(String json) {
        try {
            return objectMapper.readValue(json, ConflictCase.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid conflict fixture", exception);
        }
    }

    private void assertDatasetShape(
            List<ConflictCase> cases, DemoManifest manifest, List<DragonScenario> dragonScenarios) {
        assertThat(cases).hasSize(120);
        assertThat(cases.stream().map(ConflictCase::id)).doesNotHaveDuplicates();
        assertThat(count(cases, "CHARACTER_STATE")).isEqualTo(40);
        assertThat(count(cases, "ITEM_OWNERSHIP")).isEqualTo(20);
        assertThat(count(cases, "TIMELINE")).isEqualTo(30);
        assertThat(count(cases, "KNOWLEDGE_BOUNDARY")).isEqualTo(30);
        assertThat(manifest.datasetVersion()).isEqualTo("2.0.0-dragon-template");
        assertThat(manifest.templateMarker()).isEqualTo("storyweaver-dragon-template-v1");
        assertThat(manifest.characterNames()).containsExactly("路明非", "楚子航", "诺诺", "恺撒", "昂热", "芬格尔");
        assertThat(manifest.chapterTitles()).hasSize(20);
        assertThat(manifest.worldbookEntries())
                .extracting(WorldbookFixture::title)
                .contains("卡塞尔学院", "秘党", "青铜城", "混血种血统规则", "言灵规则", "龙文", "炼金术", "七宗罪", "学生会", "狮心会");
        assertThat(manifest.worldbookEntryCount()).isEqualTo(60);
        assertThat(manifest.storyEventCount()).isEqualTo(150);
        assertThat(manifest.workflowRegressionChapterCount()).isEqualTo(10);
        assertThat(dragonScenarios)
                .extracting(DragonScenario::id)
                .containsExactly(
                        "DRAGON-CHARACTER-LOCATION",
                        "DRAGON-UNIQUE-ITEM",
                        "DRAGON-TIMELINE-CAUSE",
                        "DRAGON-KNOWLEDGE-BOUNDARY",
                        "DRAGON-WORLD-RULE");
        assertThat(dragonScenarios).allSatisfy(value -> {
            assertThat(value.conflictInput()).isNotBlank();
            assertThat(value.safeInput()).isNotBlank();
            assertThat(value.evidenceMarker()).isNotBlank();
        });
    }

    private long count(List<ConflictCase> cases, String category) {
        return cases.stream().filter(value -> value.category().equals(category)).count();
    }

    private Evaluation evaluate(List<ConflictCase> cases) {
        Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        accumulators.put("CHARACTER_STATE", new Accumulator());
        accumulators.put("ITEM_OWNERSHIP", new Accumulator());
        accumulators.put("TIMELINE", new Accumulator());
        accumulators.put("KNOWLEDGE_BOUNDARY", new Accumulator());
        Accumulator overall = new Accumulator();
        for (ConflictCase value : cases) {
            List<Issue> conflictIssues = validate(value, value.conflictInput());
            List<Issue> safeIssues = validate(value, value.safeInput());
            boolean conflictDetected = blocking(conflictIssues, value.category());
            boolean safeDetected = blocking(safeIssues, value.category());
            boolean evidenceLocated = conflictIssues.stream()
                    .filter(issue -> issue.severity() == ReviewSeverity.BLOCKER)
                    .anyMatch(issue -> contains(issue.evidence(), value.evidenceMarker())
                            || contains(issue.historicalEvidence(), value.evidenceMarker()));
            accumulators.get(value.category()).accept(conflictDetected, safeDetected, evidenceLocated);
            overall.accept(conflictDetected, safeDetected, evidenceLocated);
        }
        Map<String, ClassificationMetrics> byCategory = new LinkedHashMap<>();
        accumulators.forEach((key, value) -> byCategory.put(key, value.metrics()));
        return new Evaluation(cases.size(), cases.size() * 2, overall.metrics(), byCategory);
    }

    private List<Issue> validate(ConflictCase value, String input) {
        UUID projectId = UUID.nameUUIDFromBytes("phase8-project".getBytes(StandardCharsets.UTF_8));
        UUID characterId = UUID.nameUUIDFromBytes(value.id().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return switch (value.category()) {
            case "CHARACTER_STATE" -> {
                CharacterState state = new CharacterState(projectId, characterId, now);
                state.update(LifeStatus.DEAD, "卡塞尔学院", null, null, null, null, null, now);
                yield characterState.validateDraft(value.subject(), state, input);
            }
            case "ITEM_OWNERSHIP" -> {
                ItemOwnership item = new ItemOwnership(
                        projectId,
                        value.id(),
                        value.subject(),
                        characterId,
                        ItemStatus.DESTROYED,
                        UUID.nameUUIDFromBytes("chapter-13".getBytes(StandardCharsets.UTF_8)),
                        "第十三章爆炸证据",
                        now);
                yield itemOwnership.validateDraft(item, input);
            }
            case "TIMELINE" ->
                timeline.validate(
                        value.latestStoryTime(),
                        new TimelineEvent(
                                List.of(characterId),
                                List.of(characterId),
                                "青铜城",
                                input,
                                "发现水下入口",
                                "进入青铜城",
                                0.8,
                                "段落-" + value.id()));
            case "KNOWLEDGE_BOUNDARY" -> {
                CharacterKnowledge fact = new CharacterKnowledge(
                        projectId,
                        UUID.nameUUIDFromBytes((value.id() + "-other").getBytes(StandardCharsets.UTF_8)),
                        value.id(),
                        value.subject(),
                        KnowledgeCertainty.CONFIRMED,
                        null,
                        UUID.nameUUIDFromBytes("chapter-15".getBytes(StandardCharsets.UTF_8)),
                        "第十五章私下记录",
                        now);
                yield knowledge.validateDraft(fact, false, input);
            }
            default -> throw new IllegalArgumentException("Unsupported category: " + value.category());
        };
    }

    private boolean blocking(List<Issue> issues, String category) {
        return issues.stream()
                .anyMatch(issue -> issue.severity() == ReviewSeverity.BLOCKER
                        && issue.category().equals(category));
    }

    private boolean contains(String value, String marker) {
        return value != null && marker != null && value.contains(marker);
    }

    private ContextComparison compareContext(DemoManifest manifest) {
        String system = repeat("系统规则：保持正典、人物认知与时间线连续。", 500);
        String instruction = repeat("本章执行青铜城调查任务并核对人物知识、炼金武器与时间线。", 200);
        List<String> bodies = manifest.chapterTitles().stream()
                .map(title -> repeat(title + "：原创青铜城技术演示记录与人物行动证据。", manifest.chapterBodyCodePoints()))
                .toList();
        String baseline = system + String.join("\n", bodies.subList(0, 15)) + instruction;
        String selectedWorldbook =
                repeat("动态世界书：青铜城水下结构、龙文机关、炼金武器限制、混血种血统规则和角色可见信息。", manifest.selectedWorldbookCount() * 180);
        String selectedEvents = repeat("相关事件：发现青铜城入口、解析龙文机关和完整七宗罪剑匣交接证据。", manifest.selectedEventCount() * 120);
        String storyWeaver = system
                + repeat("上一章摘要：第十五个测试章完成角色知识边界核对，但龙王身份仍未确认。", 240)
                + selectedWorldbook
                + selectedEvents
                + repeat("正典状态：人物、物品、时间线和知识边界。", 700)
                + repeat("Skill：现代校园幻想、有限视角、证据优先，不模仿原著文风。", 350)
                + instruction;
        int baselineTokens = tokens.estimate("Baseline A", baseline);
        int storyWeaverTokens = tokens.estimate("StoryWeaver B", storyWeaver);
        BigDecimal savings = percent(baselineTokens - storyWeaverTokens, baselineTokens);
        return new ContextComparison(
                15,
                manifest.selectedWorldbookCount(),
                manifest.selectedEventCount(),
                baselineTokens,
                storyWeaverTokens,
                savings);
    }

    private Performance benchmark(List<ConflictCase> cases, DemoManifest manifest) {
        for (int i = 0; i < 10; i++) cases.forEach(value -> validate(value, value.conflictInput()));
        long[] validationSamples = new long[cases.size() * PERFORMANCE_ITERATIONS];
        int sample = 0;
        for (int iteration = 0; iteration < PERFORMANCE_ITERATIONS; iteration++) {
            for (ConflictCase value : cases) {
                long started = System.nanoTime();
                validate(value, value.conflictInput());
                validationSamples[sample++] = System.nanoTime() - started;
            }
        }
        long[] contextSamples = new long[1_000];
        for (int i = 0; i < contextSamples.length; i++) {
            long started = System.nanoTime();
            compareContext(manifest);
            contextSamples[i] = System.nanoTime() - started;
        }
        return new Performance(
                validationSamples.length,
                stats(validationSamples),
                contextSamples.length,
                stats(contextSamples),
                "Single-JVM local microbenchmark after warm-up; no concurrency or network I/O.");
    }

    private LatencyStats stats(long[] nanos) {
        Arrays.sort(nanos);
        return new LatencyStats(micros(percentile(nanos, 0.50)), micros(percentile(nanos, 0.95)));
    }

    private long percentile(long[] sorted, double percentile) {
        int index = Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * percentile) - 1);
        return sorted[index];
    }

    private BigDecimal micros(long nanos) {
        return BigDecimal.valueOf(nanos).divide(BigDecimal.valueOf(1_000), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String repeat(String value, int minimumCodePoints) {
        StringBuilder result = new StringBuilder(minimumCodePoints + value.length());
        while (result.codePointCount(0, result.length()) < minimumCodePoints) result.append(value);
        return result.toString();
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private record ConflictCase(
            String id,
            String category,
            String subject,
            String conflictInput,
            String safeInput,
            String latestStoryTime,
            String evidenceMarker) {}

    private record DemoManifest(
            String datasetVersion,
            String templateMarker,
            String projectName,
            String genre,
            String description,
            String authorIntent,
            String currentFocus,
            List<String> characterNames,
            List<String> chapterTitles,
            List<WorldbookFixture> worldbookEntries,
            int worldbookEntryCount,
            int storyEventCount,
            int seededChapterCount,
            int workflowRegressionChapterCount,
            int chapterBodyCodePoints,
            int selectedWorldbookCount,
            int selectedEventCount,
            UniqueItem uniqueItem) {}

    private record WorldbookFixture(
            String title, String content, List<String> keywords, String activationMode, String visibilityCharacter) {}

    private record DragonScenario(
            String id, String category, String conflictInput, String safeInput, String evidenceMarker) {}

    private record UniqueItem(String itemKey, String itemName, String expectedOwner) {}

    private record Environment(String javaVersion, String operatingSystem) {}

    private record Result(
            String schemaVersion,
            String generatedAt,
            String conflictDatasetSha256,
            String demoManifestSha256,
            Environment environment,
            Evaluation conflictEvaluation,
            ContextComparison contextComparison,
            Performance performance,
            String limitations) {}

    private record Evaluation(
            int conflictGroups,
            int predictions,
            ClassificationMetrics overall,
            Map<String, ClassificationMetrics> byCategory) {}

    private record ClassificationMetrics(
            int truePositive,
            int falsePositive,
            int trueNegative,
            int falseNegative,
            int evidenceLocated,
            BigDecimal precisionPercent,
            BigDecimal recallPercent,
            BigDecimal f1Percent,
            BigDecimal blockerMissRatePercent,
            BigDecimal evidenceLocationAccuracyPercent) {}

    private record ContextComparison(
            int baselineHistoryChapters,
            int selectedWorldbookEntries,
            int selectedEvents,
            int baselineTokens,
            int storyWeaverTokens,
            BigDecimal tokenSavingsPercent) {}

    private record Performance(
            int validatorSamples,
            LatencyStats validatorLatencyMicros,
            int contextSamples,
            LatencyStats contextAssemblyLatencyMicros,
            String scope) {}

    private record LatencyStats(BigDecimal p50, BigDecimal p95) {}

    private static final class Accumulator {
        private int truePositive;
        private int falsePositive;
        private int trueNegative;
        private int falseNegative;
        private int evidenceLocated;

        void accept(boolean conflictDetected, boolean safeDetected, boolean evidenceFound) {
            if (conflictDetected) truePositive++;
            else falseNegative++;
            if (safeDetected) falsePositive++;
            else trueNegative++;
            if (evidenceFound) evidenceLocated++;
        }

        ClassificationMetrics metrics() {
            BigDecimal precision = percent(truePositive, truePositive + falsePositive);
            BigDecimal recall = percent(truePositive, truePositive + falseNegative);
            BigDecimal f1 = precision.add(recall).signum() == 0
                    ? BigDecimal.ZERO
                    : precision
                            .multiply(recall)
                            .multiply(BigDecimal.valueOf(2))
                            .divide(precision.add(recall), 2, RoundingMode.HALF_UP);
            return new ClassificationMetrics(
                    truePositive,
                    falsePositive,
                    trueNegative,
                    falseNegative,
                    evidenceLocated,
                    precision,
                    recall,
                    f1,
                    percent(falseNegative, truePositive + falseNegative),
                    percent(evidenceLocated, truePositive + falseNegative));
        }

        private BigDecimal percent(long numerator, long denominator) {
            if (denominator == 0) return BigDecimal.ZERO;
            return BigDecimal.valueOf(numerator)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
        }
    }
}
