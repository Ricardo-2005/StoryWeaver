CREATE TABLE global_skill (
    id UUID PRIMARY KEY,
    owner_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    slug VARCHAR(80) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    scope VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    contract_json JSONB NOT NULL,
    current_version_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_global_skill_owner_slug UNIQUE NULLS NOT DISTINCT (owner_id, slug),
    CONSTRAINT ck_global_skill_scope CHECK (scope IN ('BUILT_IN','PRIVATE_GLOBAL','IMPORTED')),
    CONSTRAINT ck_global_skill_status CHECK (status IN ('DRAFT','DISTILLING','WAITING_REVIEW','VALIDATING','VALIDATION_FAILED','VALIDATED','DEPRECATED','ARCHIVED'))
);

CREATE TABLE global_skill_version (
    id UUID PRIMARY KEY,
    global_skill_id UUID NOT NULL REFERENCES global_skill(id) ON DELETE CASCADE,
    version_no INTEGER NOT NULL,
    contract_json JSONB NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    token_estimate INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_global_skill_version UNIQUE (global_skill_id, version_no),
    CONSTRAINT uq_global_skill_version_hash UNIQUE (snapshot_hash)
);

ALTER TABLE global_skill
    ADD CONSTRAINT fk_global_skill_current_version
    FOREIGN KEY (current_version_id) REFERENCES global_skill_version(id) ON DELETE SET NULL;

CREATE TABLE global_skill_atomic_rule (
    id UUID PRIMARY KEY,
    skill_version_id UUID NOT NULL REFERENCES global_skill_version(id) ON DELETE CASCADE,
    dimension VARCHAR(32) NOT NULL,
    statement TEXT NOT NULL,
    rationale TEXT NOT NULL,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence NUMERIC(4,3) NOT NULL,
    applicability JSONB NOT NULL DEFAULT '[]'::jsonb,
    exclusions JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_global_skill_atomic_dimension CHECK (dimension IN ('NARRATIVE','CHARACTER','EXPRESSION','PACING','ANTI_PATTERN','BOUNDARY')),
    CONSTRAINT ck_global_skill_atomic_confidence CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE TABLE skill_forge_run (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    global_skill_id UUID NOT NULL REFERENCES global_skill(id) ON DELETE CASCADE,
    mode VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    source_material TEXT NOT NULL,
    candidate_contract JSONB NOT NULL,
    summary TEXT,
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_skill_forge_run_mode CHECK (mode IN ('MANUAL','DERIVED','MATERIAL')),
    CONSTRAINT ck_skill_forge_run_status CHECK (status IN ('COLLECTING','DISTILLING','WAITING_REVIEW','VALIDATING','VALIDATION_FAILED','VALIDATED','CANCELLED','FAILED'))
);

CREATE TABLE project_skill_binding (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES novel_project(id) ON DELETE CASCADE,
    binding_type VARCHAR(24) NOT NULL,
    global_skill_id UUID NOT NULL REFERENCES global_skill(id),
    global_skill_version_id UUID NOT NULL REFERENCES global_skill_version(id),
    snapshot_hash VARCHAR(64) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_skill_binding_type UNIQUE (project_id, binding_type),
    CONSTRAINT ck_project_skill_binding_type CHECK (binding_type IN ('FOUNDATION'))
);

CREATE INDEX idx_global_skill_visible ON global_skill(scope, status, updated_at DESC);
CREATE INDEX idx_global_skill_owner_updated ON global_skill(owner_id, updated_at DESC);
CREATE INDEX idx_project_skill_binding_project ON project_skill_binding(project_id, binding_type);

INSERT INTO global_skill (id, owner_id, slug, display_name, description, scope, status, contract_json, current_version_id, version, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000011',
    NULL,
    'longform-web-fiction',
    '长篇网文基础',
    '为中文长篇小说提供章节规划、场景写作与基础审查的可验证行为契约。',
    'BUILT_IN',
    'VALIDATED',
    '{"identity":{"displayName":"长篇网文基础","type":"FOUNDATION","version":"1.0.0"},"scope":{"useWhen":["长篇章节规划","场景正文生成","章节局部修订"],"doNotUseWhen":["学术论文","法律文书"]},"inputs":{"required":["authorIntent","chapterOutline","canonContext"]},"outputs":{"planning":"ChapterPlan","writing":"ChapterDraft","review":"ReviewFinding"},"preconditions":["章纲存在","视角人物明确","世界硬规则可用"],"workflow":["读取任务与正典","识别本章信息增量","规划场景","执行写作","自检反模式","输出结果与不确定项"],"constraints":["不覆盖用户确认的项目偏好","不把候选事实直接写入正典"],"antiPatterns":["重复解释同一设定","连续大段上帝视角","用旁白替人物做决定"],"honestyBoundaries":["来源不足时标记不确定","不声称精确复刻特定在世作者","不虚构未提供的正典事实"],"recovery":{"missingContext":"请求补充或降级为候选建议"},"termination":{"success":["输出满足 Schema","无 BLOCKER"]},"provenance":{"generatedBy":"BUILT_IN","reviewedByUser":true},"evaluation":{"minimumScore":85},"recommendation":{"genres":[],"audiences":[],"perspectives":[],"lengthTypes":["LONG_NOVEL"]}}'::jsonb,
    NULL,
    0,
    NOW(), NOW()
);

INSERT INTO global_skill_version (id, global_skill_id, version_no, contract_json, snapshot_hash, status, token_estimate, created_by, created_at)
SELECT '00000000-0000-0000-0000-000000000012', id, 1, contract_json,
    'a6316b183e56fd7689aafc45a5c48c0f36aa4a96ea7e9e8fb52bb7f7ebd707d5', 'VALIDATED', 680, NULL, NOW()
FROM global_skill WHERE id = '00000000-0000-0000-0000-000000000011';

UPDATE global_skill
SET current_version_id = '00000000-0000-0000-0000-000000000012'
WHERE id = '00000000-0000-0000-0000-000000000011';
