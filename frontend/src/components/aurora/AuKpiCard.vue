<template>
  <!--
    AuKpiCard — 学术云蓝 KPI 卡
    顶部 3px 渐变高光 / 渐变数字 / 趋势 chip / sparkline
  -->
  <div class="au-kpi" :class="`au-kpi-${tone}`">
    <div class="au-kpi-stripe" />
    <div class="au-kpi-row">
      <div class="au-kpi-main">
        <div class="au-kpi-label">{{ label }}</div>
        <div class="au-kpi-value-line">
          <span class="au-kpi-value au-grad-text">{{ formatted }}</span>
          <span v-if="unit" class="au-kpi-unit">{{ unit }}</span>
        </div>
        <div v-if="trend != null" class="au-kpi-trend" :class="trendClass">
          <svg viewBox="0 0 12 12" width="10" height="10" aria-hidden="true">
            <path
              v-if="trendDir > 0"
              d="M6 2L10 7H7v3H5V7H2L6 2z"
              fill="currentColor"
            />
            <path
              v-else-if="trendDir < 0"
              d="M6 10L2 5h3V2h2v3h3l-4 5z"
              fill="currentColor"
            />
            <circle v-else cx="6" cy="6" r="2.5" fill="currentColor" />
          </svg>
          <span>{{ Math.abs(trend) }}% {{ trendNote }}</span>
        </div>
      </div>

      <div v-if="sparkData && sparkData.length" class="au-kpi-spark" ref="sparkEl" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  label: String,
  value: { type: [Number, String], default: 0 },
  unit: String,
  trend: { type: Number, default: null },
  trendInvert: { type: Boolean, default: false },
  trendNote: { type: String, default: 'vs 昨日' },
  sparkData: { type: Array, default: () => [] },
  tone: { type: String, default: 'info' },
})

const sparkEl = ref(null)
let chart = null
let resizeFn = null

const formatted = computed(() => {
  const v = props.value
  if (typeof v !== 'number') return v
  if (v >= 1e6) return (v / 1e6).toFixed(1) + 'M'
  if (v >= 1e4) return (v / 1e4).toFixed(1) + '万'
  return v.toLocaleString('zh-CN')
})

const trendDir = computed(() => {
  if (props.trend == null) return 0
  if (props.trend > 0) return 1
  if (props.trend < 0) return -1
  return 0
})

const trendClass = computed(() => {
  const d = trendDir.value
  if (d === 0) return 'flat'
  const isGood = props.trendInvert ? d < 0 : d > 0
  return isGood ? 'good' : 'bad'
})

const sparkColor = computed(() => {
  switch (props.tone) {
    case 'success': return '#10B981'
    case 'warning': return '#F59E0B'
    case 'danger':  return '#EF4444'
    default:        return '#2563EB'
  }
})

function buildOption() {
  return {
    grid: { top: 4, left: 0, right: 0, bottom: 4 },
    xAxis: {
      type: 'category', show: false, boundaryGap: false,
      data: props.sparkData.map((_, i) => i),
    },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    tooltip: { show: false },
    series: [{
      type: 'line',
      data: props.sparkData,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: sparkColor.value, width: 1.8 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: sparkColor.value + '55' },
          { offset: 1, color: sparkColor.value + '00' },
        ]),
      },
    }],
  }
}

function render() {
  if (!sparkEl.value) return
  if (!chart) chart = echarts.init(sparkEl.value)
  chart.setOption(buildOption(), true)
}

onMounted(async () => {
  await nextTick()
  render()
  resizeFn = () => chart?.resize()
  window.addEventListener('resize', resizeFn)
})

onBeforeUnmount(() => {
  if (resizeFn) window.removeEventListener('resize', resizeFn)
  if (chart) { chart.dispose(); chart = null }
})

watch(() => [props.sparkData, props.tone], () => render(), { deep: true })
</script>

<style scoped>
.au-kpi {
  position: relative;
  background: var(--au-bg-surface);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-lg);
  padding: 16px 18px 14px;
  box-shadow: var(--au-shadow-sm);
  overflow: hidden;
  transition: box-shadow var(--au-dur-base) var(--au-ease),
              transform var(--au-dur-base) var(--au-ease);
}
.au-kpi:hover {
  box-shadow: var(--au-shadow-md);
  transform: translateY(-1px);
}

.au-kpi-stripe {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--au-grad-primary);
  opacity: 0.85;
}
.au-kpi-success .au-kpi-stripe { background: linear-gradient(90deg, #10B981, #34D399); }
.au-kpi-warning .au-kpi-stripe { background: linear-gradient(90deg, #F59E0B, #FBBF24); }
.au-kpi-danger  .au-kpi-stripe { background: linear-gradient(90deg, #DC2626, #F87171); }

.au-kpi-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.au-kpi-main { flex: 1; min-width: 0; }
.au-kpi-spark { width: 100px; height: 44px; flex-shrink: 0; }

.au-kpi-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--au-text-secondary);
  letter-spacing: 0.02em;
  margin-bottom: 6px;
}
.au-kpi-value-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
  line-height: 1;
}
.au-kpi-value {
  font-family: var(--au-font-num);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  font-feature-settings: var(--au-font-feat);
}
.au-kpi-unit {
  font-size: 12px;
  color: var(--au-text-tertiary);
}

.au-kpi-trend {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
}
.au-kpi-trend.good { color: var(--au-success); background: var(--au-success-soft); }
.au-kpi-trend.bad  { color: var(--au-danger);  background: var(--au-danger-soft); }
.au-kpi-trend.flat { color: var(--au-text-tertiary); background: var(--au-bg-hover); }
</style>
