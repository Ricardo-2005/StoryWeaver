package com.storyweaver.evals;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EvalRunner {
    private EvalRunner() {}

    public static void main(String[] args) throws Exception {
        String mode = property("eval.mode", "all").toLowerCase(java.util.Locale.ROOT);
        String profile = property("eval.profile", "local");
        String datasetVersion = property("eval.datasetVersion", "v1");
        String output = property("eval.output", "");
        int repetitions = Integer.parseInt(property("eval.repetitions", "1"));
        RetrievalExperimentMode ragMode = RetrievalExperimentMode.valueOf(
                property("eval.ragStrategy", "VECTOR_ONLY").toUpperCase(java.util.Locale.ROOT));
        int candidatePoolSize = Integer.parseInt(property(
                "eval.ragCandidatePool", ragMode == RetrievalExperimentMode.BASELINE ? "10" : "30"));
        int finalRankingSize = Integer.parseInt(property(
                "eval.ragFinalK", ragMode == RetrievalExperimentMode.BASELINE ? String.valueOf(Integer.MAX_VALUE) : "10"));
        int rrfRankConstant = Integer.parseInt(property("eval.ragRrfK", "60"));
        RagEvaluationOptions ragOptions =
                new RagEvaluationOptions(ragMode, candidatePoolSize, finalRankingSize, rrfRankConstant);
        if (!java.util.Set.of("all", "rag", "token", "rag-token", "consistency", "workflow", "mcp", "live")
                .contains(mode)) {
            throw new IllegalArgumentException("Unsupported evaluation mode: " + mode);
        }
        if (repetitions < 1) throw new IllegalArgumentException("repetitions must be at least 1");

        Path repoRoot = EvalSupport.repoRoot();
        Map<String, Object> sections = new LinkedHashMap<>();
        if (!mode.equals("live")) {
            if (mode.equals("all") || mode.equals("rag") || mode.equals("token") || mode.equals("rag-token")) {
                RagTokenEvaluator.Evaluation ragToken = new RagTokenEvaluator(repoRoot, datasetVersion, ragOptions)
                        .evaluate(
                                mode.equals("all") || mode.equals("rag") || mode.equals("rag-token"),
                                mode.equals("all") || mode.equals("token") || mode.equals("rag-token"));
                if (ragToken.rag() != null) sections.put("rag", ragToken.rag());
                if (ragToken.token() != null) sections.put("token", ragToken.token());
            }
            if (mode.equals("all") || mode.equals("consistency")) {
                sections.put("consistency", new ConsistencyEvaluator(repoRoot, datasetVersion).evaluate());
            }
            if (mode.equals("all") || mode.equals("workflow")) {
                sections.put("workflow", new WorkflowEvaluator(repoRoot, datasetVersion).evaluate());
            }
            if (mode.equals("all") || mode.equals("mcp")) {
                sections.put("mcp", new McpEvaluator(repoRoot, datasetVersion).evaluate());
            }
        }

        Map<String, Object> live = new LinkedHashMap<>();
        live.put("requested", mode.equals("live"));
        live.put("gateEnabled", "true".equalsIgnoreCase(System.getenv("STORYWEAVER_EVAL_LIVE")));
        live.put("apiKeyPresent", present(System.getenv("DEEPSEEK_API_KEY")));
        live.put("executed", false);
        live.put("estimatedCases", 0);
        live.put("estimatedCalls", 0);
        live.put(
                "reason",
                mode.equals("live")
                        ? (present(System.getenv("DEEPSEEK_API_KEY"))
                                ? "No versioned LIVE_MODEL dataset is defined; metrics remain null."
                                : "DEEPSEEK_API_KEY is absent; metrics remain null.")
                        : "Offline deterministic baseline; live model was not requested.");

        ReportWriter writer = new ReportWriter(repoRoot, datasetVersion, profile, mode, repetitions, sections, live);
        Path report = writer.write(output);
        System.out.println("StoryWeaver Agent Evaluation completed: " + report);
    }

    private static String property(String name, String defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
