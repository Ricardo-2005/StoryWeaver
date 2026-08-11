package com.storyweaver.evals;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import com.storyweaver.worldbook.application.WorldbookRetrievalOptions;
import com.storyweaver.worldbook.application.WorldbookRetrievalRanker;
import com.storyweaver.worldbook.application.WorldbookRetrievalRanker.RetrievalCandidate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorldbookRetrievalRankerTest {
    private final WorldbookRetrievalRanker ranker = new WorldbookRetrievalRanker();

    @Test
    void rrfDeduplicatesAndCombinesKeywordAndVectorRanks() {
        UUID combined = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<RetrievalCandidate> candidates = List.of(
                candidate(combined, "combined", 50, false, 3.0, 0.0, "KEYWORD"),
                candidate(combined, "combined", 50, false, 0.0, 0.9, "VECTOR"),
                new RetrievalCandidate(other, "other", 50, false, 1.0, 0.8, List.of("KEYWORD", "VECTOR")));

        var result = ranker.rank(
                candidates, new WorldbookRetrievalOptions(RetrievalExperimentMode.HYBRID_FUSION, 30, 10, 60));

        assertThat(result.rawCandidateCount()).isEqualTo(3);
        assertThat(result.deduplicatedCandidateCount()).isEqualTo(2);
        assertThat(result.rankedCandidates()).extracting(value -> value.entryId()).containsExactly(combined, other);
        assertThat(result.rankedCandidates().getFirst().sources()).containsExactly("KEYWORD", "VECTOR");
    }

    @Test
    void constantIsolationReservesRulesWithoutCrowdingDynamicRank() {
        UUID rule = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID fact = UUID.fromString("00000000-0000-0000-0000-000000000011");
        var result = ranker.rank(
                List.of(
                        candidate(rule, "rule", 100, true, 0.0, 0.9, "CONSTANT", "VECTOR"),
                        candidate(fact, "fact", 50, false, 0.0, 0.8, "VECTOR")),
                new WorldbookRetrievalOptions(RetrievalExperimentMode.CONSTANT_ISOLATED, 10, 10, 60));

        assertThat(result.constants()).extracting(value -> value.entryId()).containsExactly(rule);
        assertThat(result.rankedCandidates()).extracting(value -> value.entryId()).containsExactly(fact);
    }

    @Test
    void rankingIsDeterministicForReorderedInputAndTies() {
        List<RetrievalCandidate> candidates = new ArrayList<>(List.of(
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000021"), "same", 50, false, 0, 0.5, "VECTOR"),
                candidate(UUID.fromString("00000000-0000-0000-0000-000000000020"), "same", 50, false, 0, 0.5, "VECTOR")));
        var options = new WorldbookRetrievalOptions(RetrievalExperimentMode.VECTOR_ONLY, 10, 10, 60);
        List<UUID> first = ranker.rank(candidates, options).rankedCandidates().stream()
                .map(value -> value.entryId()).toList();
        Collections.reverse(candidates);
        List<UUID> second = ranker.rank(candidates, options).rankedCandidates().stream()
                .map(value -> value.entryId()).toList();
        assertThat(first).containsExactlyElementsOf(second);
    }

    private RetrievalCandidate candidate(
            UUID id, String title, int priority, boolean constant, double keyword, double vector, String... sources) {
        return new RetrievalCandidate(id, title, priority, constant, keyword, vector, List.of(sources));
    }
}
