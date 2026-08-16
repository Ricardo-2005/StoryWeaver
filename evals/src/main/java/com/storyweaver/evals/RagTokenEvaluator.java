package com.storyweaver.evals;

import com.storyweaver.character.repository.CharacterRepository;
import com.storyweaver.chapter.repository.ChapterRepository;
import com.storyweaver.llm.adapter.LocalOnnxEmbeddingGateway;
import com.storyweaver.llm.application.EmbeddingGateway;
import com.storyweaver.llm.config.EmbeddingProperties;
import com.storyweaver.llm.config.RetrievalProperties;
import com.storyweaver.project.application.ProjectAccessService;
import com.storyweaver.worldbook.application.TokenEstimator;
import com.storyweaver.worldbook.application.WorldbookService;
import com.storyweaver.worldbook.application.WorldbookService.ActivationReport;
import com.storyweaver.worldbook.domain.WorldbookEntry;
import com.storyweaver.worldbook.domain.WorldbookScope;
import com.storyweaver.worldbook.domain.WorldbookVisibility;
import com.storyweaver.worldbook.repository.WorldbookEntryRepository;
import com.storyweaver.worldbook.repository.WorldbookRepository;
import com.storyweaver.worldbook.repository.WorldbookVectorRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;

final class RagTokenEvaluator {
    private static final UUID PROJECT_ID = stable("eval-project-v1");
    private static final UUID OWNER_ID = stable("eval-owner-v1");
    private static final UUID WORLDBOOK_ID = stable("eval-worldbook-v1");
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    private final Path repoRoot;
    private final String datasetVersion;
    private final RagEvaluationOptions options;
    private final TokenEstimator tokens = new TokenEstimator();

    RagTokenEvaluator(Path repoRoot, String datasetVersion) {
        this(repoRoot, datasetVersion, RagEvaluationOptions.baseline());
    }

    RagTokenEvaluator(Path repoRoot, String datasetVersion, RagEvaluationOptions options) {
        this.repoRoot = repoRoot;
        this.datasetVersion = datasetVersion;
        this.options = options;
    }

    Evaluation evaluate(boolean includeRag, boolean includeToken) throws Exception {
        Path fixturePath = datasetVersion.equals("rag-holdout-v1")
                ? repoRoot.resolve("evals/fixtures/worldbook/eval-holdout-v1.json")
                : repoRoot.resolve("evals/fixtures/worldbook/eval-project-v1.json");
        Path datasetPath = datasetVersion.equals("rag-holdout-v1")
                ? repoRoot.resolve("evals/datasets/rag-holdout-v1/retrieval_cases.jsonl")
                : repoRoot.resolve("evals/datasets/rag/retrieval_cases.jsonl");
        JsonNode fixture = EvalSupport.JSON.readTree(fixturePath.toFile());
        if (!datasetVersion.equals(fixture.path("datasetVersion").asText())) {
            throw new IllegalArgumentException("Worldbook fixture datasetVersion mismatch");
        }
        List<WorldbookEntry> entries = new ArrayList<>();
        Map<UUID, String> logicalIds = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        for (JsonNode item : fixture.path("entries")) {
            String logicalId = item.path("id").asText();
            WorldbookEntry entry = new WorldbookEntry(
                    PROJECT_ID,
                    WORLDBOOK_ID,
                    item.path("title").asText(),
                    item.path("content").asText(),
                    item.path("active").asBoolean(true),
                    item.path("constantEnabled").asBoolean(false),
                    item.path("vectorEnabled").asBoolean(true),
                    strings(item.path("keywords")).toArray(String[]::new),
                    item.path("priority").asInt(50),
                    WorldbookScope.PROJECT,
                    null,
                    WorldbookVisibility.ALL,
                    null,
                    NOW);
            setId(entry, stable(logicalId));
            entries.add(entry);
            logicalIds.put(entry.getId(), logicalId);
            types.put(logicalId, item.path("assetType").asText("WORLDBOOK"));
        }

        EmbeddingProperties embeddingProperties = new EmbeddingProperties(
                true,
                "BAAI/bge-small-zh-v1.5",
                512,
                repoRoot.resolve("backend/models/model.onnx").toUri().toString(),
                repoRoot.resolve("backend/models/tokenizer.json").toUri().toString(),
                repoRoot.resolve("evals/.cache/onnx").toString());
        EmbeddingGateway embeddings = new LocalOnnxEmbeddingGateway(embeddingProperties, new DefaultResourceLoader());
        ExactVectorRepository exactVectors = new ExactVectorRepository(embeddings, entries);
        WorldbookService service = new WorldbookService(
                proxy(WorldbookRepository.class, List.of()),
                proxy(WorldbookEntryRepository.class, entries),
                exactVectors,
                new AllowFixtureProjectAccess(),
                proxy(ChapterRepository.class, List.of()),
                proxy(CharacterRepository.class, List.of()),
                embeddings,
                tokens,
                new RetrievalProperties(
                        4000,
                        10,
                        10,
                        0.5,
                        0.2,
                        0.1,
                        0.1,
                        0.1,
                        options.mode(),
                        options.candidatePoolSize(),
                        options.finalRankingSize(),
                        options.rrfRankConstant()),
                new SimpleMeterRegistry(),
                null,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC));

