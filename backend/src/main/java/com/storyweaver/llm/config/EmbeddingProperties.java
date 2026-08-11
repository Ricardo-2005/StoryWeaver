package com.storyweaver.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyweaver.embedding")
public record EmbeddingProperties(
        boolean enabled,
        String modelName,
        int dimensions,
        String modelUri,
        String tokenizerUri,
        String cacheDirectory) {}
