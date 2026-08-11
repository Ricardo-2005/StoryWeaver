package com.storyweaver.worldbook.application;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorldbookRetrievalRanker {
    public Ranking rank(List<RetrievalCandidate> candidates, WorldbookRetrievalOptions options) {
        List<RetrievalCandidate> unique = deduplicate(candidates);
        if (options.mode() == RetrievalExperimentMode.BASELINE) {
            List<RankedCandidate> ordered = unique.stream()
                    .sorted(legacyComparator())
                    .map(candidate -> ranked(candidate, legacyScore(candidate)))
                    .toList();
            return new Ranking(List.of(), ordered, candidates.size(), unique.size());
        }

        List<RankedCandidate> constants = unique.stream()
                .filter(RetrievalCandidate::constant)
                .sorted(legacyComparator())
                .map(candidate -> ranked(candidate, legacyScore(candidate)))
                .toList();
        List<RetrievalCandidate> dynamic = unique.stream()
                .filter(candidate ->
                        !candidate.constant() || candidate.sources().contains("KEYWORD"))
                .toList();
        List<RankedCandidate> ranked =
                switch (options.mode()) {
                    case CONSTANT_ISOLATED ->
                        dynamic.stream()
                                .sorted(legacyComparator())
                                .map(candidate -> ranked(candidate, legacyScore(candidate)))
                                .toList();
                    case KEYWORD_ONLY ->
                        dynamic.stream()
                                .filter(candidate -> candidate.sources().contains("KEYWORD"))
                                .sorted(Comparator.comparingDouble(RetrievalCandidate::keywordScore)
                                        .reversed()
                                        .thenComparing(Comparator.comparingInt(RetrievalCandidate::priority)
                                                .reversed())
                                        .thenComparing(RetrievalCandidate::title)
                                        .thenComparing(
                                                candidate -> candidate.entryId().toString()))
                                .map(candidate -> ranked(candidate, candidate.keywordScore()))
                                .toList();
                    case VECTOR_ONLY ->
                        dynamic.stream()
                                .filter(candidate -> candidate.sources().contains("VECTOR"))
                                .sorted(Comparator.comparingDouble(RetrievalCandidate::vectorScore)
                                        .reversed()
                                        .thenComparing(RetrievalCandidate::title)
                                        .thenComparing(
                                                candidate -> candidate.entryId().toString()))
                                .map(candidate -> ranked(candidate, candidate.vectorScore()))
                                .toList();
                    case HYBRID_FUSION -> reciprocalRankFusion(dynamic, options.rrfRankConstant());
                    case BASELINE -> throw new IllegalStateException("BASELINE handled above");
                };
        return new Ranking(constants, ranked, candidates.size(), unique.size());
    }

    private List<RankedCandidate> reciprocalRankFusion(List<RetrievalCandidate> candidates, int rankConstant) {
        List<RetrievalCandidate> keyword = candidates.stream()
                .filter(candidate -> candidate.sources().contains("KEYWORD"))
                .sorted(Comparator.comparingDouble(RetrievalCandidate::keywordScore)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(RetrievalCandidate::priority)
                                .reversed())
                        .thenComparing(RetrievalCandidate::title)
                        .thenComparing(candidate -> candidate.entryId().toString()))
                .toList();
        List<RetrievalCandidate> vector = candidates.stream()
                .filter(candidate -> candidate.sources().contains("VECTOR"))
                .sorted(Comparator.comparingDouble(RetrievalCandidate::vectorScore)
                        .reversed()
                        .thenComparing(RetrievalCandidate::title)
                        .thenComparing(candidate -> candidate.entryId().toString()))
                .toList();
        Map<UUID, Integer> keywordRanks = ranks(keyword);
        Map<UUID, Integer> vectorRanks = ranks(vector);
        return candidates.stream()
                .filter(candidate ->
                        keywordRanks.containsKey(candidate.entryId()) || vectorRanks.containsKey(candidate.entryId()))
                .map(candidate -> {
                    double score = reciprocal(keywordRanks.get(candidate.entryId()), rankConstant)
                            + reciprocal(vectorRanks.get(candidate.entryId()), rankConstant);
                    return ranked(candidate, score);
                })
                .sorted(Comparator.comparingDouble(RankedCandidate::finalScore)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(RankedCandidate::keywordScore)
                                .reversed())
                        .thenComparing(Comparator.comparingDouble(RankedCandidate::vectorScore)
                                .reversed())
                        .thenComparing(RankedCandidate::title)
                        .thenComparing(candidate -> candidate.entryId().toString()))
                .toList();
    }

    private Map<UUID, Integer> ranks(List<RetrievalCandidate> values) {
        Map<UUID, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++)
            ranks.put(values.get(index).entryId(), index + 1);
        return ranks;
    }

    private double reciprocal(Integer rank, int rankConstant) {
        return rank == null ? 0.0 : 1.0 / (rankConstant + rank);
    }

    private List<RetrievalCandidate> deduplicate(List<RetrievalCandidate> candidates) {
        Map<UUID, RetrievalCandidate> unique = new LinkedHashMap<>();
        for (RetrievalCandidate candidate : candidates) {
            unique.merge(candidate.entryId(), candidate, this::merge);
        }
        return new ArrayList<>(unique.values());
    }

    private RetrievalCandidate merge(RetrievalCandidate left, RetrievalCandidate right) {
        LinkedHashSet<String> sources = new LinkedHashSet<>(left.sources());
        sources.addAll(right.sources());
        return new RetrievalCandidate(
                left.entryId(),
                left.title(),
                Math.max(left.priority(), right.priority()),
                left.constant() || right.constant(),
                Math.max(left.keywordScore(), right.keywordScore()),
                Math.max(left.vectorScore(), right.vectorScore()),
                List.copyOf(sources));
    }

    private Comparator<RetrievalCandidate> legacyComparator() {
        return Comparator.comparingInt(RetrievalCandidate::priority)
                .reversed()
                .thenComparing(Comparator.comparingDouble(RetrievalCandidate::vectorScore)
                        .reversed())
                .thenComparing(RetrievalCandidate::title)
                .thenComparing(candidate -> candidate.entryId().toString());
    }

    private double legacyScore(RetrievalCandidate candidate) {
        return candidate.priority() * 1_000_000.0 + candidate.vectorScore();
    }

    private RankedCandidate ranked(RetrievalCandidate candidate, double finalScore) {
        return new RankedCandidate(
                candidate.entryId(),
                candidate.title(),
                candidate.priority(),
                candidate.constant(),
                candidate.keywordScore(),
                candidate.vectorScore(),
                finalScore,
                candidate.sources());
    }

    public record RetrievalCandidate(
            UUID entryId,
            String title,
            int priority,
            boolean constant,
            double keywordScore,
            double vectorScore,
            List<String> sources) {}

    public record RankedCandidate(
            UUID entryId,
            String title,
            int priority,
            boolean constant,
            double keywordScore,
            double vectorScore,
            double finalScore,
            List<String> sources) {}

    public record Ranking(
            List<RankedCandidate> constants,
            List<RankedCandidate> rankedCandidates,
            int rawCandidateCount,
            int deduplicatedCandidateCount) {}
}
