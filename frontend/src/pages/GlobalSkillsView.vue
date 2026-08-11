<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { computed } from 'vue'

import { globalSkillsApi } from '@/api/endpoints/globalSkills'
import ErrorState from '@/components/base/ErrorState.vue'
import LoadingState from '@/components/base/LoadingState.vue'
import { queryKeys } from '@/queries/keys'

const queryClient = useQueryClient()
const skillsQuery = useQuery({ queryKey: queryKeys.globalSkills, queryFn: globalSkillsApi.list })
const archiveMutation = useMutation({
  mutationFn: globalSkillsApi.archive,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.globalSkills }),
})
const activeSkills = computed(() => (skillsQuery.data.value ?? []).filter((skill) => skill.status !== 'ARCHIVED'))
const validatedSkills = computed(() => activeSkills.value.filter((skill) => skill.status === 'VALIDATED').length)

function archive(skillId: string): void {
  if (window.confirm('归档后不能再用于新项目绑定。确定继续吗？')) archiveMutation.mutate(skillId)
}
</script>

<template>
  <main class="page-container skill-library-page">
    <header class="page-header skill-library-hero">
      <div>
        <p class="eyebrow">Skill workshop</p>
        <h1 tabindex="-1">Skill 工坊</h1>
        <p>在项目之外管理可复用的基础 Skill 契约。只有通过校验的版本才可以绑定到项目。</p>
      </div>
      <div class="skill-library-hero__aside">
        <dl class="skill-library-stats" aria-label="Skill 概览">
          <div><dt>可见 Skill</dt><dd>{{ activeSkills.length }}</dd></div>
          <div><dt>已验证</dt><dd>{{ validatedSkills }}</dd></div>
        </dl>
        <div class="page-header-actions">
          <RouterLink class="sw-button sw-button--secondary" to="/skills/new">手动新建</RouterLink>
          <RouterLink class="sw-button sw-button--primary" to="/skills/forge">进入 Skill 熔炉</RouterLink>
        </div>
      </div>
    </header>

    <section class="skill-workshop-notice skill-library-principle" aria-label="Skill 工坊说明">
      <strong>契约先于绑定</strong>
      <span>候选内容必须经你确认并校验；项目绑定的是不可变版本快照，不会自动升级。</span>
    </section>

    <LoadingState v-if="skillsQuery.isPending.value" label="正在加载 Skill…" />
    <ErrorState v-else-if="skillsQuery.isError.value" :error="skillsQuery.error.value" @retry="skillsQuery.refetch()" />
    <section v-else class="skill-card-grid" aria-label="全局 Skill 列表">
      <article v-for="skill in activeSkills" :key="skill.id" class="skill-contract-card" :class="{ 'skill-contract-card--built-in': skill.scope === 'BUILT_IN' }">
        <div class="skill-contract-card__meta"><span class="skill-scope-label">{{ skill.scope === 'BUILT_IN' ? '内置基础契约' : '我的 Skill' }}</span><span :class="`status-badge status-badge--${skill.status.toLowerCase()}`">{{ skill.status }}</span></div>
        <h2>{{ skill.displayName }}</h2>
        <p>{{ skill.description }}</p>
        <small class="skill-contract-card__date">当前版本：{{ skill.currentVersionId ? '已固定' : '尚未生成' }} · 更新于 {{ new Date(skill.updatedAt).toLocaleDateString('zh-CN') }}</small>
        <div class="skill-contract-card__actions">
          <RouterLink :to="`/skills/${skill.id}`">查看契约</RouterLink>
          <button v-if="skill.scope !== 'BUILT_IN'" class="archive-action" type="button" :disabled="archiveMutation.isPending.value" @click="archive(skill.id)">归档</button>
        </div>
      </article>
      <div v-if="activeSkills.length === 0" class="empty-panel">还没有 Skill。你可以先用 Skill 熔炉从写作材料中生成候选契约。</div>
    </section>
  </main>
</template>

