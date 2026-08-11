package com.storyweaver.llm.adapter;

import com.storyweaver.llm.config.DeepSeekProperties;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.ParsedResponse;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import com.storyweaver.llm.domain.DeepSeekModels.Response;
import com.storyweaver.llm.domain.DeepSeekModels.StreamResult;
import com.storyweaver.llm.domain.DeepSeekModels.TextChunkSink;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeepSeekAdapter {
    private final DeepSeekProperties properties;
    private final DeepSeekHttpClient client;
    private final DeepSeekUserPseudonymizer pseudonymizer;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Semaphore proConcurrency = new Semaphore(4, true);
    private final Semaphore flashConcurrency = new Semaphore(8, true);

    public DeepSeekAdapter(
            DeepSeekProperties properties,
            DeepSeekHttpClient client,
            DeepSeekUserPseudonymizer pseudonymizer,
            ObjectMapper objectMapper,
            Validator validator) {
        this.properties = properties;
        this.client = client;
        this.pseudonymizer = pseudonymizer;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public <T> ParsedResponse<T> completeJson(DeepSeekAgent agent, Prompt prompt, UUID userId, Class<T> responseType) {
        requireConfigured();
        Semaphore semaphore = semaphore(agent);
        acquire(semaphore);
        try {
            DeepSeekCallException last = null;
            for (int attempt = 1; attempt <= agent.maxAttempts(); attempt++) {
                try {
                    String model = attempt == 1 ? agent.model() : agent.fallbackModel();
                    Response raw = client.complete(agent, prompt, pseudonymizer.pseudonym(userId), model);
                    validateStructuredResponse(raw);
                    T value = objectMapper.readValue(raw.content(), responseType);
                    Set<ConstraintViolation<T>> violations = validator.validate(value);
                    if (!violations.isEmpty()) {
                        throw new DeepSeekCallException(
                                "deepseek_schema_validation_failed", "DeepSeek JSON failed schema validation", true);
                    }
                    Response withAttempts = new Response(
                            raw.requestId(),
                            raw.model(),
                            raw.content(),
                            raw.reasoningContent(),
                            raw.finishReason(),
                            raw.toolCalls(),
                            raw.usage(),
                            attempt,
                            raw.durationMillis());
                    return new ParsedResponse<>(value, withAttempts);
                } catch (DeepSeekCallException exception) {
                    last = exception;
                } catch (RuntimeException exception) {
                    last = new DeepSeekCallException(
                            "deepseek_json_invalid", "DeepSeek returned malformed JSON", true, exception);
                }
                if (last == null || !last.isRetryable() || attempt == agent.maxAttempts()) throw last;
                backoff(attempt);
            }
            throw last;
        } finally {
            semaphore.release();
        }
    }

    public StreamResult stream(DeepSeekAgent agent, Prompt prompt, UUID userId, TextChunkSink sink) {
        requireConfigured();
        Semaphore semaphore = semaphore(agent);
        acquire(semaphore);
        try {
            try {
                return client.stream(agent, prompt, pseudonymizer.pseudonym(userId), agent.model(), sink);
            } catch (DeepSeekCallException exception) {
                if (!exception.isRetryable()) throw exception;
                return client.stream(agent, prompt, pseudonymizer.pseudonym(userId), agent.fallbackModel(), sink);
            }
        } finally {
            semaphore.release();
        }
    }

    private void validateStructuredResponse(Response response) {
        if (response.content() == null || response.content().isBlank()) {
            throw new DeepSeekCallException("deepseek_empty_content", "DeepSeek returned empty JSON content", true);
        }
        if ("length".equals(response.finishReason())) {
            throw new DeepSeekCallException("deepseek_json_truncated", "DeepSeek JSON was truncated", true);
        }
    }

    private void requireConfigured() {
        if (!properties.configured()) throw new DeepSeekNotConfiguredException();
    }

    private Semaphore semaphore(DeepSeekAgent agent) {
        return agent.model().endsWith("-flash") ? flashConcurrency : proConcurrency;
    }

    private void acquire(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekCallException(
                    "deepseek_interrupted", "DeepSeek concurrency wait was interrupted", false, exception);
        }
    }

    private void backoff(int attempt) {
        Duration base = properties.retryInitialBackoff().multipliedBy(1L << Math.min(attempt - 1, 3));
        long jitter = base.isZero() ? 0 : ThreadLocalRandom.current().nextLong(Math.max(1, base.toMillis() / 4 + 1));
        try {
            Thread.sleep(base.plusMillis(jitter));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekCallException("deepseek_interrupted", "DeepSeek retry was interrupted", false, exception);
        }
    }
}
