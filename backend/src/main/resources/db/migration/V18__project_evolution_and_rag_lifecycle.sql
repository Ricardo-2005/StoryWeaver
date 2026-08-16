-- V1.2: versioned project state, retrieval eligibility and temporal guards.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE project_reconstruction_candidate
    DROP CONSTRAINT ck_project_reconstruction_candidate_status,
    ADD COLUMN suggested_action VARCHAR(32) NOT NULL DEFAULT 'NEEDS_REVIEW',
    ADD COLUMN target_entity_id UUID,
    ADD COLUMN subject_name VARCHAR(120),
    ADD COLUMN policy_reason VARCHAR(500),
    ADD COLUMN character_importance VARCHAR(20),
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by UUID REFERENCES app_user(id),
    ADD COLUMN revocation_reason VARCHAR(500),
    ADD COLUMN superseded_by UUID REFERENCES project_reconstruction_candidate(id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_project_reconstruction_candidate_status CHECK (
        status IN ('CANDIDATE','ACCEPTED','REJECTED','REVOKED','APPLIED','CONFLICT')
    ),
    ADD CONSTRAINT ck_project_reconstruction_candidate_action CHECK (suggested_action IN (
        'CREATE_CHARACTER','UPDATE_PROFILE','APPEND_STATE','APPEND_KNOWLEDGE',
        'APPEND_RELATIONSHIP','APPEND_EVENT','MERGE_ALIAS','UPDATE_WORLD_ASSET',
        'CREATE_FORESHADOW','ADVANCE_FORESHADOW','RESOLVE_FORESHADOW',
        'UPDATE_ROLLING_OUTLINE','IGNORE','NEEDS_REVIEW'
    )),
    ADD CONSTRAINT ck_project_reconstruction_character_importance CHECK (
        character_importance IS NULL OR character_importance IN
        ('PROTAGONIST','MAJOR','SUPPORTING','MINOR','MENTION_ONLY')
    );
CREATE INDEX idx_reconstruction_candidate_policy
    ON project_reconstruction_candidate(job_id, suggested_action, status);

ALTER TABLE character
    ADD COLUMN importance VARCHAR(20) NOT NULL DEFAULT 'MINOR',
    ADD COLUMN lifecycle_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN merged_into UUID REFERENCES character(id) ON DELETE SET NULL,
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_character_importance CHECK (
        importance IN ('PROTAGONIST','MAJOR','SUPPORTING','MINOR','MENTION_ONLY')
    ),
    ADD CONSTRAINT ck_character_lifecycle CHECK (
        lifecycle_status IN ('CANDIDATE','ACTIVE','INACTIVE','DECEASED','MISSING',
            'LEFT_STORY','MERGED','REJECTED','ARCHIVED','PURGED')
    ),
    ADD CONSTRAINT ck_character_merge_target CHECK (
        (lifecycle_status = 'MERGED' AND merged_into IS NOT NULL)
        OR (lifecycle_status <> 'MERGED' AND merged_into IS NULL)
    );
UPDATE character
SET lifecycle_status = CASE WHEN archived THEN 'ARCHIVED' ELSE 'ACTIVE' END,
    retrieval_eligible = NOT archived;
CREATE INDEX idx_character_current_retrieval
    ON character(project_id, lifecycle_status, importance, updated_at DESC)
    WHERE retrieval_eligible = TRUE;

CREATE TABLE character_state_timeline (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    character_id UUID NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    life_status VARCHAR(16) NOT NULL,
    current_location VARCHAR(200),
    physical_condition TEXT,
    emotional_state TEXT,
    abilities TEXT,
    inventory_notes TEXT,
    notes TEXT,
    valid_from_chapter_no INTEGER NOT NULL,
    valid_to_chapter_no INTEGER,
    source_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    evidence TEXT,
    confidence VARCHAR(12) NOT NULL DEFAULT 'HIGH',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    superseded_by UUID REFERENCES character_state_timeline(id) ON DELETE SET NULL,
    created_by UUID REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_character_state_timeline_range CHECK (
        valid_from_chapter_no > 0 AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no >= valid_from_chapter_no)
    ),
    CONSTRAINT ck_character_state_timeline_confidence CHECK (confidence IN ('HIGH','MEDIUM','LOW')),
    CONSTRAINT ck_character_state_timeline_status CHECK (status IN ('ACTIVE','SUPERSEDED','REVOKED'))
);
CREATE INDEX idx_character_state_timeline_current
    ON character_state_timeline(project_id, character_id, valid_from_chapter_no DESC)
    WHERE valid_to_chapter_no IS NULL AND status = 'ACTIVE';

