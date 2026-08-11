CREATE TABLE workflow_run (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES app_user(id),
    viewpoint_character_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    instruction TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    plan_json JSONB,
    extraction_json JSONB,
    draft_content TEXT NOT NULL DEFAULT '',
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    recovery_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    heartbeat_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_run_chapter
        FOREIGN KEY (chapter_id, project_id) REFERENCES chapter(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_workflow_run_viewpoint
        FOREIGN KEY (viewpoint_character_id, project_id) REFERENCES character(id, project_id),
    CONSTRAINT uq_workflow_run_user_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_workflow_run_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_workflow_run_status CHECK (status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING', 'PLAN_READY',
        'WRITING', 'TEXT_READY', 'EXTRACTING', 'WAITING_APPROVAL',
        'BLOCKED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT ck_workflow_run_recovery_count CHECK (recovery_count >= 0)
);

CREATE UNIQUE INDEX uq_workflow_run_active_project
    ON workflow_run(project_id)
    WHERE status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING',
        'PLAN_READY', 'WRITING', 'TEXT_READY', 'EXTRACTING'
    );

CREATE INDEX idx_workflow_run_recovery
    ON workflow_run(status, heartbeat_at)
    WHERE status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING',
        'PLAN_READY', 'WRITING', 'TEXT_READY', 'EXTRACTING'
    );

CREATE TABLE context_packet (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    chapter_id UUID NOT NULL,
    workflow_run_id UUID NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    context_data JSONB NOT NULL,
    worldbook_report JSONB NOT NULL,
    memory_report JSONB NOT NULL,
    skill_snapshot JSONB NOT NULL,
    token_estimate INTEGER NOT NULL,
    estimated_cost NUMERIC(16, 6) NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_context_packet_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_context_packet_chapter
        FOREIGN KEY (chapter_id, project_id) REFERENCES chapter(id, project_id) ON DELETE CASCADE,
    CONSTRAINT ck_context_packet_tokens CHECK (token_estimate >= 0),
    CONSTRAINT ck_context_packet_cost CHECK (estimated_cost >= 0)
);

CREATE INDEX idx_context_packet_project_chapter
    ON context_packet(project_id, chapter_id, created_at DESC);

CREATE TABLE workflow_step (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workflow_run_id UUID NOT NULL,
    step_name VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_workflow_step_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE,
    CONSTRAINT uq_workflow_step_run_name UNIQUE (workflow_run_id, step_name),
    CONSTRAINT ck_workflow_step_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_workflow_step_attempt CHECK (attempt > 0)
);

CREATE TABLE workflow_event (
    event_id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL,
    workflow_run_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    step_name VARCHAR(32) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_workflow_event_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE
);

CREATE INDEX idx_workflow_event_run_replay
    ON workflow_event(workflow_run_id, event_id);
