export type Uuid = string
export type IsoInstant = string

export interface UserResponse {
  id: Uuid
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  createdAt: IsoInstant
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer' | string
  expiresAt: IsoInstant
  user: UserResponse
}

export interface ProjectResponse {
  id: Uuid
  name: string
  genre: string | null
  customGenre: string | null
  targetAudience: TargetAudience
  narrativePerspective: NarrativePerspective
  lengthType: LengthType
  premise: string | null
  description: string | null
  authorIntent: string | null
  currentFocus: string | null
  worldRules: string[]
  targetWordCount: number | null
  chapterWordTarget: number | null
  archived: boolean
  creationSource?: 'MANUAL' | 'TXT_IMPORT'
  reconstructionStatus?: 'NOT_ANALYZED' | 'ANALYZING' | 'PARTIAL' | 'REVIEW_REQUIRED' | 'READY'
  version: number
  createdAt: IsoInstant
  updatedAt: IsoInstant
}

export interface CreateProjectRequest {
  name: string
  genre: string
  customGenre: string | null
  targetAudience: TargetAudience
  narrativePerspective: NarrativePerspective
  lengthType: LengthType
  premise: string
  description?: string | null
  authorIntent?: string | null
  currentFocus?: string | null
  worldRules: string[]
  targetWordCount: number | null
  chapterWordTarget: number | null
  baseSkillVersionId?: Uuid | null
}

export interface UpdateProjectRequest {
  name: string
  genre: string
  customGenre: string | null
  targetAudience: TargetAudience
  narrativePerspective: NarrativePerspective
  lengthType: LengthType
  premise: string
  description: string | null
  authorIntent: string | null
  currentFocus: string | null
  worldRules: string[]
  targetWordCount: number | null
  chapterWordTarget: number | null
  archived: boolean
  expectedVersion: number
}

export type TargetAudience = 'MALE' | 'FEMALE' | 'GENERAL'
export type NarrativePerspective = 'FIRST_PERSON' | 'THIRD_PERSON'
export type LengthType = 'SHORT_NOVEL' | 'LONG_NOVEL'

export interface SnapshotRequest {
  expectedVersion: number
}

export interface SnapshotResponse {
  id: Uuid
  projectId: Uuid
  projectVersion: number
  createdAt: IsoInstant
}

export type CanonStatus = 'DRAFT' | 'CANDIDATE' | 'CONFIRMED' | 'CONFLICTED' | 'DEPRECATED'

export interface AssetVersionResponse {
  id: Uuid
  versionNo: number
  name: string
  content: string
  changeSummary: string | null
  createdAt: IsoInstant
}

export interface AssetResponse {
  id: Uuid
  projectId: Uuid
  assetType: string
  name: string
  status: CanonStatus
  currentVersionNo: number
  confirmedVersionNo: number | null
  version: number
  createdAt: IsoInstant
  updatedAt: IsoInstant
  currentVersion: AssetVersionResponse
}

export interface CreateAssetRequest {
  assetType: string
  name: string
  content: string
  changeSummary?: string | null
}

export interface UpdateAssetRequest {
  name: string
  content: string
  changeSummary: string | null
  expectedVersion: number
}

export interface AssetTransitionRequest {
  expectedVersion: number
}

