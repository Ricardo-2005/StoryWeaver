package com.storyweaver.evals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReportWriter {
    private static final List<String> METRIC_KEYS = List.of(
            "ragRecallAt1",
            "ragRecallAt3",
            "ragRecallAt5",
            "ragRecallAt10",
            "requiredHitRateAt5",
            "requiredHitRateAt10",
            "allRequiredHitRateAt5",
            "allRequiredHitRateAt10",
            "mrr",
            "binaryNdcgAt5",
            "binaryNdcgAt10",
            "meanFirstRequiredRank",
            "medianFirstRequiredRank",
            "p95FirstRequiredRank",
            "tokenReduction",
            "contextPreservationRate",
            "qualityPreservingTokenReduction",
            "consistencyPassRate",
            "conflictPrecision",
            "conflictRecall",
            "conflictF1",
            "blockerRecall",
            "workflowEngineSuccessRate",
            "liveWorkflowSuccessRate",
            "atomicCommitSuccessRate",
            "recoverySuccessRate",
            "mcpToolSuccessRate",
            "authorizationEnforcementRate",
            "outputSchemaPassRate");

    private final Path repoRoot;
    private final String datasetVersion;
    private final String profile;
    private final String mode;
    private final int repetitions;
    private final Map<String, Object> sections;
    private final Map<String, Object> live;

    ReportWriter(
            Path repoRoot,
            String datasetVersion,
            String profile,
            String mode,
            int repetitions,
            Map<String, Object> sections,
            Map<String, Object> live) {
        this.repoRoot = repoRoot;
        this.datasetVersion = datasetVersion;
        this.profile = profile;
        this.mode = mode;
        this.repetitions = repetitions;
        this.sections = sections;
        this.live = live;
    }

    Path write(String configuredOutput) throws Exception {
        String timestamp = EvalSupport.now();
        String directoryName = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(timestamp));
        Path reportsRoot = repoRoot.resolve("evals/reports");
        Path runDirectory = configuredOutput == null || configuredOutput.isBlank()
                ? reportsRoot.resolve(directoryName)
                : Path.of(configuredOutput).toAbsolutePath().normalize();
        Files.createDirectories(runDirectory.resolve("raw"));

        Map<String, Object> metrics = summaryMetrics(sections);
        String gitCommit = gitCommit();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("datasetVersion", datasetVersion);
        summary.put("gitCommit", gitCommit);
        summary.put("timestamp", timestamp);
        summary.put("profile", profile);
        summary.put("mode", mode);
        summary.put("repetitions", repetitions);
        summary.put("environment", EvalSupport.environment());
        summary.put("live", live);
        summary.put("modelVersion", Boolean.TRUE.equals(live.get("executed")) ? "deepseek-configured" : null);
        summary.put("ragConfiguration", ragConfiguration());
        summary.put("metrics", metrics);
        summary.put("caseCounts", caseCounts());
        summary.put("failedCaseCount", failedCaseCount());

        for (Map.Entry<String, Object> entry : sections.entrySet()) {
            EvalSupport.writeJson(runDirectory.resolve(entry.getKey() + ".json"), entry.getValue());
        }
        EvalSupport.writeJson(runDirectory.resolve("raw/failures.json"), failureArtifacts());
        EvalSupport.writeJson(runDirectory.resolve("summary.json"), summary);
        Files.writeString(runDirectory.resolve("summary.md"), summaryMarkdown(summary), StandardCharsets.UTF_8);

        Path latest = reportsRoot.resolve("latest");
        replaceDirectory(latest, runDirectory);
        Files.writeString(
                repoRoot.resolve("docs/evaluation-results.md"),
                resumeMetrics(summary),
                StandardCharsets.UTF_8);
        return runDirectory.resolve("summary.md");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summaryMetrics(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        METRIC_KEYS.forEach(key -> result.put(key, null));
        Map<String, Object> rag = sectionMetrics(values.get("rag"));
        result.put("ragRecallAt1", rag.get("recallAt1"));
        result.put("ragRecallAt3", rag.get("recallAt3"));
        result.put("ragRecallAt5", rag.get("recallAt5"));
        result.put("ragRecallAt10", rag.get("recallAt10"));
        result.put("requiredHitRateAt5", rag.get("requiredHitRateAt5"));
        result.put("requiredHitRateAt10", rag.get("requiredHitRateAt10"));
        for (String key : List.of(
                "allRequiredHitRateAt5",
                "allRequiredHitRateAt10",
                "mrr",
                "binaryNdcgAt5",
                "binaryNdcgAt10",
                "meanFirstRequiredRank",
                "medianFirstRequiredRank",
                "p95FirstRequiredRank")) {
            result.put(key, rag.get(key));
        }
        Map<String, Object> token = sectionMetrics(values.get("token"));
        result.put("tokenReduction", token.get("meanTokenReduction"));
        result.put("contextPreservationRate", token.get("contextPreservationRate"));
        result.put("qualityPreservingTokenReduction", token.get("qualityPreservingTokenReduction"));
        Map<String, Object> consistency = sectionMetrics(values.get("consistency"));
        for (String key : List.of("consistencyPassRate", "conflictPrecision", "conflictRecall", "conflictF1", "blockerRecall")) {
            result.put(key, consistency.get(key));
        }
        Map<String, Object> workflow = sectionMetrics(values.get("workflow"));
        for (String key : List.of(
                "workflowEngineSuccessRate",
                "liveWorkflowSuccessRate",
                "atomicCommitSuccessRate",
                "recoverySuccessRate")) {
            result.put(key, workflow.get(key));
        }
        Map<String, Object> mcp = sectionMetrics(values.get("mcp"));
        for (String key : List.of("mcpToolSuccessRate", "authorizationEnforcementRate", "outputSchemaPassRate")) {
            result.put(key, mcp.get(key));
        }
        return result;
    }

    private Map<String, Object> ragConfiguration() {
        Map<String, Object> rag = sectionMetrics(sections.get("rag"));
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("retrievalMode", "candidatePoolSize", "finalRankingSize", "rrfRankConstant")) {
            result.put(key, rag.get(key));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sectionMetrics(Object section) {
        if (!(section instanceof Map<?, ?> map)) return Map.of();
        Object metrics = map.get("metrics");
        return metrics instanceof Map<?, ?> values ? (Map<String, Object>) values : Map.of();
    }

    private Map<String, Object> caseCounts() {
        Map<String, Object> result = new LinkedHashMap<>();
        sections.forEach((key, value) -> {
            if (value instanceof Map<?, ?> section) result.put(key, section.get("caseCount"));
        });
        return result;
    }

    private long failedCaseCount() {
        return sections.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .mapToLong(section -> ((Number) section.getOrDefault("failedCaseCount", 0)).longValue())
                .sum();
    }

    @SuppressWarnings("unchecked")
    private List<Object> failureArtifacts() {
        List<Object> failures = new ArrayList<>();
        sections.forEach((sectionName, sectionValue) -> {
            if (!(sectionValue instanceof Map<?, ?> section)) return;
            Object cases = section.get("cases");
            if (!(cases instanceof List<?> values)) return;
            for (Object value : values) {
                if (value instanceof Map<?, ?> item && !Boolean.TRUE.equals(item.get("passed"))) {
                    Map<String, Object> artifact = new LinkedHashMap<>();
                    artifact.put("section", sectionName);
                    artifact.putAll((Map<String, Object>) item);
                    failures.add(artifact);
                }
            }
        });
        return failures;
    }

    @SuppressWarnings("unchecked")
    private String summaryMarkdown(Map<String, Object> summary) {
        Map<String, Object> metrics = (Map<String, Object>) summary.get("metrics");
        StringBuilder text = new StringBuilder("# StoryWeaver Agent Evaluation Report\n\n");
        text.append("- Dataset: `").append(datasetVersion).append("`\n");
        text.append("- Commit: `").append(summary.get("gitCommit") == null ? "null" : summary.get("gitCommit")).append("`\n");
        text.append("- Date: `").append(summary.get("timestamp")).append("`\n");
        text.append("- Profile: `").append(profile).append("`\n");
        text.append("- Mode: `").append(mode).append("`\n");
        text.append("- RAG Configuration: `").append(summary.get("ragConfiguration")).append("`\n");
        text.append("- Live model executed: `").append(live.get("executed")).append("`\n\n");
        text.append("## Executive Summary\n\n");
        text.append("This report is generated from versioned offline datasets and production Java capability adapters. ")
                .append("The retrieval configuration is recorded in `summary.json`; failures remain in `raw/failures.json`.\n\n");
        appendMetrics(text, "RAG", metrics, List.of(
                "ragRecallAt1", "ragRecallAt3", "ragRecallAt5", "ragRecallAt10",
                "requiredHitRateAt5", "requiredHitRateAt10", "allRequiredHitRateAt5", "allRequiredHitRateAt10"));
        appendValues(text, "RAG Ranking Quality", metrics, List.of(
                "mrr", "binaryNdcgAt5", "binaryNdcgAt10",
                "meanFirstRequiredRank", "medianFirstRequiredRank", "p95FirstRequiredRank"));
        appendMetrics(text, "Token", metrics, List.of("tokenReduction", "contextPreservationRate", "qualityPreservingTokenReduction"));
        appendMetrics(text, "Consistency", metrics, List.of("consistencyPassRate", "conflictPrecision", "conflictRecall", "conflictF1", "blockerRecall"));
        appendMetrics(text, "Workflow", metrics, List.of("workflowEngineSuccessRate", "liveWorkflowSuccessRate", "atomicCommitSuccessRate", "recoverySuccessRate"));
        appendMetrics(text, "MCP", metrics, List.of("mcpToolSuccessRate", "authorizationEnforcementRate", "outputSchemaPassRate"));
        text.append("## Failed Cases\n\n");
        text.append("- Total: ").append(summary.get("failedCaseCount")).append("\n");
        sections.forEach((key, value) -> {
            if (value instanceof Map<?, ?> section) {
                text.append("- ").append(key).append(": ").append(section.get("failedCaseCount")).append("\n");
            }
        });
        text.append("\n## Limitations\n\n");
        text.append("- RAG uses the production `WorldbookService` and repository ONNX model, with an in-memory exact-cosine adapter instead of PostgreSQL pgvector ANN.\n");
        text.append("- Token counts use the production `TokenEstimator` (`ESTIMATED_TOKEN_COUNT`), not provider billing counts.\n");
        text.append("- Consistency measures deterministic Java validators; LLM Reviewer and Combined metrics are `null`.\n");
        text.append("- Workflow measures the production state machine with deterministic LLM stubs; live workflow, provider token and cost metrics are `null`.\n");
        text.append("- Atomic commit cases measure domain state/version invariants in the Stub harness; database transaction rollback remains covered by Docker-backed backend integration tests.\n");
        text.append("- MCP invokes discovered production capability methods against an isolated deterministic service fixture; it does not start the HTTP transport.\n");
        text.append("- RAG relevant IDs and expected outcomes are versioned human-authored fixture ground truth.\n");
        return text.toString();
    }

    private void appendMetrics(StringBuilder text, String title, Map<String, Object> metrics, List<String> keys) {
        text.append("## ").append(title).append("\n\n| Metric | Result |\n| --- | ---: |\n");
        for (String key : keys) text.append("| ").append(key).append(" | ").append(EvalSupport.percentage(metrics.get(key))).append(" |\n");
        text.append("\n");
    }

    private void appendValues(StringBuilder text, String title, Map<String, Object> metrics, List<String> keys) {
        text.append("## ").append(title).append("\n\n| Metric | Result |\n| --- | ---: |\n");
        for (String key : keys) text.append("| ").append(key).append(" | ").append(metrics.get(key)).append(" |\n");
        text.append("\n");
    }

    @SuppressWarnings("unchecked")
    private String resumeMetrics(Map<String, Object> summary) {
        Map<String, Object> metrics = (Map<String, Object>) summary.get("metrics");
        StringBuilder text = new StringBuilder("# StoryWeaver Agent Evaluation Results\n\n");
        text.append("> 此文件由 `evals` Harness 从真实 `summary.json` 自动生成，请勿手写指标。\n\n");
        text.append("- Dataset Version: `").append(datasetVersion).append("`\n");
        text.append("- Git Commit: `").append(summary.get("gitCommit") == null ? "null" : summary.get("gitCommit")).append("`\n");
        text.append("- Timestamp: `").append(summary.get("timestamp")).append("`\n");
        text.append("- Profile: `").append(profile).append("`\n");
        text.append("- RAG Configuration: `").append(summary.get("ragConfiguration")).append("`\n");
        text.append("- Environment: `").append(EvalSupport.environment()).append("`\n");
        text.append("- Case Counts: `").append(summary.get("caseCounts")).append("`\n\n");
        text.append("## Metrics\n\n| Metric | Result |\n| --- | ---: |\n");
        for (String key : List.of(
                "ragRecallAt5",
                "ragRecallAt10",
                "allRequiredHitRateAt10",
                "mrr",
                "binaryNdcgAt10",
                "tokenReduction",
                "contextPreservationRate",
                "consistencyPassRate",
                "conflictF1",
                "workflowEngineSuccessRate",
                "liveWorkflowSuccessRate",
                "mcpToolSuccessRate")) {
            Object value = metrics.get(key);
            boolean raw = key.equals("mrr") || key.startsWith("binaryNdcg");
            text.append("| ").append(key).append(" | ")
                    .append(raw ? value : EvalSupport.percentage(value)).append(" |\n");
        }
        text.append("\n## Method\n\nDeterministic production adapters, versioned fixtures, offline ONNX retrieval, Java validators, workflow stubs, and MCP contract invocation. Retrieval mode and candidate/final ranking parameters are captured in the summary.\n\n");
        text.append("## Limitations\n\nLive model metrics remain `null` unless a separately versioned LIVE_MODEL suite is actually executed. Exact in-memory vector search is not a pgvector ANN performance test.\n\n");
        text.append("## Result Snapshot\n\n");
        text.append("On StoryWeaver Eval ").append(datasetVersion).append(", measured RAG Recall@5 ")
                .append(EvalSupport.percentage(metrics.get("ragRecallAt5")))
                .append(", All-Required Hit@10 ")
                .append(EvalSupport.percentage(metrics.get("allRequiredHitRateAt10")))
                .append(", MRR ")
                .append(metrics.get("mrr"))
                .append(", Token Reduction ")
                .append(EvalSupport.percentage(metrics.get("tokenReduction")))
                .append(", Consistency F1 ")
                .append(EvalSupport.percentage(metrics.get("conflictF1")))
                .append(", Workflow Stub Success ")
                .append(EvalSupport.percentage(metrics.get("workflowEngineSuccessRate")))
                .append(", and MCP Tool Success ")
                .append(EvalSupport.percentage(metrics.get("mcpToolSuccessRate")))
                .append(".\n");
        return text.toString();
    }

    private String gitCommit() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(repoRoot.toFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return process.waitFor() == 0 && output.matches("[0-9a-fA-F]{40}") ? output : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void replaceDirectory(Path target, Path source) throws IOException {
        if (Files.exists(target)) {
            try (var paths = Files.walk(target)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Files.createDirectories(target);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) Files.createDirectories(destination);
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
