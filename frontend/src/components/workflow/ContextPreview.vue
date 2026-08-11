<script setup lang="ts">
import { computed } from 'vue'

import type { BudgetResponse, ContextPacketResponse } from '@/api/types'

const props = defineProps<{ packet: ContextPacketResponse; budget: BudgetResponse | undefined }>()
const tokenPercent = computed(() => props.budget
  ? Math.min(100, Math.round((props.packet.tokenEstimate / props.budget.taskTokenLimit) * 100))
  : 0)
const categories = [
  ['项目与章纲', '项目、当前章节与上一章'],
  ['人物与状态', '视角人物与人物状态'],
  ['正典与事实', '正典资产、已接受事实、物品和知识'],
  ['世界书', '后端 Worldbook Preview 的激活结果'],
  ['事件记忆', '后端 Story Event Search 的检索结果'],
  ['Skill 硬规则', '合成后的规则快照，不允许在预览中移除'],
] as const
</script>

<template>
  <section class="workflow-panel context-preview-panel" aria-labelledby="context-heading">
    <header><div><p class="eyebrow">Context Preview</p><h2 id="context-heading">上下文包</h2></div><span class="status-pill" :class="{ danger: packet.stale }">{{ packet.stale ? 'STALE' : 'FRESH' }}</span></header>
    <div v-if="packet.stale" class="context-stale-alert" role="alert"><strong>Context Packet 已过期</strong><span>当前后端没有重建端点，需要返回章节重新启动工作流。</span></div>
    <div class="context-metrics">
      <div><span>Token 总估算</span><strong>{{ packet.tokenEstimate.toLocaleString() }}</strong></div>
      <div><span>后端预计费用</span><strong>¥{{ Number(packet.estimatedCost).toFixed(6) }}</strong></div>
      <div><span>失效时间</span><strong>{{ new Date(packet.expiresAt).toLocaleString() }}</strong></div>
    </div>
    <div v-if="budget" class="token-budget-bar">
      <div><span>本次上下文 / 任务上限</span><strong>{{ packet.tokenEstimate.toLocaleString() }} / {{ budget.taskTokenLimit.toLocaleString() }}</strong></div>
      <div class="token-track" aria-label="Token 预算占用"><span :style="{ width: `${tokenPercent}%` }" /></div>
    </div>
    <div class="context-source-notice">实际 ContextPacket API 只返回聚合指标，没有 sourceId、版本、激活原因或逐来源 Token。下列内容是后端构建器的来源类别，不冒充本次命中明细。</div>
    <div class="context-category-list">
      <article v-for="[name, description] in categories" :key="name">
        <div><strong>{{ name }}</strong><span v-if="name === 'Skill 硬规则'" class="hard-rule-badge">锁定</span></div>
        <p>{{ description }}</p>
        <small>来源明细未由 API 返回</small>
      </article>
    </div>
    <footer>Context ID：{{ packet.id }} · 创建于 {{ new Date(packet.createdAt).toLocaleString() }}</footer>
  </section>
</template>
