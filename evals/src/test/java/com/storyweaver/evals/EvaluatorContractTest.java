package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluatorContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void workflowMcpAndConsistencyScorersExecuteVersionedCases() throws Exception {
        Path root = EvalSupport.repoRoot();
        Map<String, Object> workflow = new WorkflowEvaluator(root, "v1").evaluate();
        Map<String, Object> workflowMetrics = (Map<String, Object>) workflow.get("metrics");
        assertThat(workflow.get("caseCount")).isEqualTo(14);
        assertThat(workflowMetrics.get("workflowEngineSuccessRate")).isEqualTo(1.0);

        Map<String, Object> mcp = new McpEvaluator(root, "v1").evaluate();
        Map<String, Object> mcpMetrics = (Map<String, Object>) mcp.get("metrics");
        assertThat(mcp.get("caseCount")).isEqualTo(18);
        assertThat(mcpMetrics.get("authorizationEnforcementRate")).isEqualTo(1.0);

        Map<String, Object> consistency = new ConsistencyEvaluator(root, "v1").evaluate();
        Map<String, Object> consistencyMetrics = (Map<String, Object>) consistency.get("metrics");
        assertThat(consistency.get("caseCount")).isEqualTo(100);
        assertThat(consistencyMetrics).containsKeys("conflictPrecision", "conflictRecall", "conflictF1");
    }

    @Test
    void windowsEntrypointMapsAllModesAndProtectsLive() throws Exception {
        Path root = EvalSupport.repoRoot();
        String cmd = Files.readString(root.resolve("evals/run-evals.cmd"));
        String powershell = Files.readString(root.resolve("evals/scripts/run-all.ps1"));
        assertThat(cmd).contains("chcp 65001", "cd /d", "STORYWEAVER_EVAL_LIVE", "exit /b 2");
        assertThat(cmd).contains("all", "rag", "token", "consistency", "workflow", "mcp", "live", "help");
        assertThat(powershell).contains("DatasetVersion", "Output", "Repetitions", "exec:java");
    }
}
