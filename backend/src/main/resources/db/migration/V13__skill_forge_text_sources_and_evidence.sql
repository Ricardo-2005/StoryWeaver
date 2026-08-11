ALTER TABLE skill_forge_run
    DROP CONSTRAINT ck_skill_forge_run_status,
    DROP CONSTRAINT ck_skill_forge_run_mode,
    ADD COLUMN skill_type VARCHAR(20) NOT NULL DEFAULT 'FOUNDATION',
    ADD COLUMN learning_focus VARCHAR(1000),
    ADD COLUMN material_description VARCHAR(1000),
    ADD COLUMN exclude_character_names BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN exclude_locations BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN exclude_plot_facts BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN reusable_methods_only BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN ownership_statement TEXT NOT NULL DEFAULT '',
    ADD COLUMN ownership_confirmed_at TIMESTAMPTZ;

ALTER TABLE skill_forge_run
    ADD CONSTRAINT ck_skill_forge_run_mode CHECK (mode IN ('MANUAL','DERIVED','MATERIAL','TEXT_SOURCES')),
    ADD CONSTRAINT ck_skill_forge_run_skill_type
        CHECK (skill_type IN ('FOUNDATION','GENRE','TECHNIQUE','REVIEW')),
    ADD CONSTRAINT ck_skill_forge_run_status CHECK (status IN (
        'CREATED','SOURCE_READY','PREPROCESSING','EXTRACTING','CROSS_VALIDATING',
        'WAITING_CONFLICT_RESOLUTION','BUILDING_CONTRACT','WAITING_REVIEW',
        'VALIDATING','VALIDATED','VALIDATION_FAILED','FAILED','CANCELLED',
        'COLLECTING','DISTILLING'
    ));

CREATE TABLE skill_source (
    id UUID PRIMARY KEY,
    forge_run_id UUID NOT NULL REFERENCES skill_forge_run(id) ON DELETE CASCADE,
    source_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    material_type VARCHAR(20) NOT NULL,
    original_filename VARCHAR(255),
    detected_encoding VARCHAR(20) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    character_count INTEGER NOT NULL,
    paragraph_count INTEGER NOT NULL,
    ownership_confirmed BOOLEAN NOT NULL,
    raw_content_storage_ref VARCHAR(255) NOT NULL,
    raw_bytes BYTEA NOT NULL,
    normalized_text TEXT NOT NULL,
    source_order INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_skill_source_run_order UNIQUE (forge_run_id, source_order),
    CONSTRAINT ck_skill_source_type CHECK (source_type IN ('TXT','MANUAL_TEXT')),
    CONSTRAINT ck_skill_source_material_type CHECK (material_type IN (
        'PROSE','DIALOGUE','CHARACTER','DESCRIPTION','OUTLINE','WRITING_RULES','OTHER'
    )),
    CONSTRAINT ck_skill_source_character_count CHECK (character_count >= 0),
    CONSTRAINT ck_skill_source_paragraph_count CHECK (paragraph_count >= 0)
);

CREATE TABLE skill_source_paragraph (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES skill_source(id) ON DELETE CASCADE,
    paragraph_key VARCHAR(64) NOT NULL,
    sequence_no INTEGER NOT NULL,
    start_offset INTEGER NOT NULL,
    end_offset INTEGER NOT NULL,
    excerpt_hash VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    CONSTRAINT uq_skill_source_paragraph_key UNIQUE (source_id, paragraph_key),
    CONSTRAINT uq_skill_source_paragraph_sequence UNIQUE (source_id, sequence_no)
);

ALTER TABLE global_skill_atomic_rule
    ALTER COLUMN skill_version_id DROP NOT NULL,
    ADD COLUMN forge_run_id UUID REFERENCES skill_forge_run(id) ON DELETE CASCADE,
    ADD COLUMN scope VARCHAR(24) NOT NULL DEFAULT 'LOCAL_PATTERN',
    ADD COLUMN evidence_level VARCHAR(12) NOT NULL DEFAULT 'LOW',
    ADD COLUMN evidence_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
    ADD COLUMN user_modified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE global_skill_atomic_rule SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE global_skill_atomic_rule ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE global_skill_atomic_rule
    ADD CONSTRAINT ck_global_skill_atomic_owner
        CHECK (skill_version_id IS NOT NULL OR forge_run_id IS NOT NULL),
    ADD CONSTRAINT ck_global_skill_atomic_scope
        CHECK (scope IN ('LOCAL_PATTERN','REPEATED_PATTERN','EXPLICIT_USER_RULE')),
    ADD CONSTRAINT ck_global_skill_atomic_evidence_level
        CHECK (evidence_level IN ('LOW','MEDIUM','HIGH')),
    ADD CONSTRAINT ck_global_skill_atomic_status
        CHECK (status IN ('CANDIDATE','ACCEPTED','REJECTED','CONFLICT'));

CREATE TABLE skill_forge_step (
    id UUID PRIMARY KEY,
    forge_run_id UUID NOT NULL REFERENCES skill_forge_run(id) ON DELETE CASCADE,
    step_name VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_skill_forge_step_status CHECK (status IN ('STARTED','COMPLETED','FAILED'))
);

CREATE TABLE skill_test_case (
    id UUID PRIMARY KEY,
    global_skill_id UUID NOT NULL REFERENCES global_skill(id) ON DELETE CASCADE,
    forge_run_id UUID REFERENCES skill_forge_run(id) ON DELETE SET NULL,
    case_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    prompt TEXT NOT NULL,
    expected_assertions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_skill_test_case_type CHECK (case_type IN (
        'TYPICAL','CONFLICT','EDGE','OUT_OF_EVIDENCE','OVERFITTING','HONESTY_BOUNDARY'
    ))
);

CREATE TABLE skill_test_run (
    id UUID PRIMARY KEY,
    global_skill_id UUID NOT NULL REFERENCES global_skill(id) ON DELETE CASCADE,
    skill_version_id UUID REFERENCES global_skill_version(id) ON DELETE SET NULL,
    forge_run_id UUID REFERENCES skill_forge_run(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_skill_test_run_status CHECK (status IN ('RUNNING','PASSED','FAILED')),
    CONSTRAINT ck_skill_test_run_score CHECK (score BETWEEN 0 AND 100)
);

CREATE TABLE skill_test_result (
    id UUID PRIMARY KEY,
    test_run_id UUID NOT NULL REFERENCES skill_test_run(id) ON DELETE CASCADE,
    test_case_id UUID NOT NULL REFERENCES skill_test_case(id) ON DELETE CASCADE,
    passed BOOLEAN NOT NULL,
    finding TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_skill_test_result UNIQUE (test_run_id, test_case_id)
);

CREATE INDEX idx_skill_source_run ON skill_source(forge_run_id, source_order);
CREATE INDEX idx_skill_source_paragraph_source ON skill_source_paragraph(source_id, sequence_no);
CREATE INDEX idx_global_skill_atomic_forge ON global_skill_atomic_rule(forge_run_id, dimension, status);
CREATE INDEX idx_skill_forge_step_run ON skill_forge_step(forge_run_id, created_at);
CREATE INDEX idx_skill_test_case_skill ON skill_test_case(global_skill_id, case_type);
CREATE INDEX idx_skill_test_run_skill ON skill_test_run(global_skill_id, created_at DESC);
