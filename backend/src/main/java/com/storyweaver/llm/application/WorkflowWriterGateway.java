package com.storyweaver.llm.application;

import com.storyweaver.llm.application.AgentContracts.AgentInput;
import java.util.UUID;
import java.util.function.Consumer;

public interface WorkflowWriterGateway {
    WriterResult write(UUID projectId, UUID userId, AgentInput input, Consumer<String> chunks);

    record WriterResult(
            String requestId,
            String model,
            String finishReason,
            int promptTokens,
            int completionTokens,
            int cacheHitTokens,
            int cacheMissTokens,
            long durationMillis) {}
}