ALTER TABLE character_knowledge
    DROP CONSTRAINT uq_character_knowledge_fact,
    ADD COLUMN learned_at_chapter_no INTEGER,
    ADD COLUMN forgotten_at_chapter_no INTEGER,
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN superseded_by UUID REFERENCES character_knowledge(id) ON DELETE SET NULL,
    ADD COLUMN content_hash VARCHAR(64);
UPDATE character_knowledge k
SET learned_at_chapter_no = c.chapter_no,
    content_hash = encode(digest(k.content, 'sha256'), 'hex')
FROM chapter c WHERE c.id = k.acquired_chapter_id;
ALTER TABLE character_knowledge
    ALTER COLUMN learned_at_chapter_no SET NOT NULL,
    ADD CONSTRAINT ck_character_knowledge_range CHECK (
        forgotten_at_chapter_no IS NULL OR forgotten_at_chapter_no >= learned_at_chapter_no
    ),
    ADD CONSTRAINT ck_character_knowledge_lifecycle CHECK (
        lifecycle_status IN ('ACTIVE','SUPERSEDED','REVOKED','REJECTED')
    );
CREATE UNIQUE INDEX uq_character_knowledge_current
    ON character_knowledge(project_id, character_id, fact_key)
    WHERE lifecycle_status = 'ACTIVE' AND forgotten_at_chapter_no IS NULL;
CREATE INDEX idx_character_knowledge_temporal
    ON character_knowledge(project_id, character_id, learned_at_chapter_no, forgotten_at_chapter_no)
    WHERE retrieval_eligible = TRUE;

CREATE TABLE character_relationship_timeline (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    source_character_id UUID NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    target_character_id UUID NOT NULL REFERENCES character(id) ON DELETE CASCADE,
    relationship_type VARCHAR(80) NOT NULL,
    description TEXT,
    valid_from_chapter_no INTEGER NOT NULL,
    valid_to_chapter_no INTEGER,
    source_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    evidence TEXT,
    confidence VARCHAR(12) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    superseded_by UUID REFERENCES character_relationship_timeline(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_character_relationship_distinct CHECK (source_character_id <> target_character_id),
    CONSTRAINT ck_character_relationship_range CHECK (
        valid_from_chapter_no > 0 AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no >= valid_from_chapter_no)
    ),
    CONSTRAINT ck_character_relationship_status CHECK (status IN ('ACTIVE','SUPERSEDED','REVOKED'))
);
CREATE INDEX idx_character_relationship_current
    ON character_relationship_timeline(project_id, source_character_id, target_character_id)
    WHERE valid_to_chapter_no IS NULL AND status = 'ACTIVE';

CREATE TABLE item_ownership_timeline (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    item_key VARCHAR(160) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    owner_character_id UUID REFERENCES character(id) ON DELETE SET NULL,
    item_status VARCHAR(16) NOT NULL,
    valid_from_chapter_no INTEGER NOT NULL,
    valid_to_chapter_no INTEGER,
    source_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    evidence TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    superseded_by UUID REFERENCES item_ownership_timeline(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_item_timeline_range CHECK (
        valid_from_chapter_no > 0 AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no >= valid_from_chapter_no)
    ),
    CONSTRAINT ck_item_timeline_status CHECK (status IN ('ACTIVE','SUPERSEDED','REVOKED'))
);
CREATE INDEX idx_item_ownership_timeline_current
    ON item_ownership_timeline(project_id, item_key, valid_from_chapter_no DESC)
    WHERE valid_to_chapter_no IS NULL AND status = 'ACTIVE';

ALTER TABLE story_fact
    ADD COLUMN valid_from_chapter_no INTEGER DEFAULT 1,
    ADD COLUMN valid_to_chapter_no INTEGER,
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN superseded_by UUID REFERENCES story_fact(id) ON DELETE SET NULL,
    ADD COLUMN content_hash VARCHAR(64);
UPDATE story_fact f
SET valid_from_chapter_no = COALESCE(c.chapter_no, 1),
    lifecycle_status = CASE WHEN f.status = 'REJECTED' THEN 'REJECTED' ELSE 'ACTIVE' END,
    retrieval_eligible = f.status = 'ACCEPTED',
    content_hash = encode(digest(f.content, 'sha256'), 'hex')