export type LifeStatus = 'UNKNOWN' | 'ALIVE' | 'DEAD'
export type CharacterImportance = 'PROTAGONIST' | 'MAJOR' | 'SUPPORTING' | 'MINOR' | 'MENTION_ONLY'
export type CharacterLifecycleStatus = 'CANDIDATE' | 'ACTIVE' | 'INACTIVE' | 'DECEASED' | 'MISSING' | 'LEFT_STORY' | 'MERGED' | 'REJECTED' | 'ARCHIVED' | 'PURGED'
export interface CharacterStateInput { lifeStatus: LifeStatus | null; currentLocation: string | null; physicalCondition: string | null; emotionalState: string | null; abilities: string | null; inventoryNotes: string | null; notes: string | null }
export interface CharacterStateResponse extends CharacterStateInput { id: Uuid; projectId: Uuid; characterId: Uuid; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface CharacterResponse { id: Uuid; projectId: Uuid; name: string; aliases: string | null; role: string | null; description: string | null; personality: string | null; background: string | null; goals: string | null; appearance: string | null; notes: string | null; archived: boolean; importance: CharacterImportance; lifecycleStatus: CharacterLifecycleStatus; mergedInto: Uuid | null; retrievalEligible: boolean; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; state: CharacterStateResponse }
export interface CreateCharacterRequest { name: string; aliases?: string | null; role?: string | null; description?: string | null; personality?: string | null; background?: string | null; goals?: string | null; appearance?: string | null; notes?: string | null; importance?: CharacterImportance | null; state?: CharacterStateInput | null }
export interface UpdateCharacterRequest extends Omit<CreateCharacterRequest, 'state'> { archived: boolean; expectedVersion: number }
export interface UpdateCharacterStateRequest extends CharacterStateInput { expectedVersion: number }

export type WorldbookScope = 'PROJECT' | 'CHAPTER' | 'CHARACTER'
export type WorldbookVisibility = 'ALL' | 'AUTHOR_ONLY' | 'CHARACTER_ONLY'
export type EmbeddingStatus = 'NOT_REQUESTED' | 'AVAILABLE' | 'UNAVAILABLE'
export interface WorldbookEntryResponse { id: Uuid; projectId: Uuid; title: string; content: string; active: boolean; constantEnabled: boolean; vectorEnabled: boolean; keywords: string[]; priority: number; scopeType: WorldbookScope; scopeRefId: Uuid | null; visibilityType: WorldbookVisibility; visibilityRefId: Uuid | null; embeddingStatus: EmbeddingStatus; embeddingModel: string | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface CreateWorldbookEntryRequest { title: string; content: string; active: boolean; constantEnabled: boolean; vectorEnabled: boolean; keywords: string[]; priority: number; scopeType: WorldbookScope; scopeRefId: Uuid | null; visibilityType: WorldbookVisibility; visibilityRefId: Uuid | null }
export interface UpdateWorldbookEntryRequest extends CreateWorldbookEntryRequest { expectedVersion: number }

export type OutlineNodeType = 'MASTER' | 'VOLUME' | 'ARC' | 'CHAPTER'
export interface OutlineResponse { id: Uuid; projectId: Uuid; parentId: Uuid | null; nodeType: OutlineNodeType; title: string; summary: string | null; objective: string | null; sequenceNo: number; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface CreateOutlineRequest { parentId: Uuid | null; nodeType: OutlineNodeType; title: string; summary: string | null; objective: string | null; sequenceNo: number }
export interface UpdateOutlineRequest { title: string; summary: string | null; objective: string | null; sequenceNo: number; expectedVersion: number }

export type ChapterStatus = 'DRAFT' | 'GENERATING' | 'REVIEW_REQUIRED' | 'WAITING_APPROVAL' | 'CONFIRMED' | 'ARCHIVED'
export interface ChapterVersionResponse { id: Uuid; chapterId: Uuid; versionNo: number; title: string; content: string; summary: string | null; changeSummary: string | null; restoredFromVersionNo: number | null; createdAt: IsoInstant }
export interface ChapterResponse { id: Uuid; projectId: Uuid; outlineNodeId: Uuid | null; chapterNo: number; title: string; outline: string | null; status: ChapterStatus; currentVersionNo: number; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; currentVersion: ChapterVersionResponse | null }
export interface CreateChapterRequest { chapterNo: number; title: string; outlineNodeId: Uuid | null; outline: string | null }
export interface UpdateChapterOutlineRequest { outlineNodeId: Uuid | null; title: string; outline: string | null; expectedVersion: number }
export interface CreateChapterVersionRequest { title: string; content: string; summary: string | null; changeSummary: string | null; expectedVersion: number }
export interface RestoreChapterVersionRequest { changeSummary: string | null; expectedVersion: number }

export type SkillScope = 'BASE' | 'PROJECT' | 'CHAPTER'
export interface SkillResponse { id: Uuid; projectId: Uuid; name: string; description: string | null; rules: Record<string, string>; enabled: boolean; scope: SkillScope; chapterId: Uuid | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface CreateSkillRequest { name: string; description: string | null; rules: Record<string, string>; enabled: boolean; scope: SkillScope; chapterId: Uuid | null }
export interface UpdateSkillRequest extends CreateSkillRequest { expectedVersion: number }
export interface EffectiveRuleResponse { key: string; value: string; scope: SkillScope; skillId: Uuid; skillName: string }
export interface SkillConflictResponse { scope: SkillScope; key: string; values: string[]; skillIds: Uuid[] }
export interface SkillCompositionResponse { resolved: boolean; effectiveRules: Record<string, EffectiveRuleResponse>; conflicts: SkillConflictResponse[] }

export type GlobalSkillScope = 'BUILT_IN' | 'PRIVATE_GLOBAL' | 'IMPORTED'
export type GlobalSkillStatus = 'DRAFT' | 'DISTILLING' | 'WAITING_REVIEW' | 'VALIDATING' | 'VALIDATION_FAILED' | 'VALIDATED' | 'DEPRECATED' | 'ARCHIVED'
export interface GlobalSkillResponse { id: Uuid; slug: string; displayName: string; description: string; scope: GlobalSkillScope; status: GlobalSkillStatus; contract: Record<string, unknown>; currentVersionId: Uuid | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface GlobalSkillVersionResponse { id: Uuid; globalSkillId: Uuid; versionNo: number; contract: Record<string, unknown>; snapshotHash: string; status: GlobalSkillStatus; tokenEstimate: number; createdAt: IsoInstant }
export interface CreateGlobalSkillRequest { slug: string; displayName: string; description: string; contract: Record<string, unknown> }
export type ForgeSkillType = 'FOUNDATION' | 'GENRE' | 'TECHNIQUE' | 'REVIEW'
export type ForgeMaterialType = 'PROSE' | 'DIALOGUE' | 'CHARACTER' | 'DESCRIPTION' | 'OUTLINE' | 'WRITING_RULES' | 'OTHER'
export type ForgeRunStatus = 'CREATED' | 'SOURCE_READY' | 'PREPROCESSING' | 'EXTRACTING' | 'CROSS_VALIDATING' | 'WAITING_CONFLICT_RESOLUTION' | 'BUILDING_CONTRACT' | 'WAITING_REVIEW' | 'VALIDATING' | 'VALIDATED' | 'VALIDATION_FAILED' | 'FAILED' | 'CANCELLED'
export interface CreateForgeRunRequest { slug: string; displayName: string; skillType: ForgeSkillType; materialTag: ForgeMaterialType; genre: string | null; sourceProjectId: Uuid | null; focus: string | null; materialDescription: string | null; excludeCharacterNames: boolean; excludeLocations: boolean; excludePlotFacts: boolean; reusableMethodsOnly: boolean; ownershipConfirmed: boolean; ownershipStatement: string }
export interface ManualTextSourceRequest { title: string; content: string; materialType: ForgeMaterialType; ownershipConfirmed: boolean }
export interface ForgeSourceResponse { id: Uuid; sourceType: 'TXT' | 'MANUAL_TEXT'; title: string; materialType: ForgeMaterialType; originalFilename: string | null; detectedEncoding: string; contentHash: string; characterCount: number; paragraphCount: number; sourceOrder: number; createdAt: IsoInstant }
export type AtomicRuleStatus = 'CANDIDATE' | 'ACCEPTED' | 'REJECTED' | 'CONFLICT'
export interface AtomicRuleEvidence { sourceId: Uuid; paragraphKey: string; excerptHash: string; excerpt: string }
export interface AtomicSkillRuleResponse { id: Uuid; dimension: 'NARRATIVE' | 'CHARACTER' | 'EXPRESSION' | 'PACING' | 'ANTI_PATTERN' | 'BOUNDARY'; statement: string; rationale: string; scope: 'LOCAL_PATTERN' | 'REPEATED_PATTERN' | 'EXPLICIT_USER_RULE'; evidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH'; confidence: number; evidence: AtomicRuleEvidence[]; status: AtomicRuleStatus; userModified: boolean; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface ForgeStepResponse { id: Uuid; stepName: string; status: string; summary: string; createdAt: IsoInstant }
export interface SkillTestCaseResponse {
  id: Uuid
  caseType: 'TYPICAL' | 'CONFLICT' | 'EDGE' | 'OUT_OF_EVIDENCE' | 'OVERFITTING' | 'HONESTY_BOUNDARY'
  title: string
  prompt: string
  expectedAssertions: unknown
  latestResult: null | { runStatus: 'RUNNING' | 'PASSED' | 'FAILED'; score: number; passed: boolean | null; finding: string | null }
}
export interface ContractValidationResponse { valid: boolean; score: number; missingSections: string[]; version: GlobalSkillVersionResponse | null }
export interface ForgeRunResponse { id: Uuid; globalSkillId: Uuid; mode: string; status: ForgeRunStatus; skillType: ForgeSkillType; materialTag: ForgeMaterialType; genre: string | null; sourceProjectId: Uuid | null; learningFocus: string | null; materialDescription: string | null; excludeCharacterNames: boolean; excludeLocations: boolean; excludePlotFacts: boolean; reusableMethodsOnly: boolean; ownershipConfirmedAt: IsoInstant; candidateContract: Record<string, unknown>; summary: string | null; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface FoundationBindingResponse { id: Uuid; projectId: Uuid; bindingType: 'FOUNDATION'; globalSkillId: Uuid; globalSkillVersionId: Uuid; skillName: string; snapshotHash: string; enabled: boolean; createdAt: IsoInstant }

export interface BudgetResponse { projectId: Uuid; taskTokenLimit: number; userDailyCostLimit: number; projectCostLimit: number; writerOutputTokenLimit: number; plannerReasoningTokenLimit: number; version: number }
export interface UpdateBudgetRequest { expectedVersion: number; taskTokenLimit: number; userDailyCostLimit: number; projectCostLimit: number; writerOutputTokenLimit: number; plannerReasoningTokenLimit: number }
export type UsageStatus = 'SUCCEEDED' | 'FAILED'
export interface UsageResponse { id: Uuid; projectId: Uuid; agent: string; model: string; requestId: string | null; status: UsageStatus; promptTokens: number; completionTokens: number; reasoningTokens: number; promptCacheHitTokens: number; promptCacheMissTokens: number; attempts: number; durationMillis: number; requestedAt: IsoInstant; pricingRuleId: Uuid | null; pricingRuleVersion: string | null; estimatedCost: number | null; actualCost: number | null; currency: string | null }
export interface CostSummaryResponse { projectId: Uuid; estimatedCost: number; actualCost: number; unpricedRequests: number; requests: number }
export interface PricingRuleResponse { id: Uuid; ruleVersion: string; model: string; currency: string; inputPerMillion: number; outputPerMillion: number; reasoningPerMillion: number; cacheHitPerMillion: number; cacheMissPerMillion: number; effectiveFrom: IsoInstant; effectiveTo: IsoInstant | null }
export interface ModelConfigurationResponse { agent: string; model: string; thinking: boolean; reasoningEffort: string | null; temperature: number | null; jsonOutput: boolean; stream: boolean; maxOutputTokens: number; maxAttempts: number; ignoredParameters: string[] }

export type WorkflowStatus = 'CREATED' | 'PREFLIGHT' | 'CONTEXT_READY' | 'PLANNING' | 'PLAN_READY' | 'WRITING' | 'TEXT_READY' | 'EXTRACTING' | 'VALIDATING' | 'REVIEWING' | 'WAITING_APPROVAL' | 'REVISION_REQUIRED' | 'COMMITTING' | 'COMPLETED' | 'BLOCKED' | 'FAILED' | 'CANCELLED' | 'ROLLED_BACK'
export type WorkflowStepStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export interface StartWorkflowRequest { viewpointCharacterId: Uuid; instruction: string }
export interface ContextPacketResponse { id: Uuid; tokenEstimate: number; estimatedCost: number; expiresAt: IsoInstant; stale: boolean; createdAt: IsoInstant }
export interface WorkflowStepResponse { id: Uuid; stepName: string; status: WorkflowStepStatus; attempt: number; errorCode: string | null; errorMessage: string | null; startedAt: IsoInstant | null; finishedAt: IsoInstant | null }
export type FactStatus = 'CANDIDATE' | 'ACCEPTED' | 'REJECTED'
export type ReviewSource = 'JAVA' | 'LLM'
export type ReviewSeverity = 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'BLOCKER'
export type ItemStatus = 'ACTIVE' | 'DAMAGED' | 'DESTROYED' | 'LOST'
export type KnowledgeCertainty = 'SUSPECTED' | 'CONFIRMED'
export interface StoryFactResponse { id: Uuid; candidateIndex: number; factKey: string; content: string; evidence: string; paragraphKey: string; status: FactStatus; createdAt: IsoInstant }
export interface ReviewIssueResponse { id: Uuid; source: ReviewSource; category: string; severity: ReviewSeverity; message: string; evidence: string; historicalEvidence: string | null; suggestion: string | null; blocking: boolean; resolved: boolean; createdAt: IsoInstant }
export interface WorkflowResponse { id: Uuid; projectId: Uuid; chapterId: Uuid; viewpointCharacterId: Uuid; status: WorkflowStatus; draftContent: string | null; plan: Record<string, unknown>; extraction: Record<string, unknown>; review: Record<string, unknown>; cancelRequested: boolean; recoveryCount: number; revisionCount: number; committedVersionNo: number | null; approvedBy: Uuid | null; approvedAt: IsoInstant | null; failureCode: string | null; failureMessage: string | null; heartbeatAt: IsoInstant | null; startedAt: IsoInstant | null; finishedAt: IsoInstant | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; contextPacket: ContextPacketResponse | null; steps: WorkflowStepResponse[]; candidateFacts: StoryFactResponse[]; reviewIssues: ReviewIssueResponse[] }
export interface WorkflowEventResponse { eventId: number; runId: Uuid; type: string; step: string; timestamp: IsoInstant; payload: Record<string, unknown> }
export interface RevisionRequest { revisedDraft: string }
export interface CharacterStateChangeRequest { characterId: Uuid; lifeStatus: LifeStatus; currentLocation: string | null; physicalCondition: string | null; emotionalState: string | null; abilities: string | null; inventoryNotes: string | null; notes: string | null; expectedVersion: number; evidence: string }
export interface ItemChangeRequest { itemKey: string; itemName: string; fromOwnerCharacterId: Uuid | null; toOwnerCharacterId: Uuid | null; status: ItemStatus; evidence: string }
export interface TimelineEventRequest { participantIds: Uuid[]; knownByIds: Uuid[]; location: string | null; storyTime: string | null; action: string; result: string; importance: number; evidence: string }
export interface KnowledgeChangeRequest { characterId: Uuid; factKey: string; content: string; certainty: KnowledgeCertainty; sourceEventId: Uuid | null; evidence: string }
export interface ApproveWorkflowRequest { expectedVersion: number; changeSummary: string | null; acceptedFactIndexes: number[]; characterStateChanges: CharacterStateChangeRequest[]; itemChanges: ItemChangeRequest[]; timelineEvents: TimelineEventRequest[]; knowledgeChanges: KnowledgeChangeRequest[] }

export type ImportStatus = 'UPLOADED' | 'SPLIT_REVIEW' | 'EXTRACTING' | 'CANDIDATE_REVIEW' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export interface ImportChapterResponse { id: Uuid; sequenceNo: number; title: string; content: string; included: boolean; createdChapterId: Uuid | null }
export interface ImportCandidateResponse { id: Uuid; candidateType: string; content: string; sourceChapterNo: number | null; decision: 'PENDING' | 'ACCEPTED' | 'REJECTED' }
export interface ImportResponse { id: Uuid; projectId: Uuid; fileName: string; mediaType: string | null; status: ImportStatus; errorMessage: string | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; chapters: ImportChapterResponse[]; candidates: ImportCandidateResponse[] }
export interface ReplaceImportChaptersRequest { expectedVersion: number; chapters: Array<{ title: string; content: string; included: boolean }> }
export interface DecideImportCandidatesRequest { decisions: Array<{ candidateId: Uuid; accepted: boolean }> }
export interface AliasMergeRequest { sourceName: string; targetCharacterId: Uuid }

export type TxtImportStatus = 'UPLOADED' | 'DECODING' | 'PARSED' | 'WAITING_CONFIRMATION' | 'IMPORTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type BookAnalysisStatus = 'NOT_REQUESTED' | 'QUEUED' | 'ANALYZING' | 'WAITING_REVIEW' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export interface TxtImportChapterResponse { id: Uuid; sequenceNo: number; title: string; startOffset: number; endOffset: number; characterCount: number; paragraphCount: number; included: boolean }
export interface TxtImportJobResponse {
  id: Uuid
  sourceId: Uuid
  projectId: Uuid | null
  status: TxtImportStatus
  analysisStatus: BookAnalysisStatus
  filename: string
  sizeBytes: number
  sha256: string
  detectedEncoding: string
  selectedEncoding: string
  encodingConfident: boolean
  totalCharacters: number
  totalChapters: number
  processedChapters: number
  headingCount: number
  analysisProcessedChunks: number
  parserVersion: string
  errorCode: string | null
  errorMessage: string | null
  duplicateImportId: Uuid | null
  duplicateProjectId: Uuid | null
  version: number
  expiresAt: IsoInstant
  createdAt: IsoInstant
  updatedAt: IsoInstant
  chapters: TxtImportChapterResponse[]
}
export type TxtImportProjectInput = CreateProjectRequest
export interface BookAnalysisCandidateResponse { id: Uuid; chapterId: Uuid | null; chunkIndex: number; candidateType: 'CHARACTER' | 'WORLDBOOK' | 'OUTLINE' | 'EVENT' | 'SKILL'; content: string; status: 'CANDIDATE' | 'ACCEPTED' | 'REJECTED'; createdAt: IsoInstant }
export interface BookAnalysisResponse { importId: Uuid; projectId: Uuid; status: BookAnalysisStatus; processedChunks: number; errorCode: string | null; errorMessage: string | null; candidates: BookAnalysisCandidateResponse[] }
export interface BookAnalysisRequest { extractCharacters: boolean; extractWorldbook: boolean; extractOutline: boolean; extractEvents: boolean; extractSkills: boolean }

export type ReconstructionMode = 'QUICK' | 'STANDARD' | 'DEEP'
export type ReconstructionStatus =
  | 'NOT_ANALYZED' | 'QUEUED' | 'PREPROCESSING' | 'CHAPTER_ANALYSIS'
  | 'VOLUME_AGGREGATION' | 'ENTITY_RESOLUTION' | 'GLOBAL_RECONSTRUCTION'
  | 'FORESHADOW_ANALYSIS' | 'SKILL_DISTILLATION' | 'VALIDATING'
  | 'WAITING_REVIEW' | 'APPLYING' | 'COMPLETED' | 'PAUSED'
  | 'PAUSED_BUDGET' | 'PARTIAL' | 'CANCELLED' | 'FAILED'
export interface ReconstructionOptions {
  mode: ReconstructionMode
  includeSkillDistillation: boolean
  includeForeshadowing: boolean
}
export interface ReconstructionEstimate extends ReconstructionOptions {
  chapters: number
  chunks: number
  estimatedCalls: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  estimatedCostMin: number | null
  estimatedCostMax: number | null
  currency: string | null
  model: string
  unpriced: boolean
}
export interface ReconstructionJob {
  id: Uuid | null
  projectId: Uuid
  mode: ReconstructionMode
  status: ReconstructionStatus
  currentStep: string
  totalChapters: number
  totalChunks: number
  processedChunks: number
  failedChapters: number
  progress: number
  estimatedCalls: number
  estimatedInputTokens: number
  estimatedOutputTokens: number
  estimatedCostMin: number | null
  estimatedCostMax: number | null
  currency: string | null
  maxBudget: number | null
  actualInputTokens: number
  actualOutputTokens: number
  actualReasoningTokens: number
  actualCost: number
  retryCount: number
  candidateCount: number
  pendingCandidates: number
  conflicts: number
  acceptedCandidates: number
  rejectedCandidates: number
  errorCode: string | null
  errorMessage: string | null
  startedAt: IsoInstant | null
  completedAt: IsoInstant | null
}
export interface ReconstructionCandidate {
  id: Uuid
  chapterId: Uuid | null
  candidateType: string
  content: string
  status: 'CANDIDATE' | 'ACCEPTED' | 'REJECTED' | 'REVOKED' | 'APPLIED' | 'CONFLICT'
  confidence: 'HIGH' | 'MEDIUM' | 'LOW'
  inferenceType: 'DIRECT_FACT' | 'MODEL_INFERENCE' | 'USER_CONFIRMED'
  evidenceCount: number
  sourceCoverage: number
  sourceAnchors: string
  safeToApply: boolean
  suggestedAction: 'CREATE_CHARACTER' | 'UPDATE_PROFILE' | 'APPEND_STATE' | 'APPEND_KNOWLEDGE' | 'APPEND_RELATIONSHIP' | 'APPEND_EVENT' | 'MERGE_ALIAS' | 'UPDATE_WORLD_ASSET' | 'CREATE_FORESHADOW' | 'ADVANCE_FORESHADOW' | 'RESOLVE_FORESHADOW' | 'UPDATE_ROLLING_OUTLINE' | 'IGNORE' | 'NEEDS_REVIEW'
  targetEntityId: Uuid | null
  subjectName: string | null
  policyReason: string | null
  characterImportance: CharacterImportance | null
  retrievalEligible: boolean
  revokedAt: IsoInstant | null
  revocationReason: string | null
  createdAt: IsoInstant
}

export type ForeshadowStatus = 'CANDIDATE' | 'PLANTED' | 'DEVELOPING' | 'DUE' | 'RESOLVED' | 'PARTIALLY_RESOLVED' | 'ABANDONED' | 'REJECTED'
export interface ForeshadowResponse { id: Uuid; projectId: Uuid; title: string; description: string | null; status: ForeshadowStatus; plantedChapterId: Uuid | null; targetChapterNo: number | null; resolvedChapterId: Uuid | null; notes: string | null; version: number; createdAt: IsoInstant; updatedAt: IsoInstant }
export interface ForeshadowInput { title: string; description: string | null; plantedChapterId: Uuid | null; targetChapterNo: number | null; notes: string | null }

export interface ImpactReportResponse { id: Uuid; projectId: Uuid; chapterId: Uuid; status: 'READY' | 'FAILED'; summary: string | null; affected: Record<string, unknown>; createdAt: IsoInstant }
export interface RollingOutlineResponse { projectId: Uuid; currentChapterNo: number; windowSize: number; summary: string | null; goals: string[]; risks: string[]; baseChapterId: Uuid | null; fromChapterNo: number | null; toChapterNo: number | null; openThreads: string[]; currentLocations: string[]; activeItems: string[]; activeForeshadow: string[]; nextConstraints: string[]; stale: boolean; version: number; updatedAt: IsoInstant }
export interface PutRollingOutlineRequest { expectedVersion: number; currentChapterNo: number; windowSize: number; summary: string | null; goals: string[]; risks: string[] }
export interface AdvanceRollingOutlineRequest { expectedVersion: number; summary: string | null; goals: string[]; risks: string[] }

export type ChapterBatchStatus = 'QUEUED' | 'RUNNING' | 'PAUSED' | 'WAITING_GATE' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export interface ChapterBatchItemResponse { id: Uuid; sequenceNo: number; chapterId: Uuid; workflowRunId: Uuid | null; status: string }
export interface ChapterBatchResponse { id: Uuid; projectId: Uuid; viewpointCharacterId: Uuid; instruction: string; status: ChapterBatchStatus; currentIndex: number; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; items: ChapterBatchItemResponse[] }
export interface CreateChapterBatchRequest { viewpointCharacterId: Uuid; instruction: string; chapterIds: Uuid[]; gatedChapterIds: Uuid[] }
export interface StoryGateResponse { id: Uuid; projectId: Uuid; batchId: Uuid; chapterId: Uuid; workflowRunId: Uuid | null; gateType: string; title: string; rationale: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; decidedBy: Uuid | null; decidedAt: IsoInstant | null; createdAt: IsoInstant }

export interface ChapterBranchVersionResponse { id: Uuid; versionNo: number; title: string; content: string; changeSummary: string | null; createdAt: IsoInstant }
export interface ChapterBranchResponse { id: Uuid; projectId: Uuid; chapterId: Uuid; name: string; description: string | null; status: 'ACTIVE' | 'ARCHIVED'; promoted: boolean; version: number; createdAt: IsoInstant; updatedAt: IsoInstant; versions: ChapterBranchVersionResponse[] }
export interface CreateChapterBranchRequest { name: string; description: string | null; title: string | null; content: string | null; changeSummary: string | null }
export interface CreateChapterBranchVersionRequest { expectedVersion: number; title: string; content: string; changeSummary: string | null }
export interface LocalRevisionRequest { expectedVersion: number; startOffset: number; endOffset: number; replacement: string; reason: string }
export interface ModelAttemptResponse { id: Uuid; agent: string; provider: string; model: string; attempts: number; status: UsageStatus; durationMillis: number; requestedAt: IsoInstant }
export interface ModelHealthResponse { provider: string; status: 'AVAILABLE' | 'NOT_CONFIGURED'; failuresLastFiveMinutes: number; fallbackEnabled: boolean; checkedAt: IsoInstant }
