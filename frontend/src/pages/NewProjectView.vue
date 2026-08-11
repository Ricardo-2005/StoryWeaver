<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'

import { globalSkillsApi } from '@/api/endpoints/globalSkills'
import OptionChipGroup from '@/components/base/OptionChipGroup.vue'
import ProblemAlert from '@/components/base/ProblemAlert.vue'
import {
  audienceOptions,
  lengthOptions,
  moreGenreOptions,
  perspectiveOptions,
  primaryGenreOptions,
  type LengthType,
  type NarrativePerspective,
  type ProjectGenre,
  type TargetAudience,
} from '@/features/projects/projectOptions'
import { useCreateProjectMutation } from '@/queries/projects'
import { queryKeys } from '@/queries/keys'

const draftKey = 'storyweaver:create-project-draft:v1'
const router = useRouter()
const createMutation = useCreateProjectMutation()
const moreGenresOpen = ref(false)
const selectedBaseSkillVersionId = ref<string | null>(null)
const globalSkillsQuery = useQuery({
  queryKey: queryKeys.globalSkills,
  queryFn: globalSkillsApi.list,
  retry: false,
})

const form = reactive({
  name: '',
  description: '',
  genre: undefined as ProjectGenre | undefined,
  customGenre: '',
  targetAudience: 'GENERAL' as TargetAudience,
  narrativePerspective: 'THIRD_PERSON' as NarrativePerspective,
  lengthType: 'LONG_NOVEL' as LengthType,
  premise: '',
  authorIntent: '',
  currentFocus: '',
  worldRules: [] as string[],
  targetWordCount: undefined as number | undefined,
  chapterWordTarget: undefined as number | undefined,
})

const visibleGenreOptions = computed(() => moreGenresOpen.value ? [...primaryGenreOptions, ...moreGenreOptions] : primaryGenreOptions)
const bindableSkills = computed(() => (globalSkillsQuery.data.value ?? []).filter((skill) =>
  skill.status === 'VALIDATED' && skill.currentVersionId,
))
function matchesSelectedPreferences(skill: (typeof bindableSkills.value)[number]): boolean {
  const recommendation = skill.contract.recommendation as Partial<Record<'genres' | 'audiences' | 'perspectives' | 'lengthTypes', string[]>> | undefined
  if (!recommendation) return false
  const matches = (values: string[] | undefined, selected: string | undefined): boolean =>
    !values?.length || Boolean(selected && values.includes(selected))
  return matches(recommendation.genres, form.genre)
    && matches(recommendation.audiences, form.targetAudience)
    && matches(recommendation.perspectives, form.narrativePerspective)
    && matches(recommendation.lengthTypes, form.lengthType)
}
const recommendedSkill = computed(() => {
  if (!form.genre || !form.targetAudience || !form.narrativePerspective) return undefined
  return bindableSkills.value.find((skill) => skill.scope === 'BUILT_IN' && matchesSelectedPreferences(skill))
    ?? bindableSkills.value.find(matchesSelectedPreferences)
})
const canSubmit = computed(() => {
  const customGenreValid = form.genre !== 'CUSTOM' || form.customGenre.trim().length > 0
  return Boolean(form.name.trim() && form.genre && customGenreValid && form.premise.trim().length >= 10)
})

function optional(value: string): string | null {
  return value.trim() || null
}

function trimName(): void {
  form.name = form.name.trim()
}

function addWorldRule(): void {
  form.worldRules.push('')
}

function removeWorldRule(index: number): void {
  form.worldRules.splice(index, 1)
}

function restoreDraft(): void {
  const raw = sessionStorage.getItem(draftKey)
  if (!raw) return
  try {
    const draft = JSON.parse(raw) as Partial<typeof form>
    Object.assign(form, draft)
    if (draft.genre && !primaryGenreOptions.some((option) => option.value === draft.genre)) moreGenresOpen.value = true
  } catch {
    sessionStorage.removeItem(draftKey)
  }
}

watch(form, () => sessionStorage.setItem(draftKey, JSON.stringify(form)), { deep: true })
onMounted(restoreDraft)

