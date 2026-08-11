CREATE TABLE outline_node (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    parent_id UUID,
    node_type VARCHAR(20) NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT,
    objective TEXT,
    sequence_no INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_outline_node_id_project UNIQUE (id, project_id),
    CONSTRAINT fk_outline_node_parent
        FOREIGN KEY (parent_id, project_id)
        REFERENCES outline_node(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_outline_node_type CHECK (node_type IN ('MASTER', 'VOLUME', 'ARC', 'CHAPTER')),
    CONSTRAINT ck_outline_node_sequence CHECK (sequence_no >= 0)
);

CREATE UNIQUE INDEX uq_outline_node_position
    ON outline_node(project_id, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), sequence_no);

CREATE INDEX idx_outline_node_project_parent
    ON outline_node(project_id, parent_id, sequence_no);

CREATE TABLE chapter (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    outline_node_id UUID,
    chapter_no INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    outline TEXT,
    status VARCHAR(24) NOT NULL,
    current_version_no INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chapter_id_project UNIQUE (id, project_id),
    CONSTRAINT uq_chapter_project_no UNIQUE (project_id, chapter_no),
    CONSTRAINT fk_chapter_outline
        FOREIGN KEY (outline_node_id, project_id)
        REFERENCES outline_node(id, project_id),
    CONSTRAINT ck_chapter_no CHECK (chapter_no > 0),
    CONSTRAINT ck_chapter_current_version CHECK (current_version_no >= 0),
    CONSTRAINT ck_chapter_status CHECK (
        status IN ('DRAFT', 'GENERATING', 'REVIEW_REQUIRED', 'WAITING_APPROVAL', 'CONFIRMED', 'ARCHIVED')
    )
);

CREATE INDEX idx_chapter_project_no ON chapter(project_id, chapter_no);

CREATE TABLE chapter_version (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    chapter_id UUID NOT NULL,
    version_no INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    change_summary VARCHAR(500),
    restored_from_version_no INTEGER,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_chapter_version_chapter
        FOREIGN KEY (chapter_id, project_id)
        REFERENCES chapter(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_chapter_version UNIQUE (chapter_id, version_no),
    CONSTRAINT ck_chapter_version_no CHECK (version_no > 0),
    CONSTRAINT ck_chapter_restore_source CHECK (
        restored_from_version_no IS NULL OR restored_from_version_no > 0
    )
);

CREATE INDEX idx_chapter_version_project_chapter
    ON chapter_version(project_id, chapter_id, version_no DESC);
