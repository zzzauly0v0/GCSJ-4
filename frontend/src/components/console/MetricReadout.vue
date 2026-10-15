<template>
  <!--
    MetricReadout — Primary KPI numeric display
    Features:
      - Large monospace value with CSS count-up animation
      - Unit subscript
      - Trend arrow + delta (color-coded for alarm context:
          up = bad for alert counts, good for confirmed)
      - Micro sparkline via ECharts
      - Stagger-ready via external CSS classes
  -->
  <div class="metric-readout" :class="[`status-${status}`, { 'has-spark': showSparkline }]">

    <!-- Header row: label + status chip -->
    <div class="mr-header">
      <span class="mr-label">{{ label }}</span>
      <span class="mr-chip" :class="`chip-${status}`">
        <span class="led" :class="ledClass" />
        {{ chipLabel }}
      </span>
    </div>

    <!-- Value row -->
    <div class="mr-value-row">
      <div class="mr-value-wrap">
        <span class="mr-value" :style="{ color: valueColor }">
          {{ displayValue }}
        </span>
        <span class="mr-unit">{{ unit }}</span>
      </div>

      <!-- Trend badge -->
      <div v-if="trend !== undefined" class="mr-trend" :class="trendClass">
        <span class="trend-arrow">{{ trendArrow }}</span>
        <span class="trend-delta">{{ Math.abs(trend) }}%</span>
        <span class="trend-period">{{ trendPeriod }}</span>
      </div>
    </div>

    <!-- Sparkline -->
    <div v-if="showSparkline" ref="sparkEl" class="mr-spark" />

    <!-- Bottom rule -->
    <div class="mr-rule" :style="{ background: valueColor }" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { registerStormTheme } from '@/utils/echartsTheme'
import { buildSparklineOption } from '@/utils/echartsTheme'

registerStormTheme()

const props = defineProps({
  /** Display label above the value */
  label: {
    type: String,
    required: true,
  },
  /** The numeric value to display */
  value: {
    type: [Number, String],
    default: 0,
  },
  /** Unit string appended after value */
  unit: {
    type: String,
    default: '',
  },
  /**
   * Status / color variant:
   *   'normal'  — crystal blue
   *   'warning' — lava orange
   *   'critical'— alert red
   *   'success' — green
   */
  status: {
    type: String,
    default: 'normal',
    validator: v => ['normal', 'warning', 'critical', 'success', 'offline'].includes(v),
  },
  /**
   * Trend percentage vs comparison period.
   * Positive = increase, negative = decrease.
   * Semantics depend on context (caller sets 'up is bad' for alerts).
   */
  trend: {
    type: Number,
    default: undefined,
  },
  /** Whether a trend increase is bad (alert counts, etc.) */
  trendInvertSemantics: {
    type: Boolean,
    default: false,
  },
  /** Comparison period label for trend */
  trendPeriod: {
    type: String,
    default: '较昨日',
  },
  /** Show sparkline chart */
  showSparkline: {
    type: Boolean,
    default: true,
  },
  /** Sparkline data array */
  sparkData: {
    type: Array,
    default: () => Array.from({ length: 12 }, () => Math.round(Math.random() * 40 + 10)),
  },
  /** Override chip label */
  chipOverride: {
    type: String,
    default: undefined,
  },
})

// ---- Derived styles ----
const valueColor = computed(() => {
  const map = {
    normal:   'var(--signal-crystal)',
    warning:  'var(--signal-lava)',
    critical: 'var(--signal-alert)',
    success:  '#3fb950',
    offline:  'var(--text-tertiary)',
  }
  return map[props.status] || 'var(--signal-crystal)'
})

const ledClass = computed(() => {
  const map = {
    normal:   'led-online',
    warning:  'led-warning',
    critical: 'led-alert',
    success:  'led-online',
    offline:  'led-offline',
  }
  return map[props.status] || 'led-online'
})

const chipLabel = computed(() => {
  if (props.chipOverride) return props.chipOverride
  const map = { normal: 'NOMINAL', warning: 'ELEVATED', critical: 'CRITICAL', success: 'OK', offline: 'OFFLINE' }
  return map[props.status] || 'LIVE'
})

const trendArrow = computed(() => (props.trend >= 0) ? '▲' : '▼')
const trendClass = computed(() => {
  if (props.trend === undefined) return ''
  const isUp = props.trend >= 0
  const isBad = props.trendInvertSemantics ? isUp : !isUp
  return isBad ? 'trend-bad' : 'trend-good'
})

