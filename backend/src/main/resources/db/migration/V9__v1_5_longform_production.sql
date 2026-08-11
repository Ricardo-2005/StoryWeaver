CREATE TABLE story_import (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(100),
    status VARCHAR(32) NOT NULL,
    error_message VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_story_import_status CHECK (status IN ('UPLOADED','SPLIT_REVIEW','EXTRACTING','CANDIDATE_REVIEW','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_story_import_project ON story_import(project_id, created_at DESC);

CREATE TABLE story_import_chapter (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES story_import(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    created_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    CONSTRAINT uq_story_import_chapter UNIQUE(import_id, sequence_no),
    CONSTRAINT ck_story_import_chapter_sequence CHECK (sequence_no > 0)
);

CREATE TABLE story_import_candidate (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES story_import(id) ON DELETE CASCADE,
    candidate_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    source_chapter_no INTEGER,
    decision VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_by UUID REFERENCES app_user(id),
    decided_at TIMESTAMPTZ,
    CONSTRAINT ck_story_import_candidate_decision CHECK (decision IN ('PENDING','ACCEPTED','REJECTED'))
);
CREATE INDEX idx_story_import_candidate ON story_import_candidate(import_id, decision);

CREATE TABLE character_alias_merge (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    import_id UUID REFERENCES story_import(id) ON DELETE SET NULL,
    source_name VARCHAR(160) NOT NULL,
    target_character_id UUID NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE foreshadow (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(24) NOT NULL,
    planted_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    target_chapter_no INTEGER,
    resolved_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_foreshadow_status CHECK (status IN ('PLANNED','PLANTED','ADVANCED','RESOLVED','ABANDONED'))
);
CREATE INDEX idx_foreshadow_project ON foreshadow(project_id, status, updated_at DESC);

CREATE TABLE impact_report (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    summary TEXT,
    affected_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_impact_report_status CHECK (status IN ('READY','FAILED'))
);
CREATE INDEX idx_impact_report_chapter ON impact_report(chapter_id, created_at DESC);

CREATE TABLE rolling_outline (
    project_id UUID PRIMARY KEY REFERENCES novel_project(id) ON DELETE CASCADE,
    current_chapter_no INTEGER NOT NULL DEFAULT 1,
    window_size INTEGER NOT NULL DEFAULT 5,
    summary TEXT,
    goals_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    risks_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_rolling_outline_window CHECK (window_size BETWEEN 1 AND 20)
);

CREATE TABLE chapter_batch (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    viewpoint_character_id UUID NOT NULL REFERENCES character(id),
    instruction TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_index INTEGER NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_chapter_batch_status CHECK (status IN ('QUEUED','RUNNING','PAUSED','WAITING_GATE','COMPLETED','FAILED','CANCELLED'))
);
CREATE INDEX idx_chapter_batch_project ON chapter_batch(project_id, created_at DESC);
CREATE UNIQUE INDEX uq_chapter_batch_active_project
    ON chapter_batch(project_id)
    WHERE status IN ('QUEUED','RUNNING','PAUSED','WAITING_GATE');

CREATE TABLE chapter_batch_item (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES chapter_batch(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    chapter_id UUID NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    workflow_run_id UUID REFERENCES workflow_run(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    CONSTRAINT uq_chapter_batch_item UNIQUE(batch_id, sequence_no),
    CONSTRAINT uq_chapter_batch_chapter UNIQUE(batch_id, chapter_id)
);

CREATE TABLE story_gate (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    batch_id UUID REFERENCES chapter_batch(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapter(id) ON DELETE CASCADE,
    workflow_run_id UUID REFERENCES workflow_run(id) ON DELETE CASCADE,
    gate_type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    rationale TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_by UUID REFERENCES app_user(id),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_story_gate_status CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX idx_story_gate_run ON story_gate(workflow_run_id, status);

CREATE TABLE chapter_branch (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    promoted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chapter_branch_name UNIQUE(chapter_id, name),
    CONSTRAINT ck_chapter_branch_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE TABLE chapter_branch_version (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES chapter_branch(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    change_summary VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chapter_branch_version UNIQUE(branch_id, version_no)
);

CREATE TABLE model_attempt (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    workflow_run_id UUID REFERENCES workflow_run(id) ON DELETE CASCADE,
    agent VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    attempt_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    duration_millis BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_model_attempt_status CHECK (status IN ('SUCCEEDED','FAILED','SKIPPED'))
);
CREATE INDEX idx_model_attempt_run ON model_attempt(workflow_run_id, attempt_no);
