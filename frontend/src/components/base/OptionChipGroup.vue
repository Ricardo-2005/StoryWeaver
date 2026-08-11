<script setup lang="ts">
import type { ProjectOption } from '@/features/projects/projectOptions'

const props = withDefaults(defineProps<{
  modelValue: string | undefined
  label: string
  options: ProjectOption<string>[]
  required?: boolean
}>(), { required: false })

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

function select(value: string): void {
  emit('update:modelValue', value)
}

function keydown(event: KeyboardEvent, index: number): void {
  const keys = ['ArrowLeft', 'ArrowUp', 'ArrowRight', 'ArrowDown']
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault()
    select(props.options[index]!.value)
    return
  }
  if (!keys.includes(event.key)) return
  event.preventDefault()
  const direction = event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1
  const nextIndex = (index + direction + props.options.length) % props.options.length
  const target = event.currentTarget as HTMLElement | null
  const next = target?.parentElement?.querySelector<HTMLButtonElement>(`[data-option-index="${nextIndex}"]`)
  select(props.options[nextIndex]!.value)
  next?.focus()
}
</script>

<template>
  <div class="option-chip-group" role="radiogroup" :aria-label="label" :aria-required="required || undefined">
    <button
      v-for="(option, index) in options"
      :key="option.value"
      class="option-chip"
      type="button"
      role="radio"
      :data-option-index="index"
      :aria-checked="modelValue === option.value"
      :tabindex="modelValue ? (modelValue === option.value ? 0 : -1) : (index === 0 ? 0 : -1)"
      @click="select(option.value)"
      @keydown="keydown($event, index)"
    >
      <span aria-hidden="true" class="option-chip__mark">{{ modelValue === option.value ? '✓' : '' }}</span>
      {{ option.label }}
    </button>
  </div>
</template>
