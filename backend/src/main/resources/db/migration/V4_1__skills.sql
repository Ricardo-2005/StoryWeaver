CREATE TABLE skill_definition (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    rules JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_skill_definition_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_skill_definition_rules_object CHECK (jsonb_typeof(rules) = 'object')
);

CREATE INDEX idx_skill_definition_project_updated
    ON skill_definition(project_id, updated_at DESC);

CREATE TABLE skill_binding (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    skill_definition_id UUID NOT NULL,
    scope VARCHAR(16) NOT NULL,
    chapter_id UUID,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_skill_binding_definition
        FOREIGN KEY (skill_definition_id, project_id)
        REFERENCES skill_definition(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_skill_binding_chapter
        FOREIGN KEY (chapter_id, project_id)
        REFERENCES chapter(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_skill_binding_definition UNIQUE (skill_definition_id),
    CONSTRAINT ck_skill_binding_scope CHECK (scope IN ('BASE', 'PROJECT', 'CHAPTER')),
    CONSTRAINT ck_skill_binding_target CHECK (
        (scope = 'CHAPTER' AND chapter_id IS NOT NULL)
        OR (scope IN ('BASE', 'PROJECT') AND chapter_id IS NULL)
    )
);

CREATE INDEX idx_skill_binding_compose
    ON skill_binding(project_id, scope, chapter_id);