FROM chapter c WHERE c.id = f.chapter_id;
UPDATE story_fact SET valid_from_chapter_no = 1 WHERE valid_from_chapter_no IS NULL;
ALTER TABLE story_fact
    ALTER COLUMN valid_from_chapter_no SET NOT NULL,
    ADD CONSTRAINT ck_story_fact_valid_range CHECK (
        valid_from_chapter_no > 0 AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no >= valid_from_chapter_no)
    ),
    ADD CONSTRAINT ck_story_fact_lifecycle CHECK (
        lifecycle_status IN ('ACTIVE','SUPERSEDED','REVOKED','REJECTED')
    );
CREATE INDEX idx_story_fact_current_context
    ON story_fact(project_id, valid_from_chapter_no, valid_to_chapter_no)
    WHERE status = 'ACCEPTED' AND retrieval_eligible = TRUE AND lifecycle_status = 'ACTIVE';

ALTER TABLE story_event
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN embedding_version BIGINT NOT NULL DEFAULT 1;
UPDATE story_event SET content_hash = encode(digest(action || E'\n' || result, 'sha256'), 'hex');
ALTER TABLE story_event ADD CONSTRAINT ck_story_event_lifecycle CHECK (
    lifecycle_status IN ('ACTIVE','SUPERSEDED','ARCHIVED','REVOKED')
);
CREATE INDEX idx_story_event_temporal_retrieval
    ON story_event(project_id, chapter_no, importance DESC)
    WHERE retrieval_eligible = TRUE AND lifecycle_status = 'ACTIVE';

ALTER TABLE worldbook_entry
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN valid_from_chapter_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN valid_to_chapter_no INTEGER,
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN embedding_version BIGINT NOT NULL DEFAULT 1;
UPDATE worldbook_entry SET content_hash = encode(digest(title || E'\n' || content, 'sha256'), 'hex');
ALTER TABLE worldbook_entry
    ADD CONSTRAINT ck_worldbook_entry_lifecycle CHECK (
        lifecycle_status IN ('ACTIVE','SUPERSEDED','ARCHIVED','REVOKED')
    ),
    ADD CONSTRAINT ck_worldbook_entry_valid_range CHECK (
        valid_from_chapter_no > 0 AND (valid_to_chapter_no IS NULL OR valid_to_chapter_no > valid_from_chapter_no)
    );
CREATE INDEX idx_worldbook_entry_temporal_retrieval
    ON worldbook_entry(project_id, valid_from_chapter_no, valid_to_chapter_no, priority DESC)
    WHERE retrieval_eligible = TRUE AND lifecycle_status = 'ACTIVE';

