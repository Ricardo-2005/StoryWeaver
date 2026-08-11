<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ plan: Record<string, unknown> }>()
interface ScenePlan { title: string; goal: string; summary: string; mustInclude: string[]; mustAvoid: string[] }
function strings(value: unknown): string[] { return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [] }
function text(value: unknown): string { return typeof value === 'string' ? value : '' }
const title = computed(() => text(props.plan.chapterTitle))
const goal = computed(() => text(props.plan.chapterGoal))
const exitHook = computed(() => text(props.plan.exitHook))
const mustInclude = computed(() => strings(props.plan.mustInclude))
const mustAvoid = computed(() => strings(props.plan.mustAvoid))
const scenes = computed<ScenePlan[]>(() => Array.isArray(props.plan.scenes) ? props.plan.scenes.filter((value): value is Record<string, unknown> => Boolean(value) && typeof value === 'object').map((scene) => ({ title: text(scene.title), goal: text(scene.goal), summary: text(scene.summary), mustInclude: strings(scene.mustInclude), mustAvoid: strings(scene.mustAvoid) })) : [])
const hasPlan = computed(() => Object.keys(props.plan).length > 0)
</script>

<template>
  <section class="workflow-panel scene-plan-panel" aria-labelledby="plan-heading">
    <header><div><p class="eyebrow">Planner</p><h2 id="plan-heading">场景计划</h2></div><span class="status-pill">只读</span></header>
    <div v-if="!hasPlan" class="workflow-empty">Planner 尚未返回场景计划。</div>
    <template v-else>
      <div class="plan-overview"><h3>{{ title || '未命名计划' }}</h3><p>{{ goal || '后端没有返回章节目标。' }}</p></div>
      <div class="scene-list"><article v-for="(scene, index) in scenes" :key="`${index}-${scene.title}`"><span>Scene {{ index + 1 }}</span><h3>{{ scene.title || '未命名场景' }}</h3><strong>{{ scene.goal }}</strong><p>{{ scene.summary }}</p><div v-if="scene.mustInclude.length || scene.mustAvoid.length" class="plan-rules"><div v-if="scene.mustInclude.length"><small>Must Include</small><ul><li v-for="item in scene.mustInclude" :key="item">{{ item }}</li></ul></div><div v-if="scene.mustAvoid.length"><small>Must Avoid</small><ul><li v-for="item in scene.mustAvoid" :key="item">{{ item }}</li></ul></div></div></article></div>
      <div class="plan-footer-rules"><div><strong>全章必须包含</strong><span v-for="item in mustInclude" :key="item">{{ item }}</span><small v-if="!mustInclude.length">无</small></div><div><strong>全章必须避免</strong><span v-for="item in mustAvoid" :key="item">{{ item }}</span><small v-if="!mustAvoid.length">无</small></div></div>
      <div class="exit-hook"><span>章尾钩子</span><strong>{{ exitHook || '未返回' }}</strong></div>
      <p class="capability-note">后端启动接口不会在 PLAN_READY 暂停，也没有重新规划或保存计划端点，因此本阶段只能查看真实返回计划。</p>
    </template>
  </section>
</template>