        List<JsonNode> cases = EvalSupport.readJsonl(datasetPath, datasetVersion);
        List<Map<String, Object>> ragCases = new ArrayList<>();
        List<Map<String, Object>> tokenCases = new ArrayList<>();
        Map<Integer, List<Double>> recalls = new LinkedHashMap<>();
        Map<Integer, Long> requiredHits = new LinkedHashMap<>();
        for (int k : List.of(1, 3, 5, 10)) {
            recalls.put(k, new ArrayList<>());
            requiredHits.put(k, 0L);
        }
        List<Double> reciprocalRanks = new ArrayList<>();
        List<Double> ndcgAt5 = new ArrayList<>();
        List<Double> ndcgAt10 = new ArrayList<>();
        List<Integer> firstRequiredRanks = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        List<Double> reductions = new ArrayList<>();
        List<Double> qualityReductions = new ArrayList<>();
        long preservationHits = 0;
        Map<String, long[]> typeCounts = new LinkedHashMap<>();
        Map<String, Long> failureDistribution = new LinkedHashMap<>();

        int naiveTokens = entries.stream().mapToInt(entry -> tokens.estimate(entry.getTitle(), entry.getContent())).sum();
        for (JsonNode value : cases) {
            long started = System.nanoTime();
            String query = value.path("input").path("query").asText();
            int budget = value.path("input").path("tokenBudget").asInt(320);
            List<String> relevant = strings(value.path("expected").path("relevantIds"));
            List<String> required = strings(value.path("expected").path("requiredIds"));
            try {
                WorldbookService.ActivationPreview preview = service.previewWithOptions(
                        PROJECT_ID, OWNER_ID, query, null, null, budget, options.productionOptions());
                long latency = (System.nanoTime() - started) / 1_000_000;
                latencies.add(latency);
                List<String> retrieved = preview.retrievedEntries().stream()
                        .map(item -> logicalIds.get(item.entryId()))
                        .toList();
                List<String> contextIds = preview.selectedEntries().stream()
                        .map(item -> logicalIds.get(item.entryId()))
                        .toList();
                for (int k : List.of(1, 3, 5, 10)) {
                    recalls.get(k).add(Metrics.recall(retrieved, relevant, k));
                    if (Metrics.requiredHit(retrieved, required, k)) requiredHits.put(k, requiredHits.get(k) + 1);
                }
                reciprocalRanks.add(Metrics.reciprocalRank(retrieved, relevant));
                ndcgAt5.add(Metrics.binaryNdcg(retrieved, relevant, 5));
                ndcgAt10.add(Metrics.binaryNdcg(retrieved, relevant, 10));
                Integer firstRequiredRank = Metrics.firstRelevantRank(retrieved, required);
                if (firstRequiredRank != null) firstRequiredRanks.add(firstRequiredRank);
                for (String id : relevant) {
                    long[] count = typeCounts.computeIfAbsent(types.getOrDefault(id, "UNKNOWN"), ignored -> new long[2]);
                    count[1]++;
                    if (retrieved.stream().limit(10).anyMatch(id::equals)) count[0]++;
                }
                boolean preserved = contextIds.containsAll(required);
                if (preserved) preservationHits++;
                double reduction = Metrics.reduction(naiveTokens, preview.selectedTokens());
                reductions.add(reduction);
                if (preserved) qualityReductions.add(reduction);

                List<String> missed = relevant.stream().filter(id -> !retrieved.contains(id)).toList();
                List<String> incorrect = retrieved.stream().limit(10).filter(id -> !relevant.contains(id)).toList();
                boolean allRequired = Metrics.requiredHit(retrieved, required, retrieved.size());
                boolean passed = missed.isEmpty() && Metrics.requiredHit(retrieved, required, 10);
                RagFailureCategory category = passed
                        ? null
                        : RagFailureClassifier.classify(
                                options.mode(), retrieved, relevant, required, preview.reports(), logicalIds, types, 10);
                if (category != null) failureDistribution.merge(category.name(), 1L, Long::sum);

                Map<String, Object> actual = new LinkedHashMap<>();
                actual.put("retrievedIds", retrieved);
                actual.put("missedRelevantIds", missed);
                actual.put("incorrectTop10Ids", incorrect);
                actual.put("firstRequiredRank", firstRequiredRank);
                actual.put("allRequired", allRequired);
                actual.put("failureCategory", category == null ? null : category.name());
                actual.put("failureReason", failureReason(category, required, retrieved, firstRequiredRank));
                actual.put("embeddingAvailable", preview.embeddingAvailable());
                actual.put("degradedReason", preview.degradedReason());
                actual.put("trace", trace(preview, logicalIds, types));
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("evaluationType", "DETERMINISTIC");
                metadata.put("retrieval", "WorldbookService.previewWithOptions");
                metadata.put("retrievalMode", options.mode().name());
                metadata.put("candidatePoolSize", options.candidatePoolSize());
                metadata.put("finalRankingSize", options.finalRankingSize());
                metadata.put("rrfRankConstant", options.rrfRankConstant());
                metadata.put("vectorSearch", "EXACT_COSINE");
                metadata.put("embeddingModel", "BAAI/bge-small-zh-v1.5");
                ragCases.add(EvalSupport.caseResult(value, passed, actual, latency, null, metadata));

                Map<String, Object> tokenActual = new LinkedHashMap<>();
                tokenActual.put("baselineTokens", naiveTokens);
                tokenActual.put("optimizedTokens", preview.selectedTokens());
                tokenActual.put("tokenReduction", reduction);
                tokenActual.put("requiredIdsPreserved", preserved);
                tokenActual.put("contextIds", contextIds);
                tokenActual.put("retrievalMode", options.mode().name());
                tokenCases.add(EvalSupport.caseResult(
                        value,
                        preserved,
                        tokenActual,
                        latency,
                        null,
                        Map.of("evaluationType", "DETERMINISTIC", "countMethod", "ESTIMATED_TOKEN_COUNT")));
            } catch (RuntimeException exception) {
                long latency = (System.nanoTime() - started) / 1_000_000;
                Map<String, Object> metadata = Map.of(
                        "evaluationType", "DETERMINISTIC", "retrievalMode", options.mode().name());
                ragCases.add(EvalSupport.caseResult(value, false, Map.of(), latency, exception.toString(), metadata));
                tokenCases.add(EvalSupport.caseResult(value, false, Map.of(), latency, exception.toString(), metadata));
                failureDistribution.merge(RagFailureCategory.OTHER.name(), 1L, Long::sum);
            }
        }