<style scoped>
.skill-library-page { padding-bottom: var(--sw-space-16); }
.skill-library-hero { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(300px, .65fr); padding: clamp(1.5rem, 4vw, 2.75rem); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-dialog); background: color-mix(in srgb, var(--sw-bg-surface) 88%, var(--sw-accent-soft)); box-shadow: 0 22px 60px color-mix(in srgb, var(--sw-accent) 10%, transparent); }
.skill-library-hero h1 { max-width: 12ch; font-size: clamp(2.5rem, 5vw, 4.5rem); line-height: 1.05; }
.skill-library-hero p:not(.eyebrow) { max-width: 52ch; margin-top: var(--sw-space-5); line-height: 1.75; }
.skill-library-hero__aside { display: grid; align-content: space-between; gap: var(--sw-space-8); }
.skill-library-stats { display: grid; grid-template-columns: repeat(2, 1fr); margin: 0; border-left: 1px solid var(--sw-border); }
.skill-library-stats div { padding-left: var(--sw-space-5); }
.skill-library-stats dt { color: var(--sw-text-muted); font-size: .75rem; }
.skill-library-stats dd { margin: .25rem 0 0; color: var(--sw-text-primary); font-family: var(--sw-font-serif); font-size: 2rem; line-height: 1; }
.skill-library-hero .page-header-actions { justify-content: flex-end; }
.skill-library-principle { position: relative; display: grid; grid-template-columns: minmax(150px, .35fr) minmax(0, 1fr); align-items: center; overflow: hidden; padding: var(--sw-space-5) var(--sw-space-6); border-color: color-mix(in srgb, var(--sw-accent) 28%, var(--sw-border)); background: var(--sw-bg-surface); }
.skill-library-principle::before { position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--sw-accent); content: ''; }
.skill-card-grid { grid-template-columns: repeat(12, minmax(0, 1fr)); gap: var(--sw-space-5); }
.skill-contract-card { grid-column: span 6; min-height: 260px; align-content: start; padding: clamp(1.25rem, 3vw, 1.75rem); transition: border-color 160ms ease, transform 160ms ease, box-shadow 160ms ease; }
.skill-contract-card:hover { border-color: var(--sw-border-strong); box-shadow: 0 14px 32px color-mix(in srgb, var(--sw-accent) 8%, transparent); transform: translateY(-2px); }
.skill-contract-card--built-in { grid-column: span 7; background: color-mix(in srgb, var(--sw-bg-surface) 92%, var(--sw-accent-soft)); }
.skill-contract-card--built-in + .skill-contract-card { grid-column: span 5; }
.skill-scope-label { color: var(--sw-accent); font-size: .75rem; font-weight: 700; letter-spacing: .04em; }
.skill-contract-card h2 { margin-top: var(--sw-space-4); font-family: var(--sw-font-serif); font-size: clamp(1.35rem, 2.2vw, 1.75rem); line-height: 1.25; }
.skill-contract-card p { max-width: 58ch; line-height: 1.7; }
.skill-contract-card__date { margin-top: auto; padding-top: var(--sw-space-4); }
.skill-contract-card__actions { margin-top: var(--sw-space-2); padding-top: var(--sw-space-4); border-top: 1px solid var(--sw-border); }
.skill-contract-card__actions a, .skill-contract-card__actions button { min-height: 36px; padding: 0 var(--sw-space-3); border: 1px solid var(--sw-border); border-radius: var(--sw-radius-control); background: var(--sw-bg-surface); color: var(--sw-accent); cursor: pointer; font: inherit; font-size: .82rem; font-weight: 700; text-decoration: none; }
.skill-contract-card__actions a { display: inline-flex; align-items: center; }
.skill-contract-card__actions a:hover { border-color: var(--sw-accent); background: var(--sw-accent-soft); }
.skill-contract-card__actions .archive-action { color: var(--sw-text-secondary); }
.skill-contract-card__actions button:active, .skill-contract-card__actions a:active { transform: translateY(1px); }
@media (prefers-reduced-motion: reduce) { .skill-contract-card { transition: none; } .skill-contract-card:hover { transform: none; } }
@media (max-width: 820px) { .skill-library-hero { grid-template-columns: 1fr; } .skill-library-hero__aside { gap: var(--sw-space-5); } .skill-library-hero .page-header-actions { justify-content: flex-start; } .skill-contract-card, .skill-contract-card--built-in, .skill-contract-card--built-in + .skill-contract-card { grid-column: 1 / -1; } }
@media (max-width: 560px) { .skill-library-principle { grid-template-columns: 1fr; } .skill-library-stats { border-left: 0; border-top: 1px solid var(--sw-border); padding-top: var(--sw-space-4); } .skill-library-stats div { padding-left: 0; } .skill-library-hero .page-header-actions > * { width: 100%; } }
</style>
