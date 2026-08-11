package com.storyweaver.llm.adapter;

import com.storyweaver.llm.config.DeepSeekProperties;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import com.storyweaver.llm.domain.DeepSeekModels.Response;
import com.storyweaver.llm.domain.DeepSeekModels.StreamResult;
import com.storyweaver.llm.domain.DeepSeekModels.TextChunkSink;
import com.storyweaver.llm.domain.DeepSeekModels.Usage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeepSeekHttpClient {
    private final HttpClient httpClient;
    private final DeepSeekProperties properties;
    private final DeepSeekRequestFactory requests;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public DeepSeekHttpClient(
            HttpClient deepSeekHttpClient,
            DeepSeekProperties properties,
            DeepSeekRequestFactory requests,
            ObjectMapper objectMapper,
            Clock clock) {
        this.httpClient = deepSeekHttpClient;
        this.properties = properties;
        this.requests = requests;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public Response complete(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId) {
        return complete(agent, prompt, pseudonymousUserId, agent.model());
    }

    public Response complete(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId, String modelOverride) {
        long started = clock.millis();
        try {
            HttpResponse<String> response = httpClient.send(
                    request(agent, prompt, pseudonymousUserId, modelOverride),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choice = firstChoice(root);
            JsonNode message = choice.path("message");
            return new Response(
                    text(root, "id"),
                    text(root, "model"),
                    nullableText(message, "content"),
                    nullableText(message, "reasoning_content"),
                    nullableText(choice, "finish_reason"),
                    toolCalls(message.path("tool_calls")),
                    usage(root.path("usage")),
                    1,
                    clock.millis() - started);
        } catch (DeepSeekCallException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DeepSeekCallException("deepseek_io_error", "DeepSeek connection failed", true, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekCallException("deepseek_interrupted", "DeepSeek call was interrupted", false, exception);
        } catch (RuntimeException exception) {
            throw new DeepSeekCallException(
                    "deepseek_invalid_response", "DeepSeek returned an invalid response", true, exception);
        }
    }

    public StreamResult stream(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId, TextChunkSink sink) {
        return stream(agent, prompt, pseudonymousUserId, agent.model(), sink);
    }

    public StreamResult stream(
            DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId, String modelOverride, TextChunkSink sink) {
        long started = clock.millis();
        StringBuilder content = new StringBuilder();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> model = new AtomicReference<>(modelOverride);
        AtomicReference<String> finishReason = new AtomicReference<>();
        AtomicReference<Usage> usage = new AtomicReference<>(Usage.empty());
        AtomicBoolean done = new AtomicBoolean();
        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    request(agent, prompt, pseudonymousUserId, modelOverride), HttpResponse.BodyHandlers.ofLines());
            ensureSuccess(response.statusCode());
            try (Stream<String> lines = response.body()) {
                lines.forEach(line -> consumeLine(line, sink, content, requestId, model, finishReason, usage, done));
            }
            if (!done.get() || content.isEmpty()) {
                throw new DeepSeekCallException(
                        "deepseek_stream_incomplete", "DeepSeek stream ended before completion", content.isEmpty());
            }
            return new StreamResult(
                    requestId.get(),
                    model.get(),
                    content.toString(),
                    finishReason.get(),
                    usage.get(),
                    clock.millis() - started);
        } catch (DeepSeekCallException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DeepSeekCallException(
                    "deepseek_stream_io_error", "DeepSeek stream connection failed", content.isEmpty(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DeepSeekCallException(
                    "deepseek_interrupted", "DeepSeek stream was interrupted", false, exception);
        } catch (RuntimeException exception) {
            throw new DeepSeekCallException(
                    "deepseek_invalid_stream", "DeepSeek returned an invalid stream", content.isEmpty(), exception);
        }
    }

    private HttpRequest request(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId) {
        return request(agent, prompt, pseudonymousUserId, agent.model());
    }

    private HttpRequest request(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId, String modelOverride) {
        try {
            Map<String, Object> body = requests.create(agent, prompt, pseudonymousUserId, modelOverride);
            URI endpoint = properties.baseUrl().resolve("/chat/completions");
            return HttpRequest.newBuilder(endpoint)
                    .timeout(agent.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", agent.stream() ? "text/event-stream" : "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (RuntimeException exception) {
            throw new DeepSeekCallException(
                    "deepseek_request_error", "Could not build DeepSeek request", false, exception);
        }
    }

    private void consumeLine(
            String line,
            TextChunkSink sink,
            StringBuilder content,
            AtomicReference<String> requestId,
            AtomicReference<String> model,
            AtomicReference<String> finishReason,
            AtomicReference<Usage> usage,
            AtomicBoolean done) {
        if (line == null || line.isBlank() || line.startsWith(":")) return;
        if (!line.startsWith("data:")) return;
        String payload = line.substring(5).trim();
        if (payload.isEmpty()) return;
        if ("[DONE]".equals(payload)) {
            done.set(true);
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!text(root, "id").isBlank()) requestId.set(text(root, "id"));
            if (!text(root, "model").isBlank()) model.set(text(root, "model"));
            if (!root.path("usage").isMissingNode() && !root.path("usage").isNull()) {
                usage.set(usage(root.path("usage")));
            }
            if (root.path("choices").isArray() && !root.path("choices").isEmpty()) {
                JsonNode choice = root.path("choices").get(0);
                String finish = nullableText(choice, "finish_reason");
                if (finish != null) finishReason.set(finish);
                String delta = nullableText(choice.path("delta"), "content");
                if (delta != null && !delta.isEmpty()) {
                    sink.accept(delta);
                    content.append(delta);
                }
            }
        } catch (DeepSeekCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DeepSeekCallException(
                    "deepseek_invalid_stream",
                    "DeepSeek returned an invalid stream event",
                    content.isEmpty(),
                    exception);
        }
    }

    private JsonNode firstChoice(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new DeepSeekCallException("deepseek_empty_choices", "DeepSeek returned no choices", true);
        }
        return choices.get(0);
    }

    private Usage usage(JsonNode node) {
        return new Usage(
                node.path("prompt_tokens").asInt(0),
                node.path("completion_tokens").asInt(0),
                node.path("completion_tokens_details").path("reasoning_tokens").asInt(0),
                node.path("prompt_cache_hit_tokens").asInt(0),
                node.path("prompt_cache_miss_tokens").asInt(0));
    }

    private List<com.storyweaver.llm.domain.DeepSeekModels.ToolCall> toolCalls(JsonNode node) {
        if (!node.isArray()) return List.of();
        var calls = new ArrayList<com.storyweaver.llm.domain.DeepSeekModels.ToolCall>();
        node.forEach(call -> calls.add(new com.storyweaver.llm.domain.DeepSeekModels.ToolCall(
                text(call, "id"), text(call.path("function"), "name"), text(call.path("function"), "arguments"))));
        return List.copyOf(calls);
    }

    private void ensureSuccess(int status) {
        if (status >= 200 && status < 300) return;
        boolean retryable = status == 408 || status == 429 || status >= 500;
        throw new DeepSeekCallException(
                "deepseek_http_" + status, "DeepSeek request failed with HTTP " + status, retryable);
    }

    private String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        return value == null ? "" : value;
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
