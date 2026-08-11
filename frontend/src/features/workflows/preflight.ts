import type {
  BudgetResponse,
  ChapterResponse,
  CharacterResponse,
  ModelConfigurationResponse,
  ProjectResponse,
  SkillCompositionResponse,
  CostSummaryResponse,
} from '@/api/types'

export type PreflightCheckStatus = 'pass' | 'blocker' | 'server'
export interface PreflightCheck { code: string; label: string; detail: string; status: PreflightCheckStatus }

export interface PreflightInput {
  project: ProjectResponse
  chapter: ChapterResponse
  chapters: ChapterResponse[]
  characters: CharacterResponse[]
  viewpointCharacterId: string
  skills: SkillCompositionResponse
  budget: BudgetResponse
  costs: CostSummaryResponse
  models: ModelConfigurationResponse[]
  hasUnsavedDraft: boolean
}

function outcome(condition: boolean): PreflightCheckStatus {
  return condition ? 'pass' : 'blocker'
}

export function projectedWorkflowTokens(models: ModelConfigurationResponse[]): number {
  return models.reduce((total, model) => total + model.maxOutputTokens, 0)
}

export function buildPreflightChecks(input: PreflightInput): PreflightCheck[] {
  const previous = input.chapter.chapterNo <= 1
    ? undefined
    : input.chapters.find((chapter) => chapter.chapterNo === input.chapter.chapterNo - 1)
  const viewpoint = input.characters.find((character) => character.id === input.viewpointCharacterId && !character.archived)
  const writer = input.models.find((model) => model.agent === 'WRITER')
  const planner = input.models.find((model) => model.agent === 'PLANNER')
  const projectedTokens = projectedWorkflowTokens(input.models)

  return [
    { code: 'project_active', label: '项目可写', detail: input.project.archived ? '项目已归档' : '项目未归档', status: outcome(!input.project.archived) },
    { code: 'author_intent', label: '作者意图', detail: input.project.authorIntent?.trim() ? '已填写' : '项目缺少作者意图', status: outcome(Boolean(input.project.authorIntent?.trim())) },
    { code: 'chapter_outline', label: '章纲', detail: input.chapter.outline?.trim() ? '已填写' : '章节缺少已确认章纲', status: outcome(Boolean(input.chapter.outline?.trim())) },
    { code: 'viewpoint', label: '视角人物', detail: viewpoint ? viewpoint.name : '请选择有效且未归档的人物', status: outcome(Boolean(viewpoint)) },
    { code: 'previous_chapter', label: '上一章', detail: input.chapter.chapterNo <= 1 ? '首章无需上一章' : previous?.currentVersionNo ? `第 ${previous.chapterNo} 章已有正式版本` : '上一章缺失或没有正式版本', status: outcome(input.chapter.chapterNo <= 1 || Boolean(previous?.currentVersionNo)) },
    { code: 'skills', label: 'Skill 冲突', detail: input.skills.resolved ? `${Object.keys(input.skills.effectiveRules).length} 条有效规则` : `${input.skills.conflicts.length} 个同层冲突`, status: outcome(input.skills.resolved) },
    { code: 'task_budget', label: '工作流 Token 预算', detail: `${projectedTokens.toLocaleString()} / ${input.budget.taskTokenLimit.toLocaleString()}`, status: outcome(projectedTokens <= input.budget.taskTokenLimit) },
    { code: 'writer_budget', label: 'Writer 输出预算', detail: `${(writer?.maxOutputTokens ?? 0).toLocaleString()} / ${input.budget.writerOutputTokenLimit.toLocaleString()}`, status: outcome(Boolean(writer) && writer!.maxOutputTokens <= input.budget.writerOutputTokenLimit) },
    { code: 'planner_budget', label: 'Planner 推理预算', detail: `${(planner?.maxOutputTokens ?? 0).toLocaleString()} / ${input.budget.plannerReasoningTokenLimit.toLocaleString()}`, status: outcome(Boolean(planner) && planner!.maxOutputTokens <= input.budget.plannerReasoningTokenLimit) },
    { code: 'project_cost_budget', label: '项目费用预算', detail: `${input.costs.actualCost.toFixed(6)} / ${input.budget.projectCostLimit.toFixed(6)}`, status: outcome(input.costs.actualCost < input.budget.projectCostLimit) },
    { code: 'local_draft', label: '本地章节草稿', detail: input.hasUnsavedDraft ? '请先保存正式版本或舍弃本地修改' : '没有未提交的编辑器修改', status: outcome(!input.hasUnsavedDraft) },
    { code: 'server_only', label: '服务端最终预算校验', detail: 'DeepSeek Key 和跨项目用户日成本只能由后端启动预检确认', status: 'server' },
  ]
}
