CREATE TABLE canon_asset (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    asset_type VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_version_no INTEGER NOT NULL,
    confirmed_version_no INTEGER,
    created_by UUID NOT NULL REFERENCES app_user(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_canon_asset_id_project UNIQUE (id, project_id),
    CONSTRAINT ck_canon_asset_status CHECK (
        status IN ('DRAFT', 'CANDIDATE', 'CONFIRMED', 'CONFLICTED', 'DEPRECATED')
    ),
    CONSTRAINT ck_canon_asset_current_version CHECK (current_version_no > 0),
    CONSTRAINT ck_canon_asset_confirmed_version CHECK (
        confirmed_version_no IS NULL OR confirmed_version_no BETWEEN 1 AND current_version_no
    )
);

CREATE INDEX idx_canon_asset_project_updated
    ON canon_asset(project_id, updated_at DESC);

CREATE TABLE canon_asset_version (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    version_no INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    change_summary VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_canon_asset_version_asset
        FOREIGN KEY (asset_id, project_id)
        REFERENCES canon_asset(id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_canon_asset_version UNIQUE (asset_id, version_no),
    CONSTRAINT ck_canon_asset_version_no CHECK (version_no > 0)
);

CREATE INDEX idx_canon_asset_version_project_asset
    ON canon_asset_version(project_id, asset_id, version_no DESC);