        Map<String, Object> ragMetrics = new LinkedHashMap<>();
        for (int k : List.of(1, 3, 5, 10)) ragMetrics.put("recallAt" + k, finite(Metrics.mean(recalls.get(k))));
        ragMetrics.put("requiredHitRateAt5", finite(Metrics.ratio(requiredHits.get(5), cases.size())));
        ragMetrics.put("requiredHitRateAt10", finite(Metrics.ratio(requiredHits.get(10), cases.size())));
        ragMetrics.put("allRequiredHitRateAt5", finite(Metrics.ratio(requiredHits.get(5), cases.size())));
        ragMetrics.put("allRequiredHitRateAt10", finite(Metrics.ratio(requiredHits.get(10), cases.size())));
        ragMetrics.put("mrr", finite(Metrics.mean(reciprocalRanks)));
        ragMetrics.put("binaryNdcgAt5", finite(Metrics.mean(ndcgAt5)));
        ragMetrics.put("binaryNdcgAt10", finite(Metrics.mean(ndcgAt10)));
        ragMetrics.put("meanFirstRequiredRank", finite(Metrics.mean(firstRequiredRanks)));
        ragMetrics.put("medianFirstRequiredRank", finite(Metrics.percentile(firstRequiredRanks, 0.5)));
        ragMetrics.put("p95FirstRequiredRank", finite(Metrics.percentile(firstRequiredRanks, 0.95)));
        ragMetrics.put("firstRequiredRankCoverage", finite(Metrics.ratio(firstRequiredRanks.size(), cases.size())));
        ragMetrics.put("meanRetrievalLatencyMs", finite(Metrics.mean(latencies)));
        ragMetrics.put("p95RetrievalLatencyMs", finite(Metrics.percentile(latencies, 0.95)));
        ragMetrics.put("retrievalMode", options.mode().name());
        ragMetrics.put("candidatePoolSize", options.candidatePoolSize());
        ragMetrics.put("finalRankingSize", options.finalRankingSize());
        ragMetrics.put("rrfRankConstant", options.rrfRankConstant());
        ragMetrics.put("failureDistribution", failureDistribution);
        Map<String, Object> byType = new LinkedHashMap<>();
        typeCounts.forEach((key, count) -> byType.put(key, finite(Metrics.ratio(count[0], count[1]))));
        ragMetrics.put("recallAt10ByAssetType", byType);
        ragMetrics.put("approximateIndexRecall", null);

