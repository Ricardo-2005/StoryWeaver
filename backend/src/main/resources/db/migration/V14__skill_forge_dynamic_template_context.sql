ALTER TABLE skill_forge_run
    ADD COLUMN material_tag VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    ADD COLUMN genre VARCHAR(80),
    ADD COLUMN source_project_id UUID REFERENCES novel_project(id) ON DELETE SET NULL;

ALTER TABLE skill_forge_run
    ADD CONSTRAINT ck_skill_forge_run_material_tag CHECK (material_tag IN (
        'PROSE','DIALOGUE','CHARACTER','DESCRIPTION','OUTLINE','WRITING_RULES','OTHER'
    ));

CREATE INDEX idx_skill_forge_run_source_project ON skill_forge_run(source_project_id);
