<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ title: string; modelValue: string; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue':[value:string]; openCanvas:[] }>()
const content = computed({get:()=>props.modelValue,set:(value:string)=>emit('update:modelValue',value)})
</script>
<template>
  <article class="writing-block">
    <header><div><span>本地 Writing Block</span><strong>{{ title }}</strong></div><button type="button" @click="$emit('openCanvas')">打开 Canvas</button></header>
    <textarea v-model="content" :readonly="readonly" rows="7" aria-label="Writing Block 内容" placeholder="直接输入片段；当前内容不会自动保存到正式资产。" />
    <footer>本地临时内容 · 尚未进入正典或章节版本</footer>
  </article>
</template>