        Map<String, Object> tokenMetrics = new LinkedHashMap<>();
        tokenMetrics.put("countMethod", "ESTIMATED_TOKEN_COUNT");
        tokenMetrics.put("retrievalMode", options.mode().name());
        tokenMetrics.put("meanTokenReduction", finite(Metrics.mean(reductions)));
        tokenMetrics.put("medianTokenReduction", finite(Metrics.percentile(reductions, 0.5)));
        tokenMetrics.put("p50TokenReduction", finite(Metrics.percentile(reductions, 0.5)));
        tokenMetrics.put("p90TokenReduction", finite(Metrics.percentile(reductions, 0.9)));
        tokenMetrics.put("minTokenReduction", reductions.stream().min(Double::compareTo).orElse(null));
        tokenMetrics.put("maxTokenReduction", reductions.stream().max(Double::compareTo).orElse(null));
        tokenMetrics.put("contextPreservationRate", finite(Metrics.ratio(preservationHits, cases.size())));
        tokenMetrics.put("qualityPreservingTokenReduction", finite(Metrics.mean(qualityReductions)));

        return new Evaluation(
                includeRag ? section("RAG", cases.size(), ragMetrics, ragCases) : null,
                includeToken ? section("TOKEN", cases.size(), tokenMetrics, tokenCases) : null);
    }

    private Map<String, Object> trace(
            WorldbookService.ActivationPreview preview,
            Map<UUID, String> logicalIds,
            Map<String, String> types) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (ActivationReport report : preview.reports()) {
            Map<String, Object> item = new LinkedHashMap<>();
            String logicalId = logicalIds.get(report.entryId());
            item.put("rank", report.retrievalRank());
            item.put("id", logicalId);
            item.put("type", types.getOrDefault(logicalId, "UNKNOWN"));
            item.put("source", String.join("+", report.sources()));
            item.put("sources", report.sources());
            item.put("constant", report.constant());
            item.put("keywordScore", report.keywordScore());
            item.put("vectorScore", report.vectorScore());
            item.put("finalScore", report.finalScore());
            item.put("tokenEstimate", report.estimatedTokens());
            item.put("inFinalRanking", report.inFinalRanking());
            item.put("selected", report.selected());
            item.put("dropReason", report.dropReason());
            candidates.add(item);
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("requestedMode", preview.requestedMode().name());
        trace.put("mode", preview.mode().name());
        trace.put("candidatePoolSize", preview.candidatePoolSize());
        trace.put("finalRankingSize", preview.finalRankingSize());
        trace.put("rawCandidateCount", preview.rawCandidateCount());
        trace.put("deduplicatedCandidateCount", preview.deduplicatedCandidateCount());
        trace.put("candidates", candidates);
        return trace;
    }

    private String failureReason(
            RagFailureCategory category,
            List<String> required,
            List<String> retrieved,
            Integer firstRequiredRank) {
        if (category == null) return null;
        List<String> missingTop10 = required.stream().filter(id -> !retrieved.stream().limit(10).toList().contains(id)).toList();
        return category.name() + ": missingRequiredTop10=" + missingTop10 + ", firstRequiredRank=" + firstRequiredRank;
    }

    private Map<String, Object> section(
            String name, int count, Map<String, Object> metrics, List<Map<String, Object>> results) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("datasetVersion", datasetVersion);
        section.put("evaluationType", "DETERMINISTIC");
        section.put("name", name);
        section.put("caseCount", count);
        section.put("metrics", metrics);
        section.put("failedCaseCount", results.stream().filter(value -> !Boolean.TRUE.equals(value.get("passed"))).count());
        section.put("cases", results);
        return section;
    }

    private static Object finite(double value) {
        return Double.isFinite(value) ? value : null;
    }

    private static List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        node.forEach(item -> result.add(item.asText()));
        return List.copyOf(result);
    }

    private static UUID stable(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void setId(WorldbookEntry entry, UUID id) throws ReflectiveOperationException {
        Field field = WorldbookEntry.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entry, id);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, List<?> values) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (instance, method, args) -> {
            return switch (method.getName()) {
                case "findAllByProjectIdOrderByPriorityDescTitleAsc" -> values;
                case "findById", "findByProjectId" -> Optional.empty();
                case "toString" -> "EvalProxy(" + type.getSimpleName() + ")";
                default -> defaultValue(method.getReturnType());
            };
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    record Evaluation(Map<String, Object> rag, Map<String, Object> token) {}

    private static final class AllowFixtureProjectAccess extends ProjectAccessService {
        private AllowFixtureProjectAccess() {
            super(null);
        }

        @Override
        public OwnedProject requireOwnedProject(UUID projectId, UUID ownerId) {
            if (!PROJECT_ID.equals(projectId) || !OWNER_ID.equals(ownerId)) {
                throw new IllegalArgumentException("Evaluation fixture ownership violation");
            }
            return new OwnedProject(projectId, 0, false);
        }
    }

    static final class ExactVectorRepository extends WorldbookVectorRepository {
        private final Map<UUID, float[]> vectors = new HashMap<>();

        ExactVectorRepository(EmbeddingGateway embeddings, List<WorldbookEntry> entries) {
            super(null);
            for (WorldbookEntry entry : entries) {
                EmbeddingGateway.EmbeddingResult result = embeddings.embed(entry.getTitle() + "\n" + entry.getContent());
                if (result.available()) vectors.put(entry.getId(), result.vector());
            }
        }

        @Override
        public List<VectorMatch> search(UUID projectId, float[] query, Integer chapterNo, int topK) {
            return vectors.entrySet().stream()
                    .map(value -> new VectorMatch(value.getKey(), cosine(query, value.getValue())))
                    .sorted(Comparator.comparingDouble(VectorMatch::similarity)
                            .reversed()
                            .thenComparing(match -> match.entryId().toString()))
                    .limit(topK)
                    .toList();
        }

        private static double cosine(float[] left, float[] right) {
            double dot = 0;
            double leftNorm = 0;
            double rightNorm = 0;
            for (int index = 0; index < Math.min(left.length, right.length); index++) {
                dot += left[index] * right[index];
                leftNorm += left[index] * left[index];
                rightNorm += right[index] * right[index];
            }
            return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
        }
    }
}
