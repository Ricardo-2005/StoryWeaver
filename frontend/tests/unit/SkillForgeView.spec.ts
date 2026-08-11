import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { globalSkillsApi } from '@/api/endpoints/globalSkills'
import type { AtomicSkillRuleResponse, ForgeRunResponse } from '@/api/types'
import SkillForgeView from '@/pages/SkillForgeView.vue'

vi.mock('@/api/endpoints/globalSkills', () => ({
  globalSkillsApi: {
    createForgeRun: vi.fn(),
    addTxtSources: vi.fn(),
    addManualSource: vi.fn(),
    startDistillation: vi.fn(),
    forgeRules: vi.fn(),
    reviewForgeRule: vi.fn(),
    generateForgeContract: vi.fn(),
    validateForge: vi.fn(),
    getForgeRun: vi.fn(),
  },
}))

vi.mock('@/api/endpoints/projects', () => ({
  projectsApi: { list: vi.fn().mockResolvedValue([]) },
}))

const run: ForgeRunResponse = {
  id: 'run-1',
  globalSkillId: 'skill-1',
  mode: 'TEXT_SOURCES',
  status: 'WAITING_REVIEW',
  skillType: 'FOUNDATION',
  materialTag: 'PROSE',
  genre: null,
  sourceProjectId: null,
  learningFocus: null,
  materialDescription: null,
  excludeCharacterNames: true,
  excludeLocations: true,
  excludePlotFacts: true,
  reusableMethodsOnly: true,
  ownershipConfirmedAt: '2026-08-07T00:00:00Z',
  candidateContract: {},
  summary: '等待审查',
  createdAt: '2026-08-07T00:00:00Z',
  updatedAt: '2026-08-07T00:00:00Z',
}

const rule: AtomicSkillRuleResponse = {
  id: 'rule-1',
  dimension: 'EXPRESSION',
  statement: '使用短段落推进局部节奏。',
  rationale: '来自段落长度统计。',
  scope: 'LOCAL_PATTERN',
  evidenceLevel: 'LOW',
  confidence: 0.55,
  evidence: [{ sourceId: 'source-1', paragraphKey: 'p-0001-deadbeef', excerptHash: 'a'.repeat(64), excerpt: '他推开门。屋内的人都停下了。' }],
  status: 'CANDIDATE',
  userModified: false,
  createdAt: '2026-08-07T00:00:00Z',
  updatedAt: '2026-08-07T00:00:00Z',
}