async function submit(): Promise<void> {
  if (!canSubmit.value || !form.genre) return
  try {
    const project = await createMutation.mutateAsync({
      name: form.name.trim(),
      genre: form.genre,
      customGenre: form.genre === 'CUSTOM' ? optional(form.customGenre) : null,
      targetAudience: form.targetAudience,
      narrativePerspective: form.narrativePerspective,
      lengthType: form.lengthType,
      premise: form.premise.trim(),
      description: optional(form.description),
      authorIntent: optional(form.authorIntent),
      currentFocus: optional(form.currentFocus),
      worldRules: form.worldRules.map((rule) => rule.trim()).filter(Boolean),
      targetWordCount: form.targetWordCount || null,
      chapterWordTarget: form.chapterWordTarget || null,
      baseSkillVersionId: selectedBaseSkillVersionId.value,
    })
    sessionStorage.removeItem(draftKey)
    await router.replace(`/projects/${project.id}`)
  } catch {
    // Mutation state is rendered below; the session draft keeps every field for retry.
  }
}
</script>

<template>
  <main class="page-container page-container--narrow">
    <RouterLink class="back-link" to="/projects">← 返回项目</RouterLink>
    <header class="page-header">
      <div>
        <p class="eyebrow">New project</p>
        <h1 tabindex="-1">创建新项目</h1>
        <p>用几个简单选项确定作品的基础方向，之后都可以修改。</p>
      </div>
    </header>

    <section class="project-creation-methods" aria-labelledby="creation-method-title">
      <div><p class="eyebrow">Creation method</p><h2 id="creation-method-title">选择创建方式</h2></div>
      <a class="creation-method-card is-active" href="#blank-project-form"><strong>从零开始</strong><span>填写创作偏好和故事构想，创建空白项目。</span></a>
      <RouterLink class="creation-method-card" to="/projects/import/txt"><strong>导入 TXT 书籍</strong><span>先确认编码与章节，再创建 Project、Chapter 和 ChapterVersion。</span><small>仅 .txt · 最大 20 MB</small></RouterLink>
    </section>

    <ProblemAlert v-if="createMutation.isError.value" :error="createMutation.error.value" />

    <form id="blank-project-form" class="sw-form project-form" @submit.prevent="submit">
      <section class="form-section">
        <span class="step-number">01</span>
        <div><h2>基础信息</h2><p>名称用于项目识别，简介用于项目列表展示。</p></div>
        <label class="form-field form-field--full">
          <span>项目名称 <b aria-hidden="true">*</b></span>
          <input v-model="form.name" name="name" maxlength="80" required autofocus placeholder="例如：雾港来信" @blur="trimName" />
        </label>
        <label class="form-field form-field--full">
          <span>项目简介</span>
          <textarea v-model="form.description" name="description" maxlength="300" rows="3" placeholder="用一两句话说明这部作品讲什么。" />
          <small>{{ form.description.length }} / 300</small>
        </label>
      </section>

      <section class="form-section">
        <span class="step-number">02</span>
        <div><h2>创作偏好</h2><p>这些基础定位只由你选择，不会被 AI 自动改写。</p></div>
        <div class="form-field form-field--full">
          <span id="genre-label">小说题材 <b aria-hidden="true">*</b></span>
          <OptionChipGroup v-model="form.genre" label="小说题材" :options="visibleGenreOptions" required />
          <button class="text-button project-more-button" type="button" :aria-expanded="moreGenresOpen" @click="moreGenresOpen = !moreGenresOpen">
            {{ moreGenresOpen ? '收起更多题材' : '更多题材' }} {{ moreGenresOpen ? '⌃' : '⌄' }}
          </button>
          <label v-if="form.genre === 'CUSTOM'" class="form-field project-custom-genre">
            <span>自定义题材名称 <b aria-hidden="true">*</b></span>
            <input v-model="form.customGenre" maxlength="20" required placeholder="最多 20 字" />
          </label>
        </div>
        <div class="form-field form-field--full">
          <span>目标读者</span>
          <OptionChipGroup v-model="form.targetAudience" label="目标读者" :options="audienceOptions" required />
        </div>
        <div class="form-field form-field--full">
          <span>作品视角</span>
          <OptionChipGroup v-model="form.narrativePerspective" label="作品视角" :options="perspectiveOptions" required />
        </div>
        <div class="form-field form-field--full">
          <span>篇幅长短</span>
          <OptionChipGroup v-model="form.lengthType" label="篇幅长短" :options="lengthOptions" required />
        </div>
      </section>

      <section class="form-section">
        <span class="step-number">03</span>
        <div><h2>故事构想</h2><p>将作为项目的初始创作上下文，不会替代项目简介。</p></div>
        <label class="form-field form-field--full">
          <span>故事构想 <b aria-hidden="true">*</b></span>
          <textarea v-model="form.premise" name="premise" minlength="10" maxlength="500" rows="7" required placeholder="例如：一个普通外卖员发现自己拥有超能力，从此卷入一场外太空阴谋……" />
          <small :class="{ 'field-error': form.premise.length > 0 && form.premise.trim().length < 10 }">{{ form.premise.length }} / 500（至少 10 字）</small>
        </label>
      </section>

      <section class="form-section foundation-skill-section">
        <span class="step-number">04</span>
        <div>
          <h2>基础 Skill 契约</h2>
          <p>可选。仅展示与你已明确选择的创作偏好相关的建议；不会自动绑定，也不会在日后静默升级。</p>
        </div>
        <div class="foundation-skill-options form-field--full">
          <label class="foundation-skill-choice">
            <input v-model="selectedBaseSkillVersionId" type="radio" :value="null" />
            <span><strong>暂不绑定</strong><small>先创建项目，之后可在 Skill 工坊绑定已验证版本。</small></span>
          </label>
          <label v-if="recommendedSkill" class="foundation-skill-choice" :class="{ 'is-recommended': selectedBaseSkillVersionId === recommendedSkill.currentVersionId }">
            <input v-model="selectedBaseSkillVersionId" type="radio" :value="recommendedSkill.currentVersionId" />
            <span><em>基于已选偏好推荐</em><strong>{{ recommendedSkill.displayName }}</strong><small>{{ recommendedSkill.description }}</small></span>
          </label>
          <label v-for="skill in bindableSkills.filter((item) => item.id !== recommendedSkill?.id)" :key="skill.id" class="foundation-skill-choice">
            <input v-model="selectedBaseSkillVersionId" type="radio" :value="skill.currentVersionId" />
            <span><strong>{{ skill.displayName }}</strong><small>{{ skill.description }}</small></span>
          </label>
          <p v-if="globalSkillsQuery.isPending.value" class="field-hint">正在加载可用的基础 Skill…</p>
          <p v-else-if="!bindableSkills.length" class="field-hint">尚无可绑定的已验证 Skill。</p>
          <RouterLink class="text-button" to="/skills/new">去 Skill 工坊熔炼基础契约</RouterLink>
        </div>
      </section>

      <details class="project-advanced">
        <summary><span>高级设置</span><small>可选，创建后仍可在项目设置中补充。</small></summary>
        <div class="project-advanced__body">
          <label class="form-field"><span>作者意图</span><textarea v-model="form.authorIntent" maxlength="3000" rows="4" placeholder="希望探索的主题、基调与边界" /></label>
          <label class="form-field"><span>当前创作焦点</span><textarea v-model="form.currentFocus" maxlength="2000" rows="3" placeholder="眼下最需要推进的内容" /></label>
          <fieldset class="world-rules-field"><legend>世界硬规则</legend><p>每条规则最多 500 字；不填写时不会自动生成。</p><div v-for="(_, index) in form.worldRules" :key="index" class="world-rule-row"><input v-model="form.worldRules[index]" maxlength="500" :aria-label="`世界硬规则 ${index + 1}`" /><button class="text-button" type="button" @click="removeWorldRule(index)">删除</button></div><button class="text-button" type="button" @click="addWorldRule">+ 添加规则</button></fieldset>
          <div class="form-row"><label class="form-field"><span>目标字数</span><input v-model.number="form.targetWordCount" type="number" min="1" placeholder="可选" /></label><label class="form-field"><span>单章字数</span><input v-model.number="form.chapterWordTarget" type="number" min="1" placeholder="可选" /></label></div>
        </div>
      </details>

      <div class="form-actions">
        <RouterLink class="sw-button sw-button--secondary" to="/projects">取消</RouterLink>
        <button class="sw-button sw-button--primary" type="submit" :disabled="createMutation.isPending.value || !canSubmit">
          {{ createMutation.isPending.value ? '正在创建项目…' : '创建项目并进入工作台' }}
        </button>
      </div>
    </form>
  </main>
</template>

<style scoped>
.project-creation-methods { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-block-end: 1.5rem; }
.project-creation-methods > div { grid-column: 1 / -1; }
.project-creation-methods h2 { margin: 0; }
.creation-method-card { display: grid; gap: .5rem; min-height: 130px; padding: 1.25rem; border: 1px solid var(--sw-border); border-radius: var(--sw-radius-card); background: var(--sw-bg-surface); color: inherit; text-decoration: none; }
.creation-method-card:hover, .creation-method-card:focus-visible { border-color: var(--sw-accent); }
.creation-method-card.is-active { box-shadow: inset 3px 0 var(--sw-accent); }
.creation-method-card span, .creation-method-card small { color: var(--sw-text-secondary); }
@media (max-width: 640px) { .project-creation-methods { grid-template-columns: 1fr; } }
</style>
