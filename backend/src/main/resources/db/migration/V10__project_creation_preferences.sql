ALTER TABLE novel_project
    ADD COLUMN custom_genre VARCHAR(20),
    ADD COLUMN target_audience VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN narrative_perspective VARCHAR(20) NOT NULL DEFAULT 'THIRD_PERSON',
    ADD COLUMN length_type VARCHAR(20) NOT NULL DEFAULT 'LONG_NOVEL',
    ADD COLUMN premise VARCHAR(500),
    ADD COLUMN world_rules TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    ADD COLUMN target_word_count INTEGER,
    ADD COLUMN chapter_word_target INTEGER;

ALTER TABLE novel_project
    ADD CONSTRAINT ck_novel_project_target_audience
        CHECK (target_audience IN ('MALE', 'FEMALE', 'GENERAL')),
    ADD CONSTRAINT ck_novel_project_narrative_perspective
        CHECK (narrative_perspective IN ('FIRST_PERSON', 'THIRD_PERSON')),
    ADD CONSTRAINT ck_novel_project_length_type
        CHECK (length_type IN ('SHORT_NOVEL', 'LONG_NOVEL')),
    ADD CONSTRAINT ck_novel_project_target_word_count
        CHECK (target_word_count IS NULL OR target_word_count > 0),
    ADD CONSTRAINT ck_novel_project_chapter_word_target
        CHECK (chapter_word_target IS NULL OR chapter_word_target > 0);
