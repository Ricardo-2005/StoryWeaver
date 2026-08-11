package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import com.storyweaver.worldbook.application.WorldbookService.ActivationReport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagFailureClassifierTest {
    @Test
    void classifiesConstantCrowdingFromObservedTraceInsteadOfCaseId() {
        UUID rule1 = UUID.randomUUID();
        UUID rule2 = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        List<ActivationReport> reports = List.of(
                report(rule1, true, 1), report(rule2, true, 2), report(target, false, 11));
        Map<UUID, String> ids = Map.of(rule1, "rule-1", rule2, "rule-2", target, "target");

        RagFailureCategory category = RagFailureClassifier.classify(
                RetrievalExperimentMode.BASELINE,
                List.of("rule-1", "rule-2"),
                List.of("target"),
                List.of("target"),
                reports,
                ids,
                Map.of("target", "FACT"),
                10);

        assertThat(category).isEqualTo(RagFailureCategory.CONSTANT_RULE_CROWDING);
    }

    private ActivationReport report(UUID id, boolean constant, Integer rank) {
        return new ActivationReport(
                id,
                id.toString(),
                List.of(constant ? "CONSTANT" : "VECTOR:0.5"),
                constant ? 100 : 50,
                constant,
                0,
                0.5,
                0.5,
                List.of(constant ? "CONSTANT" : "VECTOR"),
                rank,
                10,
                true,
                true,
                null);
    }
}
