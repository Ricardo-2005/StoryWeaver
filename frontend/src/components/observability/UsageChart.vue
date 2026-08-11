<script setup lang="ts">
import { BarChart, LineChart, PieChart, type BarSeriesOption, type LineSeriesOption, type PieSeriesOption } from 'echarts/charts'
import { AriaComponent, GridComponent, LegendComponent, TooltipComponent, type GridComponentOption, type LegendComponentOption, type TooltipComponentOption } from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

export type UsageChartOption = ComposeOption<
  BarSeriesOption | LineSeriesOption | PieSeriesOption | GridComponentOption | LegendComponentOption | TooltipComponentOption
>

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, AriaComponent, CanvasRenderer])

const props = defineProps<{ option: UsageChartOption; label: string }>()
const element = ref<globalThis.HTMLElement>()
let chart: ECharts | undefined
let resizeObserver: globalThis.ResizeObserver | undefined
let themeObserver: globalThis.MutationObserver | undefined

function render(): void {
  if (!chart) return
  chart.setOption({
    ...props.option,
    backgroundColor: 'transparent',
    aria: { enabled: true, decal: { show: true }, description: props.label },
  }, { notMerge: true })
}

onMounted(() => {
  if (!element.value) return
  chart = init(element.value, undefined, { renderer: 'canvas' })
  render()
  resizeObserver = new globalThis.ResizeObserver(() => chart?.resize())
  resizeObserver.observe(element.value)
  themeObserver = new globalThis.MutationObserver(() => render())
  themeObserver.observe(globalThis.document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
})

watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="element" class="usage-chart" role="img" :aria-label="label"></div>
</template>
