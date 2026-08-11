CREATE TABLE usage_record (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id),
    agent VARCHAR(24) NOT NULL,
    model VARCHAR(80) NOT NULL,
    request_id VARCHAR(160),
    status VARCHAR(24) NOT NULL,
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    reasoning_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_hit_tokens INTEGER NOT NULL DEFAULT 0,
    prompt_cache_miss_tokens INTEGER NOT NULL DEFAULT 0,
    attempts INTEGER NOT NULL DEFAULT 1,
    duration_ms BIGINT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_usage_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_usage_nonnegative CHECK (
        prompt_tokens >= 0 AND completion_tokens >= 0 AND reasoning_tokens >= 0
        AND prompt_cache_hit_tokens >= 0 AND prompt_cache_miss_tokens >= 0
        AND attempts > 0 AND duration_ms >= 0
    )
);

CREATE INDEX idx_usage_record_project_requested
    ON usage_record(project_id, requested_at DESC);
