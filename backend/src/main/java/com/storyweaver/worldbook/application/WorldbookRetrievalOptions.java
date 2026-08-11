package com.storyweaver.worldbook.application;

import com.storyweaver.llm.config.RetrievalExperimentMode;

public record WorldbookRetrievalOptions(
        RetrievalExperimentMode mode, int candidatePoolSize, int finalRankingSize, int rrfRankConstant) {
    public WorldbookRetrievalOptions {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (candidatePoolSize < 1) throw new IllegalArgumentException("candidatePoolSize must be positive");
        if (finalRankingSize < 1) throw new IllegalArgumentException("finalRankingSize must be positive");
        if (rrfRankConstant < 1) throw new IllegalArgumentException("rrfRankConstant must be positive");
    }

    public static WorldbookRetrievalOptions baseline(int vectorTopK) {
        return new WorldbookRetrievalOptions(RetrievalExperimentMode.BASELINE, vectorTopK, Integer.MAX_VALUE, 60);
    }

    public static WorldbookRetrievalOptions optimized() {
        return new WorldbookRetrievalOptions(RetrievalExperimentMode.HYBRID_FUSION, 30, 10, 60);
    }
}
