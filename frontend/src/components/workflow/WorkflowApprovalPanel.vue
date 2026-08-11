<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import ProblemAlert from '@/components/base/ProblemAlert.vue'
import type {
  ApproveWorkflowRequest,
  CharacterResponse,
  CharacterStateChangeRequest,
  ItemChangeRequest,
  KnowledgeChangeRequest,
  TimelineEventRequest,
  WorkflowResponse,
} from '@/api/types'
import { approvalValidationErrors, createApprovalRequest } from '@/features/workflows/approval'

const props = defineProps<{
  workflow: WorkflowResponse
  characters: CharacterResponse[]
  pending: boolean
  error: unknown
}>()
const emit = defineEmits<{ approve: [request: ApproveWorkflowRequest] }>()

const request = reactive<ApproveWorkflowRequest>(createApprovalRequest(props.workflow))
const characterIds = computed(() => new Set(props.characters.map((character) => character.id)))
const errors = computed(() => approvalValidationErrors(props.workflow, normalizedRequest(), characterIds.value))
const blockerCount = computed(() => props.workflow.reviewIssues.filter((issue) => issue.blocking && !issue.resolved).length)

watch(() => props.workflow.version, (version) => { request.expectedVersion = version })

function nullable(value: string | null): string | null {
  const normalized = value?.trim() ?? ''
  return normalized || null
}

function normalizedRequest(): ApproveWorkflowRequest {
  return {
    expectedVersion: request.expectedVersion,
    changeSummary: nullable(request.changeSummary),
    acceptedFactIndexes: [...request.acceptedFactIndexes].sort((left, right) => left - right),
    characterStateChanges: request.characterStateChanges.map((change) => ({
      ...change,
      currentLocation: nullable(change.currentLocation),
      physicalCondition: nullable(change.physicalCondition),
      emotionalState: nullable(change.emotionalState),
      abilities: nullable(change.abilities),
      inventoryNotes: nullable(change.inventoryNotes),
      notes: nullable(change.notes),
      evidence: change.evidence.trim(),
    })),
    itemChanges: request.itemChanges.map((change) => ({
      ...change,
      itemKey: change.itemKey.trim(),
      itemName: change.itemName.trim(),
      evidence: change.evidence.trim(),
    })),
    timelineEvents: request.timelineEvents.map((event) => ({
      ...event,
      participantIds: [...event.participantIds],
      knownByIds: [...event.knownByIds],
      location: nullable(event.location),
      storyTime: nullable(event.storyTime),
      action: event.action.trim(),
      result: event.result.trim(),
      evidence: event.evidence.trim(),
    })),
    knowledgeChanges: request.knowledgeChanges.map((change) => ({
      ...change,
      factKey: change.factKey.trim(),
      content: change.content.trim(),
      sourceEventId: nullable(change.sourceEventId),
      evidence: change.evidence.trim(),
    })),
  }
}

function toggleFact(index: number, checked: boolean): void {
  if (checked && !request.acceptedFactIndexes.includes(index)) request.acceptedFactIndexes.push(index)
  if (!checked) request.acceptedFactIndexes = request.acceptedFactIndexes.filter((value) => value !== index)
}

function toggleFactFromEvent(index: number, event: globalThis.Event): void {
  toggleFact(index, (event.target as globalThis.HTMLInputElement).checked)
}

function selected(index: number): boolean {
  return request.acceptedFactIndexes.includes(index)
}

function addCharacterState(): void {
  const character = props.characters[0]
  const row: CharacterStateChangeRequest = {
    characterId: character?.id ?? '',
    lifeStatus: character?.state.lifeStatus ?? 'UNKNOWN',
    currentLocation: character?.state.currentLocation ?? null,
    physicalCondition: character?.state.physicalCondition ?? null,
    emotionalState: character?.state.emotionalState ?? null,
    abilities: character?.state.abilities ?? null,
    inventoryNotes: character?.state.inventoryNotes ?? null,
    notes: character?.state.notes ?? null,
    expectedVersion: character?.state.version ?? 0,
    evidence: '',
  }
  request.characterStateChanges.push(row)
}

function syncCharacterState(row: CharacterStateChangeRequest): void {
  const character = props.characters.find((candidate) => candidate.id === row.characterId)
  if (!character) return
  row.lifeStatus = character.state.lifeStatus ?? 'UNKNOWN'
  row.currentLocation = character.state.currentLocation
  row.physicalCondition = character.state.physicalCondition
  row.emotionalState = character.state.emotionalState
  row.abilities = character.state.abilities
  row.inventoryNotes = character.state.inventoryNotes
  row.notes = character.state.notes
  row.expectedVersion = character.state.version
}

