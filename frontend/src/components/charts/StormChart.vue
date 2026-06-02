<template>
  <!--
    StormChart — Universal ECharts wrapper using the 'stormwatch' theme
    All charts in the application must use this component, never bare echarts.init().

    Dataviz-skill principles applied:
      - Insight-driven title (pass as prop, not "Data Over Time")
      - Axes always have units via xUnit/yUnit props
      - Tooltip: dark, monospace, non-occluding
      - Gridlines: ghost-thin, never chartjunk
      - Direct labels where feasible
      - Stormwatch color palette only — no raw hex in option objects
  -->
  <div class="storm-chart-wrap" :style="{ height: height }">
    <div v-if="title || subtitle" class="chart-header">
      <span class="chart-title">{{ title }}</span>
      <span v-if="subtitle" class="chart-subtitle">{{ subtitle }}</span>
    </div>
    <div ref="chartEl" class="chart-canvas" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { registerStormTheme } from '@/utils/echartsTheme'

registerStormTheme()

const props = defineProps({
  /** ECharts option object — should NOT include backgroundColor (handled by theme) */
  option: {
    type: Object,
    default: () => ({}),
  },
  /** Chart container height (CSS string) */
  height: {
    type: String,
    default: '100%',
  },
  /** Insight-driven title (conclusion, not description) */
  title: {
    type: String,
    default: '',
  },
  /** Subtitle: data source, period, caveats */
  subtitle: {
    type: String,
    default: '',
  },
})

const chartEl = ref(null)
let instance = null
let resizeFn = null

function init() {
  if (!chartEl.value) return
  if (instance) instance.dispose()
  instance = echarts.init(chartEl.value, 'stormwatch')
  instance.setOption(props.option, true)
}

onMounted(() => {
  init()
  resizeFn = () => instance?.resize()
  window.addEventListener('resize', resizeFn)
})

onBeforeUnmount(() => {
  if (resizeFn) window.removeEventListener('resize', resizeFn)
  if (instance) {
    instance.dispose()
    instance = null
  }
})

watch(() => props.option, (newOpt) => {
  if (instance) instance.setOption(newOpt, true)
}, { deep: true })

/** Expose instance for parent access */
defineExpose({ getInstance: () => instance })
</script>

<style scoped>
.storm-chart-wrap {
  display: flex;
  flex-direction: column;
  position: relative;
  width: 100%;
}

.chart-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 4px 6px 4px;
  flex-shrink: 0;
}
.chart-title {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.chart-subtitle {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

.chart-canvas {
  flex: 1;
  min-height: 0;
  width: 100%;
}
</style>
