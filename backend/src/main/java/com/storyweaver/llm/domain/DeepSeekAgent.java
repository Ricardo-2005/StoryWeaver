package com.storyweaver.llm.domain;

import java.time.Duration;
import org.springframework.ai.deepseek.api.DeepSeekApi;

public enum DeepSeekAgent {
    PLANNER(
            DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue(),
            true,
            "high",
            null,
            true,
            false,
            6000,
            Duration.ofSeconds(180),
            2),
    WRITER(
            DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue(),
            false,
            null,
            0.78,
            false,
            true,
            12000,
            Duration.ofSeconds(300),
            1),
    EXTRACTOR(
            DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH.getValue(),
            false,
            null,
            0.1,
            true,
            false,
            7000,
            Duration.ofSeconds(120),
            3),
    REVIEWER(
            DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue(),
            true,
            "high",
            null,
            true,
            false,
            8000,
            Duration.ofSeconds(180),
            2);

    private final String model;
    private final boolean thinking;
    private final String reasoningEffort;
    private final Double temperature;
    private final boolean jsonOutput;
    private final boolean stream;
    private final int maxOutputTokens;
    private final Duration timeout;
    private final int maxAttempts;

    DeepSeekAgent(
            String model,
            boolean thinking,
            String reasoningEffort,
            Double temperature,
            boolean jsonOutput,
            boolean stream,
            int maxOutputTokens,
            Duration timeout,
            int maxAttempts) {
        this.model = model;
        this.thinking = thinking;
        this.reasoningEffort = reasoningEffort;
        this.temperature = temperature;
        this.jsonOutput = jsonOutput;
        this.stream = stream;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
        this.maxAttempts = maxAttempts;
    }

    public String model() {
        return model;
    }

    public boolean thinking() {
        return thinking;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    public Double temperature() {
        return temperature;
    }

    public boolean jsonOutput() {
        return jsonOutput;
    }

    public boolean stream() {
        return stream;
    }

    public int maxOutputTokens() {
        return maxOutputTokens;
    }

    public Duration timeout() {
        return timeout;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public String fallbackModel() {
        return model.endsWith("-flash")
                ? DeepSeekApi.ChatModel.DEEPSEEK_V4_PRO.getValue()
                : DeepSeekApi.ChatModel.DEEPSEEK_V4_FLASH.getValue();
    }
}
