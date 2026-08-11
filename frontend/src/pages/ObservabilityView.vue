<script setup lang="ts">
import { ElDialog, ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import type { UsageChartOption } from '@/components/observability/UsageChart.vue'
import UsageChart from '@/components/observability/UsageChart.vue'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import {
  agentUsage,
  currencies,
  dailyUsage,
  formatCost,
  formatDuration,
  modelUsage,
  usageOverview,
} from '@/features/usage/metrics'
import { useProjectQuery } from '@/queries/projects'
import { useBudgetQuery, useModelConfigQuery } from '@/queries/workflows'
import { useCostSummaryQuery, usePricingRulesQuery, useUpdateBudgetMutation, useUsageQuery } from '@/queries/usage'

const route = useRoute()
const projectId = computed(() => String(route.params.projectId ?? ''))
const projectQuery = useProjectQuery(projectId)
const usageQuery = useUsageQuery(projectId)
const costsQuery = useCostSummaryQuery(projectId)
const budgetQuery = useBudgetQuery(projectId)
const modelsQuery = useModelConfigQuery()
const pricingQuery = usePricingRulesQuery()
const updateBudget = useUpdateBudgetMutation(projectId)
const budgetOpen = ref(false)
const budgetForm = reactive({ taskTokenLimit: 1, userDailyCostLimit: 0, projectCostLimit: 0, writerOutputTokenLimit: 1, plannerReasoningTokenLimit: 1, expectedVersion: 0 })

const loading = computed(() => [projectQuery, usageQuery, costsQuery, budgetQuery, modelsQuery].some((query) => query.isPending.value))
const primaryError = computed(() => [projectQuery, usageQuery, costsQuery, budgetQuery, modelsQuery].find((query) => query.isError.value)?.error.value)
const records = computed(() => usageQuery.data.value ?? [])
const overview = computed(() => usageOverview(records.value, costsQuery.data.value ?? { projectId: projectId.value, estimatedCost: 0, actualCost: 0, unpricedRequests: 0, requests: 0 }))
const days = computed(() => dailyUsage(records.value).slice(-30))
const agents = computed(() => agentUsage(records.value))
const modelBuckets = computed(() => modelUsage(records.value))
const recordCurrencies = computed(() => currencies(records.value))
const currency = computed(() => recordCurrencies.value.length === 1 ? recordCurrencies.value[0] : undefined)
const currencyNote = computed(() => recordCurrencies.value.length > 1 ? `包含多种货币：${recordCurrencies.value.join('、')}，未进行前端换算` : currency.value ?? '后端未返回货币')
const projectBudgetRate = computed(() => {
  const limit = budgetQuery.data.value?.projectCostLimit ?? 0
  if (limit <= 0) return overview.value.actualCost > 0 ? 1 : 0
  return Math.min(overview.value.actualCost / limit, 1)
})
const projectBudgetExhausted = computed(() => {
  const limit = budgetQuery.data.value?.projectCostLimit
  return limit !== undefined && overview.value.actualCost >= limit
})
const budgetErrors = computed(() => {
  const errors: string[] = []
  if (!Number.isInteger(budgetForm.taskTokenLimit) || budgetForm.taskTokenLimit < 1) errors.push('单工作流 Token 上限必须是正整数。')
  if (!Number.isInteger(budgetForm.writerOutputTokenLimit) || budgetForm.writerOutputTokenLimit < 1) errors.push('Writer 输出上限必须是正整数。')
  if (!Number.isInteger(budgetForm.plannerReasoningTokenLimit) || budgetForm.plannerReasoningTokenLimit < 1) errors.push('Planner 推理上限必须是正整数。')
  if (!Number.isFinite(budgetForm.userDailyCostLimit) || budgetForm.userDailyCostLimit < 0) errors.push('每日费用上限不能为负数。')
  if (!Number.isFinite(budgetForm.projectCostLimit) || budgetForm.projectCostLimit < 0) errors.push('项目费用上限不能为负数。')
  return errors
})
const modelContractWarnings = computed(() => {
  const warnings: string[] = []
  const writer = modelsQuery.data.value?.find((model) => model.agent === 'WRITER')
  const planner = modelsQuery.data.value?.find((model) => model.agent === 'PLANNER')
  if (writer && budgetForm.writerOutputTokenLimit < writer.maxOutputTokens) warnings.push(`Writer 合同需要 ${writer.maxOutputTokens.toLocaleString()} Token，保存此值会使工作流预检失败。`)
  if (planner && budgetForm.plannerReasoningTokenLimit < planner.maxOutputTokens) warnings.push(`Planner 合同需要 ${planner.maxOutputTokens.toLocaleString()} Token，保存此值会使工作流预检失败。`)
  return warnings
})

const tokenChart = computed<UsageChartOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['输入', '输出', '推理'] },
  grid: { left: 56, right: 20, top: 44, bottom: 42 },
  xAxis: { type: 'category', data: days.value.map((day) => day.key), axisLabel: { hideOverlap: true } },
  yAxis: { type: 'value', name: 'Token' },
  series: [
    { name: '输入', type: 'bar', stack: 'token', data: days.value.map((day) => day.promptTokens), itemStyle: { color: '#315f4c' } },
    { name: '输出', type: 'bar', stack: 'token', data: days.value.map((day) => day.completionTokens), itemStyle: { color: '#77a88f' } },
    { name: '推理', type: 'bar', stack: 'token', data: days.value.map((day) => day.reasoningTokens), itemStyle: { color: '#c18b4a' } },
  ],
}))