// ---- Value display (pad to 3 digits for fixed-width) ----
const displayValue = computed(() => {
  const n = Number(props.value)
  if (isNaN(n)) return String(props.value)
  if (Number.isInteger(n)) return String(n).padStart(typeof props.value === 'number' ? 1 : 1, '0')
  return n.toFixed(1)
})

// ---- Sparkline ----
const sparkEl = ref(null)
let sparkInst = null

function initSpark() {
  if (!sparkEl.value || !props.showSparkline) return
  if (sparkInst) sparkInst.dispose()
  sparkInst = echarts.init(sparkEl.value, 'stormwatch')
  const color = {
    normal: '#00E5FF', warning: '#FF6A1A', critical: '#FF2D2D', success: '#3fb950', offline: '#3D5A73'
  }[props.status] || '#00E5FF'
  sparkInst.setOption(buildSparklineOption(props.sparkData, color))
}

onMounted(() => {
  initSpark()
})
onBeforeUnmount(() => {
  if (sparkInst) {
    sparkInst.dispose()
    sparkInst = null
  }
})
watch(() => [props.sparkData, props.status], () => {
  initSpark()
}, { deep: true })
</script>

<style scoped>
/* ================================================================
   READOUT CONTAINER
================================================================ */
.metric-readout {
  position: relative;
  padding: 12px 14px 10px;
  background: var(--bg-panel);
  border: 1px solid var(--border-panel);
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow: hidden;
  transition: border-color 200ms ease, box-shadow 200ms ease;
  clip-path: polygon(
    0 0,
    calc(100% - 8px) 0,
    100% 8px,
    100% 100%,
    0 100%
  );
}
.metric-readout.status-warning {
  border-color: rgba(255, 106, 26, 0.30);
  box-shadow: 0 0 16px rgba(255, 106, 26, 0.08);
}
.metric-readout.status-critical {
  border-color: rgba(255, 45, 45, 0.35);
  box-shadow: 0 0 20px rgba(255, 45, 45, 0.12);
  animation: glow-breathe 2400ms ease-in-out infinite;
  animation-delay: 1400ms;
}

/* ================================================================
   HEADER
================================================================ */
.mr-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mr-label {
  font-family: var(--font-ui);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}

.mr-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  padding: 1px 6px;
  border-radius: 1px;
  flex-shrink: 0;
}
.chip-normal   { color: var(--signal-crystal); background: var(--signal-crystal-dim); border: 1px solid rgba(0,229,255,0.18); }
.chip-warning  { color: var(--signal-lava); background: var(--signal-lava-dim); border: 1px solid rgba(255,106,26,0.22); }
.chip-critical { color: var(--signal-alert); background: var(--signal-alert-dim); border: 1px solid rgba(255,45,45,0.25); }
.chip-success  { color: #3fb950; background: rgba(63,185,80,0.12); border: 1px solid rgba(63,185,80,0.22); }
.chip-offline  { color: var(--text-tertiary); background: transparent; border: 1px solid var(--border-separator); }

/* ================================================================
   VALUE ROW
================================================================ */
.mr-value-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.mr-value-wrap {
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.mr-value {
  font-family: var(--font-mono);
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.01em;
  font-feature-settings: "tnum" 1, "zero" 1;
  /* Count-up entry animation */
  animation: num-settle 350ms var(--ease-power-on) both;
  animation-delay: var(--reveal-delay, 900ms);
  will-change: transform, opacity, filter;
}

.mr-unit {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
  margin-left: 2px;
  padding-bottom: 3px;
}

/* ================================================================
   TREND
================================================================ */
.mr-trend {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 2px 7px;
  border-radius: 1px;
  flex-shrink: 0;
  margin-bottom: 3px;
}
.trend-bad {
  color: var(--signal-alert);
  background: var(--signal-alert-dim);
  border: 1px solid rgba(255, 45, 45, 0.20);
}
.trend-good {
  color: #3fb950;
  background: rgba(63, 185, 80, 0.10);
  border: 1px solid rgba(63, 185, 80, 0.20);
}

.trend-arrow {
  font-size: 9px;
  line-height: 1;
}
.trend-delta {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
}
.trend-period {
  font-family: var(--font-ui);
  font-size: 9px;
  color: var(--text-tertiary);
  margin-left: 2px;
}

/* ================================================================
   SPARKLINE
================================================================ */
.mr-spark {
  width: 100%;
  height: 36px;
  flex-shrink: 0;
}

/* ================================================================
   BOTTOM RULE — thin colored line under value
================================================================ */
.mr-rule {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  opacity: 0.40;
  transition: opacity 200ms ease;
}
.metric-readout:hover .mr-rule {
  opacity: 0.80;
}
</style>
