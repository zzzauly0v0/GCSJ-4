/**
 * GCSJ STORMWATCH — ECharts Theme: "stormwatch"
 *
 * Design intent (dataviz-skill applied):
 *   - Data-ink ratio: minimal chrome, maximum signal
 *   - Color encodes meaning only (level 1-4, crystal for trends)
 *   - Tabular figures enforced via JetBrains Mono
 *   - Tooltips don't occlude data (smart positioning)
 *   - No 3D, no decorative gradients on axes
 *   - Gridlines: thin, low-opacity — "ghost" grid that helps without competing
 *   - Direct labels preferred; legends only when series > 3
 *
 * Usage:
 *   import * as echarts from 'echarts'
 *   import { registerStormTheme, STORM_COLORS } from '@/utils/echartsTheme'
 *   registerStormTheme()   // call once at app init
 *   echarts.init(el, 'stormwatch')
 */

import * as echarts from 'echarts'

// ================================================================
// COLOR PALETTE — semantic signal colors
// Order follows dataviz best practice: most important series first,
// distinguish by hue shift, not saturation blast.
// ================================================================
export const STORM_COLORS = {
  crystal:   '#00E5FF',   // primary data / trend lines
  crystalIce:'#7DF9FF',   // secondary highlight
  lava:      '#FF6A1A',   // orange/red level alerts
  lavaHot:   '#FF8C42',   // lava secondary
  hazard:    '#FFD60A',   // yellow level
  alert:     '#FF2D2D',   // critical red
  blue:      '#3B8BF0',   // info / level-1
  plasma:    '#D63AF9',   // tertiary accent
  dim1:      '#2A4A6A',   // muted series 1
  dim2:      '#1E3A4A',   // muted series 2
}

// Series color order for multi-series charts
const COLOR_PALETTE = [
  STORM_COLORS.crystal,
  STORM_COLORS.lava,
  STORM_COLORS.hazard,
  STORM_COLORS.alert,
  STORM_COLORS.blue,
  STORM_COLORS.plasma,
  STORM_COLORS.crystalIce,
  STORM_COLORS.lavaHot,
]

// ================================================================
// STORMWATCH ECHARTS THEME OBJECT
// ================================================================
const stormwatchTheme = {
  color: COLOR_PALETTE,

  backgroundColor: 'transparent',

  textStyle: {
    fontFamily: "'JetBrains Mono', 'IBM Plex Mono', 'Fira Code', monospace",
    color: '#7D9BB8',
    fontSize: 11,
  },

  title: {
    textStyle: {
      fontFamily: "'Orbitron', 'Rajdhani', sans-serif",
      color: '#E6F1FF',
      fontSize: 13,
      fontWeight: '600',
      letterSpacing: '0.08em',
    },
    subtextStyle: {
      fontFamily: "'JetBrains Mono', monospace",
      color: '#3D5A73',
      fontSize: 10,
    },
  },

  legend: {
    textStyle: {
      fontFamily: "'JetBrains Mono', monospace",
      color: '#7D9BB8',
      fontSize: 10,
    },
    pageTextStyle: { color: '#7D9BB8' },
    inactiveColor: '#2A3D52',
    itemWidth: 10,
    itemHeight: 6,
    icon: 'rect',
  },

  tooltip: {
    backgroundColor: 'rgba(10, 14, 26, 0.95)',
    borderColor: 'rgba(0, 229, 255, 0.30)',
    borderWidth: 1,
    textStyle: {
      fontFamily: "'JetBrains Mono', 'IBM Plex Mono', monospace",
      color: '#E6F1FF',
      fontSize: 11,
    },
    extraCssText: [
      'backdrop-filter: blur(8px)',
      'border-radius: 2px',
      'box-shadow: 0 0 0 1px rgba(0,229,255,0.18), 0 8px 32px rgba(0,0,0,0.7)',
      'padding: 10px 14px',
    ].join(';'),
    axisPointer: {
      lineStyle: {
        color: 'rgba(0, 229, 255, 0.30)',
        type: 'dashed',
        width: 1,
      },
      crossStyle: {
        color: 'rgba(0, 229, 255, 0.20)',
      },
      shadowStyle: {
        color: 'rgba(0, 229, 255, 0.04)',
      },
    },
  },

  grid: {
    borderColor: 'rgba(0, 229, 255, 0.08)',
  },

  categoryAxis: {
    axisLine: {
      show: true,
      lineStyle: { color: 'rgba(0, 229, 255, 0.15)', width: 1 },
    },
    axisTick: { show: false },
    axisLabel: {
      color: '#3D5A73',
      fontFamily: "'JetBrains Mono', monospace",
      fontSize: 10,
    },
    splitLine: {
      show: false,
    },
    splitArea: { show: false },
  },

  valueAxis: {
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: {
      color: '#3D5A73',
      fontFamily: "'JetBrains Mono', monospace",
      fontSize: 10,
    },
    splitLine: {
      show: true,
      lineStyle: {
        color: 'rgba(0, 229, 255, 0.07)',
        type: 'dashed',
        width: 1,
      },
    },
    splitArea: { show: false },
  },

  timeAxis: {
    axisLine: {
      lineStyle: { color: 'rgba(0, 229, 255, 0.15)', width: 1 },
    },
    axisTick: { show: false },
    axisLabel: {
      color: '#3D5A73',
      fontFamily: "'JetBrains Mono', monospace",
      fontSize: 10,
    },
    splitLine: {
      show: true,
      lineStyle: {
        color: 'rgba(0, 229, 255, 0.07)',
        type: 'dashed',
        width: 1,
      },
    },
  },

  line: {
    smooth: false,
    symbol: 'none',
    symbolSize: 5,
    lineStyle: { width: 1.8 },
    emphasis: {
      lineStyle: { width: 2.5 },
    },
  },

  bar: {
    itemStyle: {
      borderRadius: [1, 1, 0, 0],
    },
    emphasis: {
      itemStyle: {
        shadowBlur: 8,
        shadowColor: 'rgba(0, 229, 255, 0.30)',
      },
    },
  },

  pie: {
    itemStyle: {
      borderColor: 'rgba(10, 14, 26, 0.80)',
      borderWidth: 2,
    },
    label: {
      color: '#7D9BB8',
      fontFamily: "'JetBrains Mono', monospace",
      fontSize: 10,
    },
    labelLine: {
      lineStyle: { color: 'rgba(0, 229, 255, 0.20)' },
    },
  },

  scatter: {
    itemStyle: {
      borderWidth: 1,
      borderColor: 'rgba(255,255,255,0.15)',
    },
  },

  gauge: {
    title: {
      color: '#7D9BB8',
      fontFamily: "'JetBrains Mono', monospace",
    },
    detail: {
      color: '#00E5FF',
      fontFamily: "'JetBrains Mono', monospace",
    },
  },

  map: {
    itemStyle: {
      borderColor: 'rgba(0, 229, 255, 0.20)',
      borderWidth: 0.8,
      areaColor: 'rgba(15, 22, 34, 0.80)',
    },
    emphasis: {
      label: { color: '#E6F1FF' },
      itemStyle: { areaColor: 'rgba(0, 229, 255, 0.15)' },
    },
  },
}

