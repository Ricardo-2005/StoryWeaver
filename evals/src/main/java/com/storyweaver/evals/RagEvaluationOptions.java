package com.storyweaver.evals;

import com.storyweaver.llm.config.RetrievalExperimentMode;
import com.storyweaver.worldbook.application.WorldbookRetrievalOptions;

record RagEvaluationOptions(
        RetrievalExperimentMode mode, int candidatePoolSize, int finalRankingSize, int rrfRankConstant) {
    static RagEvaluationOptions baseline() {
        return new RagEvaluationOptions(RetrievalExperimentMode.BASELINE, 10, Integer.MAX_VALUE, 60);
    }

    static RagEvaluationOptions selected() {
        return new RagEvaluationOptions(RetrievalExperimentMode.HYBRID_FUSION, 30, 10, 60);
    }

    WorldbookRetrievalOptions productionOptions() {
        return new WorldbookRetrievalOptions(mode, candidatePoolSize, finalRankingSize, rrfRankConstant);
    }
}