function addItem(): void {
  const row: ItemChangeRequest = { itemKey: '', itemName: '', fromOwnerCharacterId: null, toOwnerCharacterId: null, status: 'ACTIVE', evidence: '' }
  request.itemChanges.push(row)
}

function addTimeline(): void {
  const row: TimelineEventRequest = { participantIds: [], knownByIds: [], location: null, storyTime: null, action: '', result: '', importance: 0.5, evidence: '' }
  request.timelineEvents.push(row)
}

function addKnowledge(): void {
  const row: KnowledgeChangeRequest = { characterId: props.characters[0]?.id ?? '', factKey: '', content: '', certainty: 'CONFIRMED', sourceEventId: null, evidence: '' }
  request.knowledgeChanges.push(row)
}

function submit(): void {
  if (errors.value.length > 0) return
  emit('approve', normalizedRequest())
}
</script>

<template>
  <section class="workflow-panel approval-panel">
    <header>
      <div><p class="eyebrow">Atomic Commit</p><h2>候选事实与最终审批</h2></div>
      <span class="status-pill" :class="{ danger: blockerCount > 0 }">{{ blockerCount ? `${blockerCount} BLOCKER` : '可审批' }}</span>
    </header>

    <div class="approval-section">
      <div class="approval-section-heading"><div><h3>候选事实</h3><p>只有勾选的索引会被接受；其余候选将在原子提交中标记为拒绝。</p></div><span>{{ request.acceptedFactIndexes.length }}/{{ workflow.candidateFacts.length }}</span></div>
      <div v-if="workflow.candidateFacts.length" class="candidate-fact-list">
        <label v-for="fact in workflow.candidateFacts" :key="fact.id" class="candidate-fact-card" :class="{ selected: selected(fact.candidateIndex) }">
          <input type="checkbox" :checked="selected(fact.candidateIndex)" :disabled="!fact.evidence.trim()" @change="toggleFactFromEvent(fact.candidateIndex, $event)" />
          <span class="candidate-fact-index">#{{ fact.candidateIndex }}</span>
          <span><strong>{{ fact.content }}</strong><small>正文证据</small><q>{{ fact.evidence || '后端未返回证据，不能接受' }}</q></span>
        </label>
      </div>
      <div v-else class="workflow-empty">本次没有候选事实，仍可提交章节版本。</div>
    </div>

    <details class="approval-advanced">
      <summary><span>高级原子变更</span><small>可选：人物状态、道具、时间线和人物知识</small></summary>
      <div class="approval-change-group">
        <div class="approval-section-heading"><div><h3>人物状态</h3><p>版本号由当前人物状态自动带入。</p></div><button class="sw-button sw-button--secondary" type="button" @click="addCharacterState">添加</button></div>
        <article v-for="(row, index) in request.characterStateChanges" :key="index" class="approval-change-card">
          <header><strong>人物状态 {{ index + 1 }}</strong><button type="button" @click="request.characterStateChanges.splice(index, 1)">移除</button></header>
          <div class="approval-form-grid">
            <label>人物<select v-model="row.characterId" @change="syncCharacterState(row)"><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>生命状态<select v-model="row.lifeStatus"><option value="UNKNOWN">未知</option><option value="ALIVE">存活</option><option value="DEAD">死亡</option></select></label>
            <label>当前位置<input v-model="row.currentLocation" maxlength="200" /></label>
            <label>状态版本<input :value="row.expectedVersion" readonly /></label>
            <label class="approval-field-wide">身体状况<textarea v-model="row.physicalCondition" maxlength="5000"></textarea></label>
            <label class="approval-field-wide">情绪状态<textarea v-model="row.emotionalState" maxlength="5000"></textarea></label>
            <label class="approval-field-wide">能力<textarea v-model="row.abilities" maxlength="10000"></textarea></label>
            <label class="approval-field-wide">随身物品备注<textarea v-model="row.inventoryNotes" maxlength="10000"></textarea></label>
            <label class="approval-field-wide">状态备注<textarea v-model="row.notes" maxlength="10000"></textarea></label>
            <label class="approval-field-wide">正文证据<textarea v-model="row.evidence" required maxlength="5000"></textarea></label>
          </div>
        </article>
      </div>

      <div class="approval-change-group">
        <div class="approval-section-heading"><div><h3>道具归属</h3><p>同一 itemKey 在一次提交中只能有一个最终持有者。</p></div><button class="sw-button sw-button--secondary" type="button" @click="addItem">添加</button></div>
        <article v-for="(row, index) in request.itemChanges" :key="index" class="approval-change-card">
          <header><strong>道具变更 {{ index + 1 }}</strong><button type="button" @click="request.itemChanges.splice(index, 1)">移除</button></header>
          <div class="approval-form-grid">
            <label>Item Key<input v-model="row.itemKey" maxlength="160" /></label><label>道具名称<input v-model="row.itemName" maxlength="200" /></label>
            <label>原持有人<select v-model="row.fromOwnerCharacterId"><option :value="null">无 / 新道具</option><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>最终持有人<select v-model="row.toOwnerCharacterId"><option :value="null">无持有人</option><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>状态<select v-model="row.status"><option value="ACTIVE">正常</option><option value="DAMAGED">损坏</option><option value="DESTROYED">毁坏</option><option value="LOST">遗失</option></select></label>
            <label class="approval-field-wide">正文证据<textarea v-model="row.evidence" required maxlength="5000"></textarea></label>
          </div>
        </article>
      </div>

      <div class="approval-change-group">
        <div class="approval-section-heading"><div><h3>时间线事件</h3><p>参与者、知情者均只能选择当前项目人物。</p></div><button class="sw-button sw-button--secondary" type="button" @click="addTimeline">添加</button></div>
        <article v-for="(row, index) in request.timelineEvents" :key="index" class="approval-change-card">
          <header><strong>时间线 {{ index + 1 }}</strong><button type="button" @click="request.timelineEvents.splice(index, 1)">移除</button></header>
          <div class="approval-form-grid">
            <label>参与人物<select v-model="row.participantIds" multiple><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>知情人物<select v-model="row.knownByIds" multiple><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>地点<input v-model="row.location" maxlength="200" /></label><label>故事时间<input v-model="row.storyTime" maxlength="200" placeholder="YYYY-MM-DD 或叙事时间" /></label>
            <label>重要度 0-1<input v-model.number="row.importance" type="number" min="0" max="1" step="0.1" /></label>
            <label class="approval-field-wide">行动<textarea v-model="row.action" required maxlength="20000"></textarea></label>
            <label class="approval-field-wide">结果<textarea v-model="row.result" required maxlength="20000"></textarea></label>
            <label class="approval-field-wide">正文证据<textarea v-model="row.evidence" required maxlength="5000"></textarea></label>
          </div>
        </article>
      </div>

      <div class="approval-change-group">
        <div class="approval-section-heading"><div><h3>人物知识</h3><p>sourceEventId 可留空；若填写必须属于当前项目。</p></div><button class="sw-button sw-button--secondary" type="button" @click="addKnowledge">添加</button></div>
        <article v-for="(row, index) in request.knowledgeChanges" :key="index" class="approval-change-card">
          <header><strong>知识变更 {{ index + 1 }}</strong><button type="button" @click="request.knowledgeChanges.splice(index, 1)">移除</button></header>
          <div class="approval-form-grid">
            <label>人物<select v-model="row.characterId"><option v-for="character in characters" :key="character.id" :value="character.id">{{ character.name }}</option></select></label>
            <label>Fact Key<input v-model="row.factKey" maxlength="160" /></label>
            <label>确定性<select v-model="row.certainty"><option value="SUSPECTED">怀疑</option><option value="CONFIRMED">确认</option></select></label>
            <label>来源事件 ID<input v-model="row.sourceEventId" placeholder="可留空" /></label>
            <label class="approval-field-wide">知识内容<textarea v-model="row.content" required maxlength="20000"></textarea></label>
            <label class="approval-field-wide">正文证据<textarea v-model="row.evidence" required maxlength="5000"></textarea></label>
          </div>
        </article>
      </div>
    </details>

    <div class="approval-submit">
      <label>提交说明<textarea v-model="request.changeSummary" maxlength="500" placeholder="例如：确认第一章，并接受经核对的候选事实"></textarea></label>
      <ul v-if="errors.length" class="approval-errors" role="alert"><li v-for="message in errors" :key="message">{{ message }}</li></ul>
      <ProblemAlert v-if="error" :error="error" />
      <div><span>提交会在一个后端事务中创建章节版本并处理所有勾选变更，不能乐观更新。</span><button class="sw-button sw-button--primary" type="button" :disabled="pending || errors.length > 0" @click="submit">{{ pending ? '原子提交中…' : '确认并原子提交' }}</button></div>
    </div>
  </section>
</template>
