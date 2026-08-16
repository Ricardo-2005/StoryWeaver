ALTER TABLE foreshadow
    ADD COLUMN source_candidate_id UUID REFERENCES project_reconstruction_candidate(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_foreshadow_source_candidate
    ON foreshadow(source_candidate_id)
    WHERE source_candidate_id IS NOT NULL;

INSERT INTO foreshadow(
    id, project_id, title, description, status, planted_chapter_id, target_chapter_no,
    resolved_chapter_id, notes, version, created_at, updated_at, confidence, evidence,
    retrieval_eligible, source_candidate_id)
SELECT
    gen_random_uuid(),
    candidate.project_id,
    LEFT(candidate.content, 160),
    candidate.content,
    'CANDIDATE',
    candidate.chapter_id,
    NULL,
    NULL,
    'TXT AI 拆书自动登记 · ' || candidate.confidence || ' · Evidence ' || candidate.evidence_count,
    0,
    now(),
    now(),
    candidate.confidence,
    candidate.source_anchors,
    TRUE,
    candidate.id
FROM project_reconstruction_candidate candidate
WHERE candidate.candidate_type = 'FORESHADOW'
  AND candidate.status IN ('CANDIDATE', 'ACCEPTED')
  AND candidate.suggested_action = 'CREATE_FORESHADOW'
ON CONFLICT DO NOTHING;

UPDATE project_reconstruction_candidate candidate
SET target_entity_id = foreshadow.id,
    status = 'APPLIED',
    applied_at = COALESCE(candidate.applied_at, now()),
    retrieval_eligible = TRUE,
    revoked_at = NULL,
    revoked_by = NULL,
    revocation_reason = NULL,
    policy_reason = 'Automatically registered in the foreshadow ledger; lifecycle remains unconfirmed',
    updated_at = now()
FROM foreshadow
WHERE foreshadow.source_candidate_id = candidate.id
  AND candidate.candidate_type = 'FORESHADOW'
  AND candidate.status IN ('CANDIDATE', 'ACCEPTED', 'REVOKED');
