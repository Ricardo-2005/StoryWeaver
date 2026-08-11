package com.storyweaver.llm.domain;

import java.util.List;

public final class DeepSeekModels {
    private DeepSeekModels() {}

    public record Prompt(String system, String user) {}

    public record Usage(
            int promptTokens,
            int completionTokens,
            int reasoningTokens,
            int promptCacheHitTokens,
            int promptCacheMissTokens) {
        public static Usage empty() {
            return new Usage(0, 0, 0, 0, 0);
        }
    }

    public record Response(
            String requestId,
            String model,
            String content,
            String reasoningContent,
            String finishReason,
            List<ToolCall> toolCalls,
            Usage usage,
            int attempts,
            long durationMillis) {}

    public record ToolCall(String id, String name, String arguments) {}

    public record StreamResult(
            String requestId, String model, String content, String finishReason, Usage usage, long durationMillis) {}

    @FunctionalInterface
    public interface TextChunkSink {
        void accept(String text) throws Exception;
    }

    public record ParsedResponse<T>(T value, Response response) {}

    public record ConfigurationPreview(
            String agent,
            String model,
            boolean thinking,
            String reasoningEffort,
            Double temperature,
            boolean jsonOutput,
            boolean stream,
            int maxOutputTokens,
            int maxAttempts,
            List<String> ignoredParameters) {}
}