const costChart = computed<UsageChartOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 70, right: 20, top: 24, bottom: 42 },
  xAxis: { type: 'category', data: days.value.map((day) => day.key), axisLabel: { hideOverlap: true } },
  yAxis: { type: 'value', name: currency.value ?? '成本' },
  series: [{ name: '实际费用', type: 'line', smooth: true, data: days.value.map((day) => Number(day.actualCost.toFixed(8))), lineStyle: { color: '#315f4c', width: 3 }, itemStyle: { color: '#315f4c' }, areaStyle: { color: 'rgba(49,95,76,.12)' } }],
}))

const cacheChart = computed<UsageChartOption>(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    name: '缓存 Token', type: 'pie', radius: ['46%', '70%'],
    data: [
      { name: 'Cache Hit', value: overview.value.cacheHitTokens, itemStyle: { color: '#315f4c' } },
      { name: 'Cache Miss', value: overview.value.cacheMissTokens, itemStyle: { color: '#c18b4a' } },
    ],
  }],
}))

const durationChart = computed<UsageChartOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 72, right: 20, top: 24, bottom: 42 },
  xAxis: { type: 'category', data: agents.value.map((agent) => agent.key), axisLabel: { hideOverlap: true } },
  yAxis: { type: 'value', name: '平均 ms' },
  series: [{ name: '平均请求耗时', type: 'bar', data: agents.value.map((agent) => agent.requests ? Math.round(agent.durationMillis / agent.requests) : 0), itemStyle: { color: '#77a88f', borderRadius: [5, 5, 0, 0] } }],
}))

function openBudget(): void {
  const budget = budgetQuery.data.value
  if (!budget) return
  Object.assign(budgetForm, {
    taskTokenLimit: budget.taskTokenLimit,
    userDailyCostLimit: budget.userDailyCostLimit,
    projectCostLimit: budget.projectCostLimit,
    writerOutputTokenLimit: budget.writerOutputTokenLimit,
    plannerReasoningTokenLimit: budget.plannerReasoningTokenLimit,
    expectedVersion: budget.version,
  })
  updateBudget.reset()
  budgetOpen.value = true
}

async function saveBudget(): Promise<void> {
  if (budgetErrors.value.length) return
  try {
    await updateBudget.mutateAsync({ ...budgetForm })
    budgetOpen.value = false
    ElMessage.success('项目预算已更新；后端会在工作流预检时强制执行')
  } catch { /* Problem Details remains visible. */ }
}

function refresh(): void {
  void Promise.all([projectQuery.refetch(), usageQuery.refetch(), costsQuery.refetch(), budgetQuery.refetch(), modelsQuery.refetch()])
}
</script>

