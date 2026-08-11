<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { globalSkillsApi } from '@/api/endpoints/globalSkills'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import { queryKeys } from '@/queries/keys'

const route = useRoute()
const skillId = computed(() => String(route.params.skillId ?? ''))
const queryClient = useQueryClient()
const skillQuery = useQuery({ queryKey: computed(() => ['global-skill', skillId.value]), queryFn: () => globalSkillsApi.get(skillId.value), enabled: () => Boolean(skillId.value) })
const versionsQuery = useQuery({ queryKey: computed(() => queryKeys.globalSkillVersions(skillId.value)), queryFn: () => globalSkillsApi.versions(skillId.value), enabled: () => Boolean(skillId.value) })
const testsQuery = useQuery({ queryKey: computed(() => ['global-skill-tests', skillId.value]), queryFn: () => globalSkillsApi.tests(skillId.value), enabled: () => Boolean(skillId.value) })
const validateMutation = useMutation({ mutationFn: () => globalSkillsApi.validate(skillId.value), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['global-skill', skillId.value] }); await queryClient.invalidateQueries({ queryKey: queryKeys.globalSkillVersions(skillId.value) }); await queryClient.invalidateQueries({ queryKey: queryKeys.globalSkills }) } })
const passedTests = computed(() => (testsQuery.data.value ?? []).filter(test => test.latestResult?.passed).length)

