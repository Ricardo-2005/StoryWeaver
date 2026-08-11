ALTER TABLE novel_project
    ADD COLUMN creation_source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_hash VARCHAR(64),
    ADD COLUMN source_encoding VARCHAR(20),
    ADD COLUMN parser_version VARCHAR(32),
    ADD CONSTRAINT ck_novel_project_creation_source
        CHECK (creation_source IN ('MANUAL', 'TXT_IMPORT'));

CREATE TABLE book_import_source (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(80),
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    raw_content_hash VARCHAR(64) NOT NULL,
    normalized_content_hash VARCHAR(64),
    detected_encoding VARCHAR(20) NOT NULL,
    selected_encoding VARCHAR(20),
    encoding_confident BOOLEAN NOT NULL,
    character_count BIGINT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_book_import_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_book_import_source_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT ck_book_import_source_encoding
        CHECK (detected_encoding IN ('UTF-8', 'UTF-8-BOM', 'GB18030', 'GBK')),
    CONSTRAINT ck_book_import_source_selected_encoding
        CHECK (selected_encoding IS NULL OR selected_encoding IN ('UTF-8', 'GB18030', 'GBK'))
);
CREATE INDEX idx_book_import_source_owner_hash
    ON book_import_source(owner_id, sha256, created_at DESC);
CREATE INDEX idx_book_import_source_expiry
    ON book_import_source(expires_at) WHERE storage_key IS NOT NULL;

CREATE TABLE book_import_job (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    source_id UUID NOT NULL REFERENCES book_import_source(id),
    project_id UUID REFERENCES novel_project(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    parser_version VARCHAR(32) NOT NULL,
    total_characters BIGINT NOT NULL DEFAULT 0,
    total_chapters INTEGER NOT NULL DEFAULT 0,
    processed_chapters INTEGER NOT NULL DEFAULT 0,
    heading_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    analysis_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
    analysis_processed_chunks INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_book_import_job_status CHECK (status IN (
        'UPLOADED','DECODING','PARSED','WAITING_CONFIRMATION','IMPORTING','COMPLETED','FAILED','CANCELLED'
    )),
    CONSTRAINT ck_book_import_analysis_status CHECK (analysis_status IN (
        'NOT_REQUESTED','QUEUED','ANALYZING','WAITING_REVIEW','COMPLETED','FAILED','CANCELLED'
    )),
    CONSTRAINT ck_book_import_progress CHECK (
        total_characters >= 0 AND total_chapters >= 0 AND processed_chapters >= 0
        AND processed_chapters <= total_chapters
    )
);
CREATE INDEX idx_book_import_job_owner_created
    ON book_import_job(owner_id, created_at DESC);
CREATE INDEX idx_book_import_job_project
    ON book_import_job(project_id) WHERE project_id IS NOT NULL;

CREATE TABLE book_import_chapter (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES book_import_job(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    title VARCHAR(160) NOT NULL,
    start_offset BIGINT NOT NULL,
    end_offset BIGINT NOT NULL,
    character_count BIGINT NOT NULL,
    paragraph_count INTEGER NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    created_chapter_id UUID REFERENCES chapter(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_book_import_chapter_sequence UNIQUE(import_id, sequence_no),
    CONSTRAINT ck_book_import_chapter_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_book_import_chapter_offsets CHECK (
        start_offset >= 0 AND end_offset > start_offset AND character_count = end_offset - start_offset
    ),
    CONSTRAINT ck_book_import_chapter_paragraphs CHECK (paragraph_count >= 0)
);
CREATE INDEX idx_book_import_chapter_job
    ON book_import_chapter(import_id, sequence_no);

CREATE TABLE book_analysis_candidate (
    id UUID PRIMARY KEY,
    import_id UUID NOT NULL REFERENCES book_import_job(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    chapter_id UUID REFERENCES chapter(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    candidate_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_book_analysis_candidate_status CHECK (status IN ('CANDIDATE','ACCEPTED','REJECTED')),
    CONSTRAINT ck_book_analysis_candidate_chunk CHECK (chunk_index > 0)
);
CREATE INDEX idx_book_analysis_candidate_import
    ON book_analysis_candidate(import_id, candidate_type, chunk_index);

ALTER TABLE chapter
    ADD COLUMN import_source_id UUID REFERENCES book_import_source(id) ON DELETE SET NULL,
    ADD COLUMN source_start_offset BIGINT,
    ADD COLUMN source_end_offset BIGINT,
    ADD COLUMN source_hash VARCHAR(64),
    ADD CONSTRAINT ck_chapter_source_offsets CHECK (
        (source_start_offset IS NULL AND source_end_offset IS NULL)
        OR (source_start_offset >= 0 AND source_end_offset > source_start_offset)
    );

ALTER TABLE chapter_version
    ADD COLUMN creation_source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN import_source_id UUID REFERENCES book_import_source(id) ON DELETE SET NULL,
    ADD COLUMN source_start_offset BIGINT,
    ADD COLUMN source_end_offset BIGINT,
    ADD COLUMN source_hash VARCHAR(64),
    ADD COLUMN source_encoding VARCHAR(20),
    ADD COLUMN parser_version VARCHAR(32),
    ADD CONSTRAINT ck_chapter_version_creation_source
        CHECK (creation_source IN ('MANUAL', 'TXT_IMPORT')),
    ADD CONSTRAINT ck_chapter_version_source_offsets CHECK (
        (source_start_offset IS NULL AND source_end_offset IS NULL)
        OR (source_start_offset >= 0 AND source_end_offset > source_start_offset)
    );
