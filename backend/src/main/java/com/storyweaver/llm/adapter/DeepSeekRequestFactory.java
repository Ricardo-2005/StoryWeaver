package com.storyweaver.llm.adapter;

import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.ConfigurationPreview;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekRequestFactory {
    private static final List<String> IGNORED = List.of(
            "presence_penalty: DeepSeek V4 no longer supports this parameter",
            "frequency_penalty: DeepSeek V4 no longer supports this parameter");

    public Map<String, Object> create(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId) {
        return create(agent, prompt, pseudonymousUserId, agent.model());
    }

    public Map<String, Object> create(DeepSeekAgent agent, Prompt prompt, String pseudonymousUserId, String model) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", prompt.system()),
                        Map.of("role", "user", "content", prompt.user())));
        request.put("thinking", Map.of("type", agent.thinking() ? "enabled" : "disabled"));
        if (agent.thinking()) {
            request.put("reasoning_effort", agent.reasoningEffort());
        } else if (agent.temperature() != null) {
            request.put("temperature", agent.temperature());
        }
        if (agent.jsonOutput()) {
            request.put("response_format", Map.of("type", "json_object"));
        }
        request.put("stream", agent.stream());
        request.put("max_tokens", agent.maxOutputTokens());
        request.put("user_id", pseudonymousUserId);
        return request;
    }

    public ConfigurationPreview preview(DeepSeekAgent agent) {
        return new ConfigurationPreview(
                agent.name(),
                agent.model(),
                agent.thinking(),
                agent.reasoningEffort(),
                agent.temperature(),
                agent.jsonOutput(),
                agent.stream(),
                agent.maxOutputTokens(),
                agent.maxAttempts(),
                IGNORED);
    }
}
