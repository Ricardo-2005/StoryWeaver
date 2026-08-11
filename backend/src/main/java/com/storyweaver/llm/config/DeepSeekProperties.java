package com.storyweaver.llm.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyweaver.deepseek")
public record DeepSeekProperties(
        URI baseUrl, String apiKey, String userIdSecret, Duration connectTimeout, Duration retryInitialBackoff) {

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
