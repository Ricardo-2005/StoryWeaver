package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {
    @TempDir
    Path temp;

    @Test
    @SuppressWarnings("unchecked")
    void skippedMetricsRemainNull() throws Exception {
        ReportWriter writer = new ReportWriter(temp, "v1", "ci", "mcp", 1, Map.of(), Map.of("executed", false));
        Method method = ReportWriter.class.getDeclaredMethod("summaryMetrics", Map.class);
        method.setAccessible(true);
        Map<String, Object> metrics = (Map<String, Object>) method.invoke(writer, new LinkedHashMap<>());
        assertThat(metrics.get("liveWorkflowSuccessRate")).isNull();
        assertThat(metrics.get("ragRecallAt5")).isNull();
        assertThat(metrics).containsKeys("mcpToolSuccessRate", "tokenReduction", "mrr", "binaryNdcgAt10");
    }

    @Test
    void failureArtifactKeepsEvidenceWithoutSecrets() throws Exception {
        String redacted = EvalSupport.redact("failure with Bearer abcdefghijklmnop");
        Path artifact = temp.resolve("failure.txt");
        Files.writeString(artifact, redacted);
        assertThat(Files.readString(artifact)).contains("[REDACTED]").doesNotContain("abcdefghijklmnop");
    }
}
