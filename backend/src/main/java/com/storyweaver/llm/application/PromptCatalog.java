package com.storyweaver.llm.application;

import com.storyweaver.llm.domain.DeepSeekAgent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptCatalog {
    private final Map<DeepSeekAgent, String> prompts = new EnumMap<>(DeepSeekAgent.class);

    public PromptCatalog() {
        for (DeepSeekAgent agent : DeepSeekAgent.values()) {
            prompts.put(agent, load("prompts/" + agent.name().toLowerCase() + "/system.md"));
        }
    }

    public String system(DeepSeekAgent agent) {
        return prompts.get(agent);
    }

    private String load(String location) {
        try (var input = new ClassPathResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Missing prompt resource: " + location, exception);
        }
    }
}
