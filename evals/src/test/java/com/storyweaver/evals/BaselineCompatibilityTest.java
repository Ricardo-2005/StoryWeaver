package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BaselineCompatibilityTest {
    @Test
    @SuppressWarnings("unchecked")
    void enhancedTracePreservesFrozenV1BaselineMetrics() throws Exception {
        RagTokenEvaluator.Evaluation result = new RagTokenEvaluator(
                        EvalSupport.repoRoot(), "v1", RagEvaluationOptions.baseline())
                .evaluate(true, true);
        Map<String, Object> rag = (Map<String, Object>) result.rag().get("metrics");
        Map<String, Object> token = (Map<String, Object>) result.token().get("metrics");

        assertThat(rag.get("recallAt1")).isEqualTo(0.01);
        assertThat(rag.get("recallAt3")).isEqualTo(0.05);
        assertThat(rag.get("recallAt5")).isEqualTo(0.22);
        assertThat(rag.get("recallAt10")).isEqualTo(0.765);
        assertThat(rag.get("requiredHitRateAt5")).isEqualTo(0.18);
        assertThat(rag.get("requiredHitRateAt10")).isEqualTo(0.76);
        assertThat(token.get("meanTokenReduction")).isEqualTo(0.7842175732217573);
        assertThat(token.get("contextPreservationRate")).isEqualTo(1.0);
    }
}
