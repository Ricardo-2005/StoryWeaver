package com.storyweaver.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyweaver.retrieval")
public record RetrievalProperties(
        int worldbookDefaultTokenBudget,
        int worldbookDefaultTopK,
        int eventDefaultTopK,
        double semanticWeight,
        double participantWeight,
        double locationWeight,
        double chapterProximityWeight,
        double importanceWeight,
        RetrievalExperimentMode worldbookMode,
        int worldbookCandidatePoolSize,
        int worldbookFinalRankingSize,
        int worldbookRrfRankConstant) {}