// ================================================================
// HELPER: Build glowing line series config
// ================================================================
export function buildGlowLineSeries(name, data, color, areaOpacity = 0.15) {
  return {
    name,
    type: 'line',
    data,
    smooth: true,
    symbol: 'none',
    lineStyle: {
      color,
      width: 2,
      shadowBlur: 8,
      shadowColor: color + '60',
    },
    areaStyle: areaOpacity > 0
      ? {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + Math.round(areaOpacity * 255).toString(16).padStart(2, '0') },
            { offset: 1, color: color + '00' },
          ]),
        }
      : undefined,
    emphasis: {
      lineStyle: {
        width: 3,
        shadowBlur: 16,
        shadowColor: color + '80',
      },
    },
  }
}

// ================================================================
// HELPER: Standard dark grid config (use in all charts)
// ================================================================
export function buildStormGrid(overrides = {}) {
  return {
    top: 32,
    left: 48,
    right: 16,
    bottom: 28,
    containLabel: false,
    ...overrides,
  }
}

// ================================================================
// HELPER: Warning level stacked area series
// ================================================================
export function buildAlertLevelSeries(buckets, levelMeta) {
  return [4, 3, 2, 1].map(lv => ({
    name: levelMeta(lv).label,
    type: 'line',
    stack: 'total',
    smooth: true,
    symbol: 'none',
    lineStyle: {
      color: levelMeta(lv).stormColor,
      width: 1.5,
    },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: levelMeta(lv).stormColor + '55' },
        { offset: 1, color: levelMeta(lv).stormColor + '08' },
      ]),
    },
    data: buckets.map(b => b[lv] || 0),
  }))
}

// ================================================================
// HELPER: Sparkline config (minimal, for KPI cards)
// ================================================================
export function buildSparklineOption(data, color) {
  return {
    backgroundColor: 'transparent',
    grid: { top: 2, left: 0, right: 0, bottom: 2 },
    xAxis: { type: 'category', show: false, data },
    yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
    series: [{
      type: 'line',
      data,
      smooth: true,
      symbol: 'none',
      lineStyle: {
        color,
        width: 1.5,
        shadowBlur: 6,
        shadowColor: color + '60',
      },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: color + '35' },
          { offset: 1, color: color + '00' },
        ]),
      },
    }],
  }
}

// ================================================================
// REGISTER — call once at app startup
// ================================================================
let registered = false
export function registerStormTheme() {
  if (registered) return
  echarts.registerTheme('stormwatch', stormwatchTheme)
  registered = true
}
