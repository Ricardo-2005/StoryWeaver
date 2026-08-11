<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ status: number }>()
const content = computed(() => {
  if (props.status === 403) return { title: '没有访问权限', detail: '当前账户不能访问这个页面。' }
  if (props.status === 404) return { title: '页面不存在', detail: '链接可能已失效，或页面已经移动。' }
  return { title: '服务暂时不可用', detail: '请稍后重试，或返回项目列表。' }
})
</script>

<template>
  <main id="main-content" class="status-page" tabindex="-1">
    <div class="status-code">{{ status }}</div>
    <h1 tabindex="-1">{{ content.title }}</h1>
    <p>{{ content.detail }}</p>
    <RouterLink class="sw-button sw-button--primary" to="/projects">返回项目</RouterLink>
  </main>
</template>
