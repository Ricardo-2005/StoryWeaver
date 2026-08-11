package com.storyweaver.llm.adapter;

import com.storyweaver.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public final class DeepSeekCallException extends ApiException {
    private final boolean retryable;

    public DeepSeekCallException(String code, String message, boolean retryable) {
        super(HttpStatus.BAD_GATEWAY, code, message);
        this.retryable = retryable;
    }

    public DeepSeekCallException(String code, String message, boolean retryable, Throwable cause) {
        this(code, message, retryable);
        initCause(cause);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