async function exportSkill(): Promise<void> {
  const blob = await globalSkillsApi.export(skillId.value)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${skillQuery.data.value?.slug ?? 'skill'}.zip`
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <main class="page-container skill-detail-page">
    <RouterLink class="back-link" to="/skills">← 返回 Skill 工坊</RouterLink>
    <LoadingState v-if="skillQuery.isPending.value" label="正在加载契约…" />
    <ErrorState v-else-if="skillQuery.isError.value" :error="skillQuery.error.value" @retry="skillQuery.refetch()" />
    <template v-else-if="skillQuery.data.value">
      <header class="page-header skill-detail-hero">
        <div class="skill-detail-hero__copy">
          <div class="skill-detail-identity"><span>{{ skillQuery.data.value.scope === 'BUILT_IN' ? '内置基础契约' : '私有全局 Skill' }}</span><span :class="`status-badge status-badge--${skillQuery.data.value.status.toLowerCase()}`">{{ skillQuery.data.value.status }}</span></div>
          <h1 tabindex="-1">{{ skillQuery.data.value.displayName }}</h1>
          <p>{{ skillQuery.data.value.description }}</p>
        </div>
        <div class="form-actions skill-detail-actions"><button class="sw-button sw-button--secondary" type="button" @click="exportSkill">导出 Skill 包</button><button v-if="skillQuery.data.value.scope !== 'BUILT_IN'" class="sw-button sw-button--primary" type="button" :disabled="validateMutation.isPending.value" @click="validateMutation.mutate()">{{ validateMutation.isPending.value ? '校验中…' : '确认并校验候选' }}</button></div>
      </header>
      <dl class="skill-detail-stats" aria-label="Skill 状态摘要">
        <div><dt>契约版本</dt><dd>{{ versionsQuery.data.value?.length ?? 0 }}</dd></div>
        <div><dt>测试通过</dt><dd>{{ passedTests }} / {{ testsQuery.data.value?.length ?? 0 }}</dd></div>
        <div><dt>绑定状态</dt><dd>{{ skillQuery.data.value.status === 'VALIDATED' ? '可用于项目' : '等待验证' }}</dd></div>
      </dl>
      <p v-if="validateMutation.data.value" class="skill-workshop-notice skill-validation-message">{{ validateMutation.data.value.valid ? `校验通过（${validateMutation.data.value.score} 分），已生成可绑定版本。` : `缺少段落：${validateMutation.data.value.missingSections.join('、')}` }}</p>
      <section class="contract-layout skill-contract-layout">
        <article class="contract-document"><header><div><span>Contract JSON</span><h2>契约内容</h2></div><small>结构化行为约束</small></header><pre>{{ JSON.stringify(skillQuery.data.value.contract, null, 2) }}</pre></article>
        <aside class="contract-side">
          <section><div class="contract-side__heading"><h2>版本历史</h2><span>{{ versionsQuery.data.value?.length ?? 0 }}</span></div><LoadingState v-if="versionsQuery.isPending.value" label="正在加载版本…" /><ol v-else class="version-list version-timeline"><li v-for="version in versionsQuery.data.value" :key="version.id"><div><strong>v{{ version.versionNo }}</strong><span :class="`status-badge status-badge--${version.status.toLowerCase()}`">{{ version.status }}</span></div><small>{{ version.snapshotHash.slice(0, 12) }}… · {{ version.tokenEstimate }} tokens</small></li></ol></section>
          <section><div class="contract-side__heading"><h2>验证测试</h2><span>{{ passedTests }}/{{ testsQuery.data.value?.length ?? 0 }}</span></div><LoadingState v-if="testsQuery.isPending.value" label="正在加载测试集…" /><div v-else-if="!testsQuery.data.value?.length" class="skill-test-empty">尚未生成测试集</div><ol v-else class="version-list skill-test-grid"><li v-for="test in testsQuery.data.value" :key="test.id"><div><strong>{{ test.title }}</strong><span :class="['test-result', { 'is-passed': test.latestResult?.passed === true, 'is-failed': test.latestResult?.passed === false }]">{{ test.latestResult?.passed === true ? '通过' : test.latestResult?.passed === false ? '失败' : '未运行' }}</span></div><small>{{ test.caseType }}<template v-if="test.latestResult"> · {{ test.latestResult.score }} 分</template></small></li></ol></section>
        </aside>
      </section>
    </template>
  </main>
</template>

<style scoped>
.skill-detail-page { padding-bottom: var(--sw-space-16); }
.skill-detail-page .back-link { padding-bottom: .15rem; border-bottom: 1px solid transparent; }
.skill-detail-page .back-link:hover { border-bottom-color: currentColor; }
.skill-detail-hero { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; padding-bottom: var(--sw-space-8); border-bottom: 1px solid var(--sw-border); }
.skill-detail-hero__copy { max-width: 760px; }
.skill-detail-identity { display: flex; align-items: center; gap: var(--sw-space-3); margin-bottom: var(--sw-space-4); color: var(--sw-accent); font-size: .75rem; font-weight: 700; letter-spacing: .05em; }
.skill-detail-hero h1 { max-width: 18ch; font-size: clamp(2.4rem, 5vw, 4rem); line-height: 1.08; }
.skill-detail-hero p { max-width: 62ch; margin: var(--sw-space-5) 0 0; line-height: 1.75; }
.skill-detail-actions { flex-wrap: wrap; }
.skill-detail-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 0 0 var(--sw-space-8); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); }
.skill-detail-stats div { padding: var(--sw-space-4) var(--sw-space-5); }
.skill-detail-stats div + div { border-left: 1px solid var(--sw-border); }
.skill-detail-stats dt { color: var(--sw-text-muted); font-size: .72rem; }
.skill-detail-stats dd { margin: .3rem 0 0; color: var(--sw-text-primary); font-size: 1.05rem; font-weight: 750; }
.skill-validation-message { border-color: color-mix(in srgb, var(--sw-success) 35%, var(--sw-border)); }
.skill-contract-layout { grid-template-columns: minmax(0, 1.65fr) minmax(320px, .85fr); align-items: start; gap: var(--sw-space-6); }
.skill-contract-layout > * { padding: 0; }
.contract-document { overflow: hidden; }
.contract-document > header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--sw-space-4); padding: var(--sw-space-5) var(--sw-space-6); border-bottom: 1px solid var(--sw-border); }
.contract-document header span { color: var(--sw-accent); font-size: .7rem; font-weight: 750; letter-spacing: .08em; text-transform: uppercase; }
.contract-document header h2 { margin: .25rem 0 0; font-family: var(--sw-font-serif); font-size: 1.45rem; }
.contract-document header small { color: var(--sw-text-muted); }
.contract-document pre { max-height: 760px; padding: var(--sw-space-6); border-radius: 0; background: var(--sw-bg-editor); font-family: "Cascadia Code", "SFMono-Regular", Consolas, monospace; font-size: .78rem; line-height: 1.7; }
.contract-side { display: grid; gap: 0; }
.contract-side > section { padding: var(--sw-space-5); }
.contract-side > section + section { border-top: 1px solid var(--sw-border); }
.contract-side__heading { display: flex; align-items: center; justify-content: space-between; gap: var(--sw-space-4); margin-bottom: var(--sw-space-4); }
.contract-side__heading h2 { margin: 0; font-family: var(--sw-font-serif); font-size: 1.12rem; }
.contract-side__heading > span { color: var(--sw-accent); font-size: .78rem; font-weight: 750; }
.version-timeline, .skill-test-grid { margin: 0; padding: 0; list-style: none; }
.version-timeline li, .skill-test-grid li { padding: var(--sw-space-3) 0; }
.version-timeline li + li, .skill-test-grid li + li { border-top: 1px solid color-mix(in srgb, var(--sw-border) 70%, transparent); }
.version-timeline li > div, .skill-test-grid li > div { display: flex; align-items: center; justify-content: space-between; gap: var(--sw-space-3); }
.version-timeline small, .skill-test-grid small { display: block; margin-top: .25rem; }
.skill-test-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); column-gap: var(--sw-space-5); }
.skill-test-grid li:nth-child(2) { border-top: 0; }
.test-result { flex: none; color: var(--sw-text-muted); font-size: .7rem; font-weight: 700; }
.test-result.is-passed { color: var(--sw-success); }
.test-result.is-failed { color: var(--sw-danger); }
.skill-test-empty { padding: var(--sw-space-5); border: 1px dashed var(--sw-border); border-radius: var(--sw-radius-control); color: var(--sw-text-secondary); text-align: center; }
@media (max-width: 960px) { .skill-contract-layout { grid-template-columns: 1fr; } .contract-document pre { max-height: 560px; } }
@media (max-width: 720px) { .skill-detail-hero { grid-template-columns: 1fr; align-items: start; } .skill-detail-actions { justify-content: flex-start; } .skill-detail-stats { grid-template-columns: 1fr; } .skill-detail-stats div + div { border-top: 1px solid var(--sw-border); border-left: 0; } }
@media (max-width: 520px) { .skill-detail-actions, .skill-detail-actions > * { width: 100%; } .skill-test-grid { grid-template-columns: 1fr; } .skill-test-grid li:nth-child(2) { border-top: 1px solid color-mix(in srgb, var(--sw-border) 70%, transparent); } }
</style>
