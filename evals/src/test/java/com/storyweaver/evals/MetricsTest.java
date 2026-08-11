package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsTest {
    @Test
    void calculatesRecallRequiredHitAndReduction() {
        assertThat(Metrics.recall(List.of("a", "x", "b"), List.of("a", "b"), 1)).isEqualTo(0.5);
        assertThat(Metrics.recall(List.of("a", "x", "b"), List.of("a", "b"), 3)).isEqualTo(1.0);
        assertThat(Metrics.requiredHit(List.of("a", "x", "b"), List.of("a", "b"), 3)).isTrue();
        assertThat(Metrics.requiredHit(List.of("a", "x", "b"), List.of("a", "b"), 2)).isFalse();
        assertThat(Metrics.reduction(100, 25)).isEqualTo(0.75);
    }

    @Test
    void calculatesConfusionMatrixAndPercentiles() {
        Metrics.Classification value = Metrics.classification(8, 9, 1, 2);
        assertThat(value.accuracy()).isEqualTo(0.85);
        assertThat(value.precision()).isEqualTo(8.0 / 9.0);
        assertThat(value.recall()).isEqualTo(0.8);
        assertThat(Metrics.percentile(List.of(1, 2, 3, 4), 0.5)).isEqualTo(2.5);
    }

    @Test
    void calculatesMrrBinaryNdcgAndFirstRank() {
        assertThat(Metrics.firstRelevantRank(List.of("x", "a", "b"), List.of("a", "b"))).isEqualTo(2);
        assertThat(Metrics.reciprocalRank(List.of("x", "a"), List.of("a"))).isEqualTo(0.5);
        assertThat(Metrics.reciprocalRank(List.of("x"), List.of("a"))).isZero();
        assertThat(Metrics.binaryNdcg(List.of("a", "b"), List.of("a", "b"), 2)).isEqualTo(1.0);
        assertThat(Metrics.binaryNdcg(List.of("x", "a"), List.of("a"), 2)).isBetween(0.0, 1.0);
    }
}