<template>
  <main class="page-container observability-page">
    <LoadingState v-if="loading" label="正在加载模型、费用和预算…" />
    <ErrorState v-else-if="primaryError" :error="primaryError" @retry="refresh" />
    <template v-else>
      <header class="observability-header"><div><p class="eyebrow">Models &amp; Costs</p><h1 tabindex="-1">模型与费用</h1><p>{{ projectQuery.data.value?.name }} · 所有数字均来自后端 Usage、Costs、Budget 和 Model Config。</p></div><button class="sw-button sw-button--secondary" type="button" @click="refresh">刷新数据</button></header>

      <section v-if="overview.unpricedRequests" class="usage-warning" role="alert"><strong>{{ overview.unpricedRequests }} 个请求未计价</strong><span>后端没有匹配的 Pricing Rule；这些请求没有被伪装为零成本。</span></section>
      <section v-if="projectBudgetExhausted" class="usage-warning usage-warning--danger" role="alert"><strong>项目费用预算已耗尽</strong><span>后端会阻止新的工作流通过预算预检。请调整预算或检查实际费用。</span></section>

      <section class="usage-kpis" aria-label="用量摘要">
        <article><span>实际费用</span><strong>{{ formatCost(overview.actualCost, currency) }}</strong><small>{{ currencyNote }}</small></article>
        <article><span>总 Token</span><strong>{{ overview.totalTokens.toLocaleString() }}</strong><small>输入 + 输出 + 推理</small></article>
        <article><span>Cache Hit</span><strong>{{ overview.cacheHitRate === null ? '无数据' : `${(overview.cacheHitRate * 100).toFixed(1)}%` }}</strong><small>{{ overview.cacheHitTokens.toLocaleString() }} Hit / {{ overview.cacheMissTokens.toLocaleString() }} Miss</small></article>
        <article><span>请求</span><strong>{{ overview.requests.toLocaleString() }}</strong><small>{{ overview.succeeded }} 成功 · {{ overview.failed }} 失败</small></article>
        <article><span>平均耗时</span><strong>{{ formatDuration(overview.averageDurationMillis) }}</strong><small>P95 {{ formatDuration(overview.p95DurationMillis) }}</small></article>
      </section>

      <section class="usage-chart-grid">
        <figure class="usage-figure"><figcaption><h2>每日 Token</h2><p>最近最多 30 个有请求的 UTC 日期；输入、输出和推理分别统计。</p></figcaption><UsageChart :option="tokenChart" label="每日输入、输出和推理 Token 堆叠柱状图" /><details><summary>查看 Token 数据表</summary><div class="usage-table-wrap"><table><thead><tr><th>日期</th><th>输入</th><th>输出</th><th>推理</th></tr></thead><tbody><tr v-for="day in days" :key="day.key"><td>{{ day.key }}</td><td>{{ day.promptTokens.toLocaleString() }}</td><td>{{ day.completionTokens.toLocaleString() }}</td><td>{{ day.reasoningTokens.toLocaleString() }}</td></tr></tbody></table></div></details></figure>
        <figure class="usage-figure"><figcaption><h2>每日实际费用</h2><p>{{ currencyNote }}。失败和未计价请求的 actualCost 不会被补零估算。</p></figcaption><UsageChart :option="costChart" label="每日实际费用折线图" /><details><summary>查看费用数据表</summary><div class="usage-table-wrap"><table><thead><tr><th>日期</th><th>请求数</th><th>实际费用</th></tr></thead><tbody><tr v-for="day in days" :key="day.key"><td>{{ day.key }}</td><td>{{ day.requests }}</td><td>{{ formatCost(day.actualCost, currency) }}</td></tr></tbody></table></div></details></figure>
        <figure class="usage-figure"><figcaption><h2>缓存命中</h2><p>{{ overview.cacheHitRate === null ? '后端没有返回 Cache Hit/Miss Token。' : `缓存口径 Token 命中率 ${(overview.cacheHitRate * 100).toFixed(1)}%。` }}</p></figcaption><UsageChart :option="cacheChart" label="Cache Hit 与 Cache Miss Token 环形图" /><details><summary>查看缓存数据</summary><dl class="usage-text-data"><div><dt>Hit</dt><dd>{{ overview.cacheHitTokens.toLocaleString() }}</dd></div><div><dt>Miss</dt><dd>{{ overview.cacheMissTokens.toLocaleString() }}</dd></div></dl></details></figure>
        <figure class="usage-figure"><figcaption><h2>Agent 请求耗时</h2><p>这是 LLM Usage 的请求耗时，不是完整 Workflow 端到端耗时。</p></figcaption><UsageChart :option="durationChart" label="各 Agent 平均 LLM 请求耗时柱状图" /><details><summary>查看耗时数据表</summary><div class="usage-table-wrap"><table><thead><tr><th>Agent</th><th>请求</th><th>平均耗时</th></tr></thead><tbody><tr v-for="agent in agents" :key="agent.key"><td>{{ agent.key }}</td><td>{{ agent.requests }}</td><td>{{ formatDuration(agent.durationMillis / agent.requests) }}</td></tr></tbody></table></div></details></figure>
      </section>

      <section class="observability-panel budget-panel"><header><div><p class="eyebrow">Budget</p><h2>项目预算</h2></div><button class="sw-button sw-button--secondary" type="button" @click="openBudget">调整预算</button></header><div class="budget-overview"><div class="budget-meter"><div><span>项目实际费用</span><strong>{{ formatCost(overview.actualCost, currency) }} / {{ formatCost(budgetQuery.data.value?.projectCostLimit ?? 0, currency) }}</strong></div><progress :value="projectBudgetRate" max="1">{{ Math.round(projectBudgetRate * 100) }}%</progress></div><dl><div><dt>单工作流 Token</dt><dd>{{ budgetQuery.data.value?.taskTokenLimit.toLocaleString() }}</dd></div><div><dt>用户每日费用</dt><dd>{{ formatCost(budgetQuery.data.value?.userDailyCostLimit ?? 0, currency) }}</dd></div><div><dt>Writer 输出</dt><dd>{{ budgetQuery.data.value?.writerOutputTokenLimit.toLocaleString() }}</dd></div><div><dt>Planner 推理</dt><dd>{{ budgetQuery.data.value?.plannerReasoningTokenLimit.toLocaleString() }}</dd></div></dl></div><p class="observability-note">后端没有跨项目“当前用户今日费用”接口，因此这里只展示每日上限；实际每日限制仍由后端 Workflow Preflight 强制执行。</p></section>

      <section class="observability-panel"><header><div><p class="eyebrow">Model Comparison</p><h2>模型用量对比</h2></div><span>{{ modelBuckets.length }} 个模型</span></header><div class="usage-table-wrap"><table><thead><tr><th>模型</th><th>请求</th><th>输入 Token</th><th>输出 Token</th><th>推理 Token</th><th>平均耗时</th><th>实际费用</th></tr></thead><tbody><tr v-for="model in modelBuckets" :key="model.key"><td><strong>{{ model.key }}</strong></td><td>{{ model.requests }}</td><td>{{ model.promptTokens.toLocaleString() }}</td><td>{{ model.completionTokens.toLocaleString() }}</td><td>{{ model.reasoningTokens.toLocaleString() }}</td><td>{{ formatDuration(model.durationMillis / model.requests) }}</td><td>{{ formatCost(model.actualCost, currency) }}</td></tr></tbody></table></div><p class="observability-note">模型维度来自每条 UsageRecord；后端没有按 Workflow 或 Chapter 关联用量。</p></section>

      <section class="observability-panel"><header><div><p class="eyebrow">Capability Matrix</p><h2>模型能力</h2></div><span>{{ modelsQuery.data.value?.length ?? 0 }} Agents</span></header><div class="usage-table-wrap"><table><thead><tr><th>Agent</th><th>模型</th><th>Thinking</th><th>Reasoning</th><th>输出上限</th><th>流式</th><th>重试</th><th>不支持参数</th></tr></thead><tbody><tr v-for="model in modelsQuery.data.value" :key="model.agent"><td><strong>{{ model.agent }}</strong></td><td>{{ model.model }}</td><td>{{ model.thinking ? '是' : '否' }}</td><td>{{ model.reasoningEffort || '—' }}</td><td>{{ model.maxOutputTokens.toLocaleString() }}</td><td>{{ model.stream ? '是' : '否' }}</td><td>{{ model.maxAttempts }}</td><td><span v-for="parameter in model.ignoredParameters" :key="parameter" class="ignored-parameter" aria-disabled="true">{{ parameter }}</span><span v-if="!model.ignoredParameters.length">—</span></td></tr></tbody></table></div></section>

      <section class="observability-panel"><header><div><p class="eyebrow">Usage Records</p><h2>请求明细</h2></div><span>显示 {{ Math.min(records.length, 100) }} / {{ records.length }}</span></header><div class="usage-table-wrap"><table><thead><tr><th>时间</th><th>Agent / 模型</th><th>状态</th><th>Token</th><th>缓存</th><th>耗时</th><th>实际费用</th><th>模型请求 ID</th></tr></thead><tbody><tr v-for="record in records.slice(0, 100)" :key="record.id"><td>{{ new Date(record.requestedAt).toLocaleString() }}</td><td><strong>{{ record.agent }}</strong><small>{{ record.model }}</small></td><td><span class="usage-status" :class="record.status.toLowerCase()">{{ record.status }}</span></td><td>{{ (record.promptTokens + record.completionTokens + record.reasoningTokens).toLocaleString() }}</td><td>{{ record.promptCacheHitTokens.toLocaleString() }} / {{ record.promptCacheMissTokens.toLocaleString() }}</td><td>{{ formatDuration(record.durationMillis) }}</td><td>{{ record.actualCost === null ? '未产生' : formatCost(record.actualCost, record.currency ?? undefined) }}</td><td><code>{{ record.requestId || '后端未返回' }}</code></td></tr></tbody></table></div><p class="observability-note">模型 requestId 不是应用 Trace ID。Trace ID 只在后端 Problem Details 或 X-Trace-ID 响应头存在时显示。</p></section>

      <section class="observability-panel"><header><div><p class="eyebrow">Pricing</p><h2>后端价格规则</h2></div><span>{{ pricingQuery.data.value?.length ?? 0 }} 条</span></header><ProblemAlert v-if="pricingQuery.isError.value" :error="pricingQuery.error.value" /><div v-else class="usage-table-wrap"><table><thead><tr><th>模型</th><th>版本</th><th>输入 / M</th><th>输出 / M</th><th>推理 / M</th><th>Hit / M</th><th>Miss / M</th><th>生效时间</th></tr></thead><tbody><tr v-for="rule in pricingQuery.data.value" :key="rule.id"><td>{{ rule.model }}</td><td>{{ rule.ruleVersion }}</td><td>{{ formatCost(rule.inputPerMillion, rule.currency) }}</td><td>{{ formatCost(rule.outputPerMillion, rule.currency) }}</td><td>{{ formatCost(rule.reasoningPerMillion, rule.currency) }}</td><td>{{ formatCost(rule.cacheHitPerMillion, rule.currency) }}</td><td>{{ formatCost(rule.cacheMissPerMillion, rule.currency) }}</td><td>{{ new Date(rule.effectiveFrom).toLocaleDateString() }}—{{ rule.effectiveTo ? new Date(rule.effectiveTo).toLocaleDateString() : '当前' }}</td></tr></tbody></table></div></section>
    </template>

    <ElDialog v-model="budgetOpen" title="调整项目预算" width="min(680px, 94vw)" destroy-on-close>
      <form id="budget-form" class="sw-form" @submit.prevent="saveBudget">
        <label class="form-field"><span>单工作流 Token 上限</span><input v-model.number="budgetForm.taskTokenLimit" type="number" min="1" step="1" required /></label>
        <label class="form-field"><span>用户每日费用上限</span><input v-model.number="budgetForm.userDailyCostLimit" type="number" min="0" step="0.000001" required /></label>
        <label class="form-field"><span>项目累计费用上限</span><input v-model.number="budgetForm.projectCostLimit" type="number" min="0" step="0.000001" required /></label>
        <label class="form-field"><span>Writer 输出 Token 上限</span><input v-model.number="budgetForm.writerOutputTokenLimit" type="number" min="1" step="1" required /></label>
        <label class="form-field"><span>Planner 推理 Token 上限</span><input v-model.number="budgetForm.plannerReasoningTokenLimit" type="number" min="1" step="1" required /></label>
      </form>
      <ul v-if="budgetErrors.length" class="approval-errors" role="alert"><li v-for="error in budgetErrors" :key="error">{{ error }}</li></ul>
      <ul v-if="modelContractWarnings.length" class="budget-warnings" role="status"><li v-for="warning in modelContractWarnings" :key="warning">{{ warning }}</li></ul>
      <ProblemAlert v-if="updateBudget.isError.value" :error="updateBudget.error.value" />
      <template #footer><button class="sw-button sw-button--secondary" type="button" @click="budgetOpen = false">取消</button><button class="sw-button sw-button--primary" type="submit" form="budget-form" :disabled="updateBudget.isPending.value || budgetErrors.length > 0">{{ updateBudget.isPending.value ? '保存中…' : '保存预算' }}</button></template>
    </ElDialog>
  </main>
</template>
