package com.storyweaver.llm.adapter;

import com.storyweaver.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public final class DeepSeekNotConfiguredException extends ApiException {
    public DeepSeekNotConfiguredException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "deepseek_not_configured", "DeepSeek API key is not configured");
    }
}
