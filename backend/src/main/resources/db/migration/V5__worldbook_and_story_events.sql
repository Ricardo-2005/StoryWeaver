CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE worldbook (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE REFERENCES novel_project(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    default_token_budget INTEGER NOT NULL DEFAULT 4000,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_worldbook_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_worldbook_token_budget CHECK (default_token_budget > 0)
);

CREATE TABLE worldbook_entry (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    worldbook_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    constant_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vector_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    keywords TEXT[] NOT NULL DEFAULT '{}',
    priority INTEGER NOT NULL DEFAULT 500,
    scope_type VARCHAR(24) NOT NULL DEFAULT 'PROJECT',
    scope_ref_id UUID,
    visibility_type VARCHAR(24) NOT NULL DEFAULT 'ALL',
    visibility_ref_id UUID,
    embedding_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
    embedding_model VARCHAR(120),
    embedding vector(512),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_worldbook_entry_worldbook
        FOREIGN KEY (worldbook_id, project_id)
        REFERENCES worldbook(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_worldbook_entry_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_worldbook_entry_priority CHECK (priority BETWEEN 0 AND 1000),
    CONSTRAINT ck_worldbook_entry_scope CHECK (
        (scope_type = 'PROJECT' AND scope_ref_id IS NULL)
        OR (scope_type IN ('CHAPTER', 'CHARACTER') AND scope_ref_id IS NOT NULL)
    ),
    CONSTRAINT ck_worldbook_entry_visibility CHECK (
        (visibility_type IN ('ALL', 'AUTHOR_ONLY') AND visibility_ref_id IS NULL)
        OR (visibility_type = 'CHARACTER_ONLY' AND visibility_ref_id IS NOT NULL)
    ),
    CONSTRAINT ck_worldbook_entry_embedding_status CHECK (
        embedding_status IN ('NOT_REQUESTED', 'AVAILABLE', 'UNAVAILABLE')
    )
);

CREATE INDEX idx_worldbook_entry_project_active
    ON worldbook_entry(project_id, active, priority DESC);

CREATE TABLE story_event (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID,
    chapter_no INTEGER,
    participant_ids UUID[] NOT NULL DEFAULT '{}',
    known_by_ids UUID[] NOT NULL DEFAULT '{}',
    location VARCHAR(200),
    story_time VARCHAR(200),
    action TEXT NOT NULL,
    result TEXT NOT NULL,
    importance DOUBLE PRECISION NOT NULL DEFAULT 0.5,
    evidence_paragraph VARCHAR(200),
    embedding_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
    embedding_model VARCHAR(120),
    embedding vector(512),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_story_event_chapter
        FOREIGN KEY (chapter_id, project_id)
        REFERENCES chapter(id, project_id),
    CONSTRAINT uq_story_event_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_story_event_chapter CHECK (
        (chapter_id IS NULL AND chapter_no IS NULL)
        OR (chapter_id IS NOT NULL AND chapter_no IS NOT NULL AND chapter_no > 0)
    ),
    CONSTRAINT ck_story_event_importance CHECK (importance BETWEEN 0.0 AND 1.0),
    CONSTRAINT ck_story_event_embedding_status CHECK (
        embedding_status IN ('NOT_REQUESTED', 'AVAILABLE', 'UNAVAILABLE')
    )
);

CREATE INDEX idx_story_event_project_chapter
    ON story_event(project_id, chapter_no DESC NULLS LAST, created_at DESC);
