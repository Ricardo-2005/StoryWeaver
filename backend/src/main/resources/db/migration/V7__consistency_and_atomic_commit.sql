ALTER TABLE workflow_run DROP CONSTRAINT ck_workflow_run_status;

ALTER TABLE workflow_run
    ADD COLUMN review_json JSONB,
    ADD COLUMN revision_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN committed_version_no INTEGER,
    ADD COLUMN approved_by UUID REFERENCES app_user(id),
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_workflow_run_status CHECK (status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING', 'PLAN_READY',
        'WRITING', 'TEXT_READY', 'EXTRACTING', 'VALIDATING', 'REVIEWING',
        'WAITING_APPROVAL', 'REVISION_REQUIRED', 'COMMITTING', 'COMPLETED',
        'BLOCKED', 'FAILED', 'CANCELLED', 'ROLLED_BACK'
    )),
    ADD CONSTRAINT ck_workflow_run_revision_count CHECK (revision_count >= 0),
    ADD CONSTRAINT ck_workflow_run_commit_fields CHECK (
        (status = 'COMPLETED' AND committed_version_no IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL)
        OR status <> 'COMPLETED'
    );

DROP INDEX uq_workflow_run_active_project;
CREATE UNIQUE INDEX uq_workflow_run_active_project
    ON workflow_run(project_id)
    WHERE status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING', 'PLAN_READY',
        'WRITING', 'TEXT_READY', 'EXTRACTING', 'VALIDATING', 'REVIEWING', 'COMMITTING'
    );

DROP INDEX idx_workflow_run_recovery;
CREATE INDEX idx_workflow_run_recovery
    ON workflow_run(status, heartbeat_at)
    WHERE status IN (
        'CREATED', 'PREFLIGHT', 'CONTEXT_READY', 'PLANNING', 'PLAN_READY',
        'WRITING', 'TEXT_READY', 'EXTRACTING', 'VALIDATING', 'REVIEWING'
    );

CREATE TABLE story_fact (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    workflow_run_id UUID NOT NULL,
    chapter_id UUID NOT NULL,
    candidate_index INTEGER NOT NULL,
    fact_key VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    evidence TEXT NOT NULL,
    paragraph_key VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    accepted_by UUID REFERENCES app_user(id),
    accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_story_fact_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_story_fact_chapter
        FOREIGN KEY (chapter_id, project_id)
        REFERENCES chapter(id, project_id) ON DELETE CASCADE,
    CONSTRAINT uq_story_fact_run_candidate UNIQUE (workflow_run_id, candidate_index),
    CONSTRAINT ck_story_fact_candidate_index CHECK (candidate_index >= 0),
    CONSTRAINT ck_story_fact_status CHECK (status IN ('CANDIDATE', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT ck_story_fact_acceptance CHECK (
        (status = 'ACCEPTED' AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
        OR (status <> 'ACCEPTED' AND accepted_by IS NULL AND accepted_at IS NULL)
    )
);

CREATE INDEX idx_story_fact_project_status
    ON story_fact(project_id, status, created_at DESC);

CREATE TABLE item_ownership (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    item_key VARCHAR(160) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    owner_character_id UUID,
    item_status VARCHAR(16) NOT NULL,
    acquired_chapter_id UUID NOT NULL,
    evidence TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_item_owner_character
        FOREIGN KEY (owner_character_id, project_id)
        REFERENCES character(id, project_id),
    CONSTRAINT fk_item_acquired_chapter
        FOREIGN KEY (acquired_chapter_id, project_id)
        REFERENCES chapter(id, project_id),
    CONSTRAINT uq_item_ownership_project_key UNIQUE (project_id, item_key),
    CONSTRAINT ck_item_ownership_status CHECK (item_status IN ('ACTIVE', 'DAMAGED', 'DESTROYED', 'LOST'))
);

CREATE INDEX idx_item_ownership_owner
    ON item_ownership(project_id, owner_character_id);

CREATE TABLE character_knowledge (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    character_id UUID NOT NULL,
    fact_key VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    certainty VARCHAR(16) NOT NULL,
    source_event_id UUID,
    acquired_chapter_id UUID NOT NULL,
    evidence TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_character_knowledge_character
        FOREIGN KEY (character_id, project_id)
        REFERENCES character(id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_character_knowledge_source_event
        FOREIGN KEY (source_event_id, project_id)
        REFERENCES story_event(id, project_id),
    CONSTRAINT fk_character_knowledge_chapter
        FOREIGN KEY (acquired_chapter_id, project_id)
        REFERENCES chapter(id, project_id),
    CONSTRAINT uq_character_knowledge_fact UNIQUE (project_id, character_id, fact_key),
    CONSTRAINT ck_character_knowledge_certainty CHECK (certainty IN ('SUSPECTED', 'CONFIRMED'))
);

CREATE INDEX idx_character_knowledge_character
    ON character_knowledge(project_id, character_id, updated_at DESC);

CREATE TABLE review_issue (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    workflow_run_id UUID NOT NULL,
    source VARCHAR(16) NOT NULL,
    category VARCHAR(40) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    evidence TEXT NOT NULL,
    historical_evidence TEXT,
    suggestion TEXT NOT NULL,
    blocking BOOLEAN NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_review_issue_run
        FOREIGN KEY (workflow_run_id, project_id)
        REFERENCES workflow_run(id, project_id) ON DELETE CASCADE,
    CONSTRAINT ck_review_issue_source CHECK (source IN ('JAVA', 'LLM')),
    CONSTRAINT ck_review_issue_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'BLOCKER')),
    CONSTRAINT ck_review_issue_blocker CHECK (severity <> 'BLOCKER' OR blocking)
);

CREATE INDEX idx_review_issue_run
    ON review_issue(workflow_run_id, blocking, resolved, created_at);
