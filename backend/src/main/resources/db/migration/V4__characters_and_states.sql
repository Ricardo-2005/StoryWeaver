CREATE TABLE character (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    aliases VARCHAR(500),
    role VARCHAR(80),
    description TEXT,
    personality TEXT,
    background TEXT,
    goals TEXT,
    appearance TEXT,
    notes TEXT,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_character_id_project UNIQUE (id, project_id)
);

CREATE INDEX idx_character_project_updated
    ON character(project_id, updated_at DESC);

CREATE TABLE character_state (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    character_id UUID NOT NULL,
    life_status VARCHAR(16) NOT NULL,
    current_location VARCHAR(200),
    physical_condition TEXT,
    emotional_state TEXT,
    abilities TEXT,
    inventory_notes TEXT,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_character_state_character
        FOREIGN KEY (character_id, project_id)
        REFERENCES character(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_character_state_character UNIQUE (character_id),
    CONSTRAINT ck_character_state_life_status CHECK (life_status IN ('UNKNOWN', 'ALIVE', 'DEAD'))
);

CREATE INDEX idx_character_state_project_character
    ON character_state(project_id, character_id);
