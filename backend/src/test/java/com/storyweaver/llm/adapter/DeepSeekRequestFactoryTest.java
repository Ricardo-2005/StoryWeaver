package com.storyweaver.llm.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyweaver.llm.config.DeepSeekProperties;
import com.storyweaver.llm.domain.DeepSeekAgent;
import com.storyweaver.llm.domain.DeepSeekModels.Prompt;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeepSeekRequestFactoryTest {
    private final DeepSeekRequestFactory factory = new DeepSeekRequestFactory();

    @Test
    void filtersUnsupportedParametersAndSeparatesThinkingFromSampling() {
        Map<String, Object> planner = factory.create(DeepSeekAgent.PLANNER, new Prompt("system", "user"), "sw_test");
        assertThat(planner)
                .containsEntry("thinking", Map.of("type", "enabled"))
                .containsEntry("reasoning_effort", "high")
                .containsEntry("response_format", Map.of("type", "json_object"))
                .doesNotContainKeys("temperature", "top_p", "presence_penalty", "frequency_penalty");

        Map<String, Object> writer = factory.create(DeepSeekAgent.WRITER, new Prompt("system", "user"), "sw_test");
        assertThat(writer)
                .containsEntry("thinking", Map.of("type", "disabled"))
                .containsEntry("temperature", 0.78)
                .containsEntry("stream", true)
                .doesNotContainKeys("reasoning_effort", "top_p", "presence_penalty", "frequency_penalty");
        assertThat(factory.preview(DeepSeekAgent.WRITER).ignoredParameters())
                .allMatch(reason -> reason.contains("no longer supports"));
    }

    @Test
    void hmacUserIdIsStableAndContainsNoRawUuid() {
        var properties = new DeepSeekProperties(
                URI.create("https://api.deepseek.com"),
                "test-key",
                "separate-test-secret",
                Duration.ofSeconds(1),
                Duration.ZERO);
        var pseudonymizer = new DeepSeekUserPseudonymizer(properties);
        UUID userId = UUID.fromString("6e2c87d2-94c9-48c6-ac3a-17abc9ebc51d");

        String first = pseudonymizer.pseudonym(userId);

        assertThat(first).isEqualTo(pseudonymizer.pseudonym(userId));
        assertThat(first).startsWith("sw_").hasSize(27).doesNotContain(userId.toString());
    }

    @Test
    void supportsAnExplicitFallbackModelWithoutChangingAgentPolicy() {
        Map<String, Object> request = factory.create(
                DeepSeekAgent.PLANNER, new Prompt("system", "user"), "sw_test", DeepSeekAgent.PLANNER.fallbackModel());

        assertThat(request).containsEntry("model", DeepSeekAgent.PLANNER.fallbackModel());
        assertThat(request).containsEntry("thinking", Map.of("type", "enabled"));
        assertThat(DeepSeekAgent.PLANNER.fallbackModel()).isNotEqualTo(DeepSeekAgent.PLANNER.model());
    }
}
