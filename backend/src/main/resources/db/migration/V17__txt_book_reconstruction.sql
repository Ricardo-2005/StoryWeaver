ALTER TABLE novel_project
    ADD COLUMN reconstruction_status VARCHAR(24) NOT NULL DEFAULT 'NOT_ANALYZED',
    ADD CONSTRAINT ck_novel_project_reconstruction_status CHECK (
        reconstruction_status IN ('NOT_ANALYZED','ANALYZING','PARTIAL','REVIEW_REQUIRED','READY')
    );

CREATE TABLE book_reconstruction_job (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES book_import_job(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    mode VARCHAR(16) NOT NULL,
    status VARCHAR(40) NOT NULL,
    current_step VARCHAR(40) NOT NULL,
    include_skill_distillation BOOLEAN NOT NULL,
    include_foreshadowing BOOLEAN NOT NULL,
    total_chapters INTEGER NOT NULL,
    total_chunks INTEGER NOT NULL DEFAULT 0,
    processed_chunks INTEGER NOT NULL DEFAULT 0,
    failed_chapters INTEGER NOT NULL DEFAULT 0,
    estimated_calls INTEGER NOT NULL,
    estimated_input_tokens BIGINT NOT NULL,
    estimated_output_tokens BIGINT NOT NULL,
    estimated_cost_min NUMERIC(18,8),
    estimated_cost_max NUMERIC(18,8),
    estimate_currency VARCHAR(3),
    max_budget NUMERIC(18,8),
    actual_input_tokens BIGINT NOT NULL DEFAULT 0,
    actual_output_tokens BIGINT NOT NULL DEFAULT 0,
    actual_reasoning_tokens BIGINT NOT NULL DEFAULT 0,
    actual_cost NUMERIC(18,8) NOT NULL DEFAULT 0,
    retry_count INTEGER NOT NULL DEFAULT 0,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    pause_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    analysis_version VARCHAR(40) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    model VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_book_reconstruction_mode CHECK (mode IN ('QUICK','STANDARD','DEEP')),
    CONSTRAINT ck_book_reconstruction_status CHECK (status IN (
        'WAITING_USER_CONFIRMATION','QUEUED','PREPROCESSING','CHAPTER_ANALYSIS',
        'VOLUME_AGGREGATION','ENTITY_RESOLUTION','GLOBAL_RECONSTRUCTION',
        'FORESHADOW_ANALYSIS','SKILL_DISTILLATION','VALIDATING','WAITING_REVIEW',
        'APPLYING','COMPLETED','PAUSED','PAUSED_BUDGET','PARTIAL','CANCELLED','FAILED'
    )),
    CONSTRAINT ck_book_reconstruction_counts CHECK (
        total_chapters >= 0 AND total_chunks >= 0 AND processed_chunks >= 0
        AND processed_chunks <= total_chunks AND failed_chapters >= 0
    ),
    CONSTRAINT ck_book_reconstruction_budget CHECK (max_budget IS NULL OR max_budget >= 0)
);
CREATE INDEX idx_book_reconstruction_project_created
    ON book_reconstruction_job(project_id, created_at DESC);
CREATE INDEX idx_book_reconstruction_owner_status
    ON book_reconstruction_job(owner_id, status, updated_at);

ALTER TABLE usage_record
    ADD COLUMN reconstruction_job_id UUID REFERENCES book_reconstruction_job(id) ON DELETE SET NULL;
CREATE INDEX idx_usage_record_reconstruction_job
    ON usage_record(reconstruction_job_id, requested_at);

CREATE UNIQUE INDEX uq_book_reconstruction_active_project
    ON book_reconstruction_job(project_id)
    WHERE status IN ('QUEUED','PREPROCESSING','CHAPTER_ANALYSIS','VOLUME_AGGREGATION',
        'ENTITY_RESOLUTION','GLOBAL_RECONSTRUCTION','FORESHADOW_ANALYSIS',
        'SKILL_DISTILLATION','VALIDATING','APPLYING','PAUSED','PAUSED_BUDGET');

CREATE TABLE book_analysis_chunk (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES book_reconstruction_job(id) ON DELETE CASCADE,
    chapter_id UUID NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    chapter_index INTEGER NOT NULL,
    start_offset BIGINT NOT NULL,
    end_offset BIGINT NOT NULL,
    text_hash VARCHAR(64) NOT NULL,
    token_estimate INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_book_analysis_chunk_sequence UNIQUE(job_id, sequence_no),
    CONSTRAINT ck_book_analysis_chunk_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    CONSTRAINT ck_book_analysis_chunk_offsets CHECK (start_offset >= 0 AND end_offset > start_offset),
    CONSTRAINT ck_book_analysis_chunk_attempt CHECK (attempt_count >= 0)
);
CREATE INDEX idx_book_analysis_chunk_job_status
    ON book_analysis_chunk(job_id, status, sequence_no);

CREATE TABLE project_reconstruction_candidate (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES book_reconstruction_job(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapter(id) ON DELETE CASCADE,
    chunk_id UUID REFERENCES book_analysis_chunk(id) ON DELETE SET NULL,
    candidate_type VARCHAR(40) NOT NULL,
    natural_key VARCHAR(240),
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
    confidence VARCHAR(12) NOT NULL,
    inference_type VARCHAR(24) NOT NULL,
    evidence_count INTEGER NOT NULL DEFAULT 0,
    source_coverage NUMERIC(7,6) NOT NULL DEFAULT 0,
    source_anchors JSONB NOT NULL DEFAULT '[]'::jsonb,
    safe_to_apply BOOLEAN NOT NULL DEFAULT FALSE,
    applied_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_project_reconstruction_candidate_status CHECK (
        status IN ('CANDIDATE','ACCEPTED','REJECTED','APPLIED','CONFLICT')
    ),
    CONSTRAINT ck_project_reconstruction_confidence CHECK (confidence IN ('HIGH','MEDIUM','LOW')),
    CONSTRAINT ck_project_reconstruction_inference CHECK (
        inference_type IN ('DIRECT_FACT','MODEL_INFERENCE','USER_CONFIRMED')
    ),
    CONSTRAINT ck_project_reconstruction_evidence CHECK (evidence_count >= 0),
    CONSTRAINT ck_project_reconstruction_coverage CHECK (source_coverage BETWEEN 0 AND 1)
);
CREATE INDEX idx_project_reconstruction_candidate_job
    ON project_reconstruction_candidate(job_id, status, candidate_type, created_at);

CREATE TABLE chapter_reconstruction_metadata (
    chapter_id UUID PRIMARY KEY REFERENCES chapter(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    summary TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    analysis_version VARCHAR(40) NOT NULL,
    source_candidate_id UUID REFERENCES project_reconstruction_candidate(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE book_reconstruction_step (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES book_reconstruction_job(id) ON DELETE CASCADE,
    step_name VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed_units INTEGER NOT NULL DEFAULT 0,
    total_units INTEGER NOT NULL DEFAULT 0,
    summary VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_book_reconstruction_step_status CHECK (status IN ('STARTED','COMPLETED','FAILED','PAUSED'))
);
CREATE INDEX idx_book_reconstruction_step_job
    ON book_reconstruction_step(job_id, created_at);