function wrapper() {
  return mount(SkillForgeView, {
    global: {
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

async function prepareManualForm(view: ReturnType<typeof wrapper>) {
  const textInputs = view.findAll('input[type="text"], input:not([type])')
  await textInputs[0]!.setValue('My Skill')
  await textInputs[1]!.setValue('my-skill')
  const selects = view.findAll('select')
  await selects[0]!.setValue('FOUNDATION')
  await selects[1]!.setValue('PROSE')
  await view.get('[role="tab"]:nth-child(2)').trigger('click')
  const textareas = view.findAll('textarea')
  await textareas.at(-1)!.setValue('“你来了。”\n他把门推开。于是争执重新开始。\n'.repeat(20))
}

describe('SkillForgeView', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.stubGlobal('crypto', { randomUUID: () => 'random-id' })
    vi.mocked(globalSkillsApi.createForgeRun).mockResolvedValue({ ...run, status: 'CREATED' })
    vi.mocked(globalSkillsApi.addManualSource).mockResolvedValue({
      id: 'source-1', sourceType: 'MANUAL_TEXT', title: '我的手写文本', materialType: 'PROSE', originalFilename: null,
      detectedEncoding: 'UTF-8', contentHash: 'b'.repeat(64), characterCount: 420, paragraphCount: 20, sourceOrder: 1,
      createdAt: '2026-08-07T00:00:00Z',
    })
    vi.mocked(globalSkillsApi.startDistillation).mockResolvedValue(run)
    vi.mocked(globalSkillsApi.forgeRules).mockResolvedValue([rule])
    vi.mocked(globalSkillsApi.reviewForgeRule).mockResolvedValue({ ...rule, status: 'ACCEPTED' })
    vi.mocked(globalSkillsApi.generateForgeContract).mockResolvedValue({ ...run, candidateContract: { provenance: { rawTextIncludedInContract: false } } })
    vi.mocked(globalSkillsApi.validateForge).mockResolvedValue({ valid: true, score: 100, missingSections: [], version: null })
    vi.mocked(globalSkillsApi.getForgeRun).mockResolvedValue({ ...run, status: 'VALIDATED' })
  })

  it('keeps customized template text when the Skill type changes', async () => {
    const view = wrapper()
    const selects = view.findAll('select')
    await selects[0]!.setValue('FOUNDATION')
    await selects[1]!.setValue('PROSE')
    const focus = view.findAll('textarea')[0]!
    expect(focus.element.value).toContain('章节结构')

    await focus.setValue('只学习我指定的动作节奏。')
    await selects[0]!.setValue('TECHNIQUE')

    expect(focus.element.value).toBe('只学习我指定的动作节奏。')
    expect(view.text()).toContain('当前内容已被修改')
    expect(view.text()).toContain('使用新模板')
  })

  it('requires enough manual text and ownership confirmation before distillation', async () => {
    const view = wrapper()
    await prepareManualForm(view)
    const submit = view.get('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()

    const ownership = view.get('.ownership-confirm input')
    await ownership.setValue(true)
    expect(submit.attributes('disabled')).toBeUndefined()
    await submit.trigger('submit')
    await flushPromises()

    expect(globalSkillsApi.createForgeRun).toHaveBeenCalledWith(expect.objectContaining({
      ownershipConfirmed: true,
      materialTag: 'PROSE',
      skillType: 'FOUNDATION',
      excludeCharacterNames: true,
      reusableMethodsOnly: true,
    }))
    expect(globalSkillsApi.addManualSource).toHaveBeenCalledWith('run-1', expect.objectContaining({ ownershipConfirmed: true }))
    expect(globalSkillsApi.startDistillation).toHaveBeenCalledWith('run-1')
    expect(view.text()).toContain('表达 DNA')
    expect(view.text()).toContain('使用短段落推进局部节奏')
  })

  it('shows paragraph evidence and requires explicit rule acceptance', async () => {
    const view = wrapper()
    await prepareManualForm(view)
    await view.get('.ownership-confirm input').setValue(true)
    await view.get('button[type="submit"]').trigger('submit')
    await flushPromises()

    expect(view.text()).not.toContain('他推开门。屋内的人都停下了。')
    const evidenceButton = view.findAll('.rule-actions button').find(button => button.text().includes('查看证据'))!
    await evidenceButton.trigger('click')
    expect(view.text()).toContain('他推开门。屋内的人都停下了。')

    const acceptButton = view.findAll('.rule-actions button').find(button => button.text().includes('接受'))!
    await acceptButton.trigger('click')
    await flushPromises()
    expect(globalSkillsApi.reviewForgeRule).toHaveBeenCalledWith('run-1', 'rule-1', 'ACCEPT', undefined)
    expect(view.text()).toContain('ACCEPTED')
  })

  it('rejects non-TXT files before upload', async () => {
    const view = wrapper()
    const file = new File(['binary'], 'payload.exe', { type: 'application/octet-stream' })
    Object.defineProperty(file, 'arrayBuffer', { value: () => Promise.resolve(new TextEncoder().encode('binary').buffer) })
    const input = view.get('input[type="file"]')
    Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
    await input.trigger('change')
    await flushPromises()
    expect(view.text()).toContain('仅支持 .txt 文件')
    expect(view.get('button[type="submit"]').attributes('disabled')).toBeDefined()
  })
})
