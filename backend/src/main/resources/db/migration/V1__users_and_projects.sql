CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    normalized_username VARCHAR(50) NOT NULL,
    email VARCHAR(320) NOT NULL,
    normalized_email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT uq_app_user_normalized_username UNIQUE (normalized_username),
    CONSTRAINT uq_app_user_normalized_email UNIQUE (normalized_email)
);

CREATE TABLE novel_project (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    name VARCHAR(120) NOT NULL,
    genre VARCHAR(80),
    description VARCHAR(2000),
    author_intent TEXT,
    current_focus TEXT,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_novel_project_owner_updated
    ON novel_project(owner_id, updated_at DESC);

CREATE TABLE project_snapshot (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    project_version BIGINT NOT NULL,
    snapshot_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_snapshot_project_created
    ON project_snapshot(project_id, created_at DESC);
