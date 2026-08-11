CREATE TABLE pricing_rule (
    id UUID PRIMARY KEY,
    rule_version VARCHAR(80) NOT NULL,
    model VARCHAR(80) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'CNY',
    input_per_million NUMERIC(18, 8) NOT NULL,
    output_per_million NUMERIC(18, 8) NOT NULL,
    reasoning_per_million NUMERIC(18, 8) NOT NULL,
    cache_hit_per_million NUMERIC(18, 8) NOT NULL,
    cache_miss_per_million NUMERIC(18, 8) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pricing_rule_model_version UNIQUE (model, rule_version),
    CONSTRAINT ck_pricing_rule_nonnegative CHECK (
        input_per_million >= 0 AND output_per_million >= 0
        AND reasoning_per_million >= 0 AND cache_hit_per_million >= 0
        AND cache_miss_per_million >= 0
    ),
    CONSTRAINT ck_pricing_rule_window CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX idx_pricing_rule_lookup
    ON pricing_rule(model, active, effective_from DESC);

ALTER TABLE usage_record
    ADD COLUMN pricing_rule_id UUID REFERENCES pricing_rule(id),
    ADD COLUMN pricing_rule_version VARCHAR(80),
    ADD COLUMN estimated_cost NUMERIC(18, 8),
    ADD COLUMN actual_cost NUMERIC(18, 8),
    ADD COLUMN currency VARCHAR(3);

CREATE TABLE project_budget (
    project_id UUID PRIMARY KEY REFERENCES novel_project(id) ON DELETE CASCADE,
    task_token_limit INTEGER NOT NULL,
    user_daily_cost_limit NUMERIC(18, 8) NOT NULL,
    project_cost_limit NUMERIC(18, 8) NOT NULL,
    writer_output_token_limit INTEGER NOT NULL,
    planner_reasoning_token_limit INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_project_budget_positive CHECK (
        task_token_limit > 0 AND user_daily_cost_limit >= 0 AND project_cost_limit >= 0
        AND writer_output_token_limit > 0 AND planner_reasoning_token_limit > 0
    )
);

CREATE TABLE mcp_audit_log (
    id UUID PRIMARY KEY,
    caller_user_id UUID NOT NULL REFERENCES app_user(id),
    project_id UUID REFERENCES novel_project(id) ON DELETE CASCADE,
    operation_type VARCHAR(16) NOT NULL,
    operation_name VARCHAR(120) NOT NULL,
    request_id VARCHAR(160) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    error_code VARCHAR(100),
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_mcp_audit_type CHECK (operation_type IN ('TOOL', 'RESOURCE', 'PROMPT')),
    CONSTRAINT ck_mcp_audit_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_mcp_audit_duration CHECK (duration_ms >= 0)
);

CREATE INDEX idx_mcp_audit_project_created
    ON mcp_audit_log(project_id, created_at DESC);
CREATE INDEX idx_mcp_audit_caller_created
    ON mcp_audit_log(caller_user_id, created_at DESC);

ALTER TABLE story_fact DROP CONSTRAINT fk_story_fact_run;
ALTER TABLE story_fact DROP CONSTRAINT fk_story_fact_chapter;
ALTER TABLE story_fact
    ALTER COLUMN workflow_run_id DROP NOT NULL,
    ALTER COLUMN chapter_id DROP NOT NULL,
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'WORKFLOW',
    ADD COLUMN created_by UUID REFERENCES app_user(id),
    ADD COLUMN mcp_request_key VARCHAR(160),
    ADD CONSTRAINT fk_story_fact_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_story_fact_chapter
        FOREIGN KEY (chapter_id, project_id)
        REFERENCES chapter(id, project_id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_story_fact_source CHECK (source IN ('WORKFLOW', 'MCP')),
    ADD CONSTRAINT ck_story_fact_origin CHECK (
        (source = 'WORKFLOW' AND workflow_run_id IS NOT NULL AND chapter_id IS NOT NULL)
        OR (source = 'MCP' AND workflow_run_id IS NULL AND created_by IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_story_fact_mcp_request
    ON story_fact(project_id, created_by, mcp_request_key)
    WHERE source = 'MCP' AND mcp_request_key IS NOT NULL;