CREATE TABLE worldbook_entry_version (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES worldbook_entry(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    version_no BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    valid_from_chapter_no INTEGER NOT NULL,
    valid_to_chapter_no INTEGER,
    lifecycle_status VARCHAR(16) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_worldbook_entry_version UNIQUE(entry_id, version_no)
);

ALTER TABLE outline_node
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN source_type VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_outline_lifecycle CHECK (
        lifecycle_status IN ('PLANNED','IN_PROGRESS','REALIZED','DIVERGED','ARCHIVED')
    ),
    ADD CONSTRAINT ck_outline_source_type CHECK (source_type IN ('MANUAL','REVERSE_OUTLINE'));

ALTER TABLE foreshadow
    DROP CONSTRAINT ck_foreshadow_status,
    ADD COLUMN expected_window_start INTEGER,
    ADD COLUMN expected_window_end INTEGER,
    ADD COLUMN related_character_ids UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN related_world_asset_ids UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN priority INTEGER NOT NULL DEFAULT 500,
    ADD COLUMN confidence VARCHAR(12) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN retrieval_eligible BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_foreshadow_priority CHECK (priority BETWEEN 0 AND 1000),
    ADD CONSTRAINT ck_foreshadow_confidence CHECK (confidence IN ('HIGH','MEDIUM','LOW')),
    ADD CONSTRAINT ck_foreshadow_expected_window CHECK (
        expected_window_start IS NULL OR expected_window_end IS NULL OR expected_window_end >= expected_window_start
    );
UPDATE foreshadow SET status = CASE status
    WHEN 'PLANNED' THEN 'CANDIDATE'
    WHEN 'ADVANCED' THEN 'DEVELOPING'
    ELSE status END;
UPDATE foreshadow SET retrieval_eligible = status NOT IN ('RESOLVED','ABANDONED','REJECTED');
ALTER TABLE foreshadow ADD CONSTRAINT ck_foreshadow_status CHECK (
    status IN ('CANDIDATE','PLANTED','DEVELOPING','DUE','RESOLVED',
        'PARTIALLY_RESOLVED','ABANDONED','REJECTED')
);
CREATE INDEX idx_foreshadow_due_retrieval
    ON foreshadow(project_id, status, priority DESC, target_chapter_no)
    WHERE retrieval_eligible = TRUE;

ALTER TABLE rolling_outline
    ADD COLUMN base_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    ADD COLUMN from_chapter_no INTEGER,
    ADD COLUMN to_chapter_no INTEGER,
    ADD COLUMN current_arc_id UUID REFERENCES outline_node(id) ON DELETE SET NULL,
    ADD COLUMN open_threads_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN recent_character_changes_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN current_locations_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN active_items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN active_foreshadow_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN next_constraints_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN source_chapter_ids UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN content_hash VARCHAR(64),
    ADD COLUMN stale BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE rolling_outline_snapshot (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    base_chapter_id UUID NOT NULL REFERENCES chapter(id) ON DELETE CASCADE,
    from_chapter_no INTEGER NOT NULL,
    to_chapter_no INTEGER NOT NULL,
    recent_summary TEXT,
    current_arc_id UUID REFERENCES outline_node(id) ON DELETE SET NULL,
    current_goal TEXT,
    active_conflicts JSONB NOT NULL DEFAULT '[]'::jsonb,
    open_threads JSONB NOT NULL DEFAULT '[]'::jsonb,
    recent_character_changes JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_locations JSONB NOT NULL DEFAULT '[]'::jsonb,
    active_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    active_foreshadow JSONB NOT NULL DEFAULT '[]'::jsonb,
    next_constraints JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_chapter_ids UUID[] NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_rolling_outline_snapshot_range CHECK (
        from_chapter_no > 0 AND to_chapter_no >= from_chapter_no
    )
);
CREATE INDEX idx_rolling_outline_snapshot_project
    ON rolling_outline_snapshot(project_id, to_chapter_no DESC, version DESC);

ALTER TABLE chapter_reconstruction_metadata
    ADD COLUMN lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'CURRENT',
    ADD COLUMN content_hash VARCHAR(64),
    ADD CONSTRAINT ck_chapter_reconstruction_metadata_lifecycle CHECK (
        lifecycle_status IN ('CURRENT','STALE','SUPERSEDED')
    );

CREATE TABLE asset_dependency (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    source_asset_type VARCHAR(40) NOT NULL,
    source_asset_id UUID NOT NULL,
    target_asset_type VARCHAR(40) NOT NULL,
    target_asset_id UUID NOT NULL,
    relation_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_asset_dependency UNIQUE (
        project_id, source_asset_type, source_asset_id, target_asset_type, target_asset_id, relation_type
    )
);
CREATE INDEX idx_asset_dependency_source
    ON asset_dependency(project_id, source_asset_type, source_asset_id);

CREATE TABLE asset_invalidation (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    asset_type VARCHAR(40) NOT NULL,
    asset_id UUID NOT NULL,
    reason VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'STALE',
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_asset_invalidation_status CHECK (status IN ('STALE','REFRESHED','IGNORED'))
);
CREATE INDEX idx_asset_invalidation_open
    ON asset_invalidation(project_id, asset_type, created_at DESC)
    WHERE status = 'STALE';

-- Historical workflow/item rows keep their project history while allowing an explicitly
-- confirmed character purge to physically remove the character record.
ALTER TABLE workflow_run
    DROP CONSTRAINT fk_workflow_run_viewpoint,
    ALTER COLUMN viewpoint_character_id DROP NOT NULL,
    ADD CONSTRAINT fk_workflow_run_viewpoint
        FOREIGN KEY (viewpoint_character_id, project_id)
        REFERENCES character(id, project_id) ON DELETE SET NULL (viewpoint_character_id);
ALTER TABLE chapter_batch
    DROP CONSTRAINT chapter_batch_viewpoint_character_id_fkey,
    ALTER COLUMN viewpoint_character_id DROP NOT NULL,
    ADD CONSTRAINT fk_chapter_batch_viewpoint
        FOREIGN KEY (viewpoint_character_id) REFERENCES character(id) ON DELETE SET NULL;
ALTER TABLE item_ownership
    DROP CONSTRAINT fk_item_owner_character,
    ADD CONSTRAINT fk_item_owner_character
        FOREIGN KEY (owner_character_id, project_id)
        REFERENCES character(id, project_id) ON DELETE SET NULL (owner_character_id);
