<template>
  <div class="monitor-page">
    <!-- 顶部:传感器选择卡片 + 当前指标卡 -->
    <div class="top-bar">
      <div class="gcsj-card sensor-picker">
        <div class="picker-head">
          <div class="head-title">
            <el-icon><Cpu /></el-icon><span>传感器</span>
            <span class="count">{{ sensors.length }}</span>
          </div>
          <el-input v-model="search" placeholder="搜索传感器" :prefix-icon="Search" size="small" style="width: 200px" />
        </div>
        <div class="picker-grid">
          <div
            v-for="s in filteredSensors"
            :key="s.id"
            class="picker-cell"
            :class="{ active: filter.sensorId === s.id, off: s.status !== 1 }"
            @click="onPickSensor(s)"
          >
            <div class="cell-head">
              <span class="cell-dot" :class="s.status === 1 ? 'on' : 'off'" />
              <span class="cell-name">{{ s.name }}</span>
            </div>
            <div class="cell-meta">
              <span class="cell-type">{{ typeLabel(s.type) }}</span>
              <span class="cell-code">{{ s.code }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 当前传感器详情卡 -->
      <div class="gcsj-card current">
        <div v-if="currentSensor" class="current-body">
          <div class="cur-head">
            <div class="cur-name">{{ currentSensor.name }}</div>
            <span class="cur-status" :class="currentSensor.status === 1 ? 'on' : 'off'">
              <span class="led" />
              {{ currentSensor.status === 1 ? 'ONLINE' : 'OFFLINE' }}
            </span>
          </div>
          <div class="cur-meta">
            <div><span class="lbl">编码</span>{{ currentSensor.code }}</div>
            <div><span class="lbl">类型</span>{{ typeLabel(currentSensor.type) }}</div>
            <div><span class="lbl">位置</span>{{ currentSensor.longitude?.toFixed(3) }}, {{ currentSensor.latitude?.toFixed(3) }}</div>
            <div><span class="lbl">高程</span>{{ currentSensor.elevation || '-' }} m</div>
          </div>
          <div class="cur-stats">
            <div class="cur-stat" v-for="m in liveMetrics" :key="m.key">
              <div class="m-value num-mono" :style="{ color: m.color }">{{ m.value }}</div>
              <div class="m-label">{{ m.label }}<span class="m-unit">/ {{ m.unit }}</span></div>
            </div>
          </div>
        </div>
        <el-empty v-else description="请选择传感器" :image-size="60" />
      </div>
    </div>

    <!-- 中部:筛选条 -->
    <div class="gcsj-card filter-bar">
      <el-form inline :model="filter" size="default">
        <el-form-item label="指标">
          <el-radio-group v-model="filter.indicator" @change="loadSeries">
            <el-radio-button value="RAINFALL_HOURLY">降雨量</el-radio-button>
            <el-radio-button value="DISPLACEMENT_24H">位移</el-radio-button>
            <el-radio-button value="SOIL_MOISTURE">土壤含水率</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="时间">
          <el-radio-group v-model="rangeKey" @change="onRangeKey">
            <el-radio-button value="1h">近 1 小时</el-radio-button>
            <el-radio-button value="6h">近 6 小时</el-radio-button>
            <el-radio-button value="24h">近 24 小时</el-radio-button>
            <el-radio-button value="7d">近 7 天</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Refresh" @click="loadSeries">刷新</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 下部:图表 + 列表 -->
    <div class="charts-row">
      <div class="gcsj-card chart-card">
        <div class="gcsj-card-header">
          <div class="title">{{ indicatorLabel }} 时间序列</div>
          <div class="header-extra">
            <span class="legend-item"><span class="dot" style="background:#3B82F6"/>实测</span>
            <span class="legend-item"><span class="dot dashed" style="background:#F59E0B"/>阈值</span>
          </div>
        </div>
        <div ref="chartEl" class="chart-main" v-loading="loading" />
      </div>

      <div class="gcsj-card list-card">
        <div class="gcsj-card-header">
          <div class="title">最新观测</div>
          <span class="text-3 fs-12">实时刷新</span>
        </div>
        <div class="latest-list">
          <div v-for="o in latest" :key="o.observedAt + '-' + o.sensorId" class="latest-item" :class="{ abnormal: o.abnormal }">
            <div class="li-left">
              <div class="li-time num-mono">{{ formatTime(o.observedAt) }}</div>
              <div class="li-sensor">传感器#{{ o.sensorId }}</div>
            </div>
            <div class="li-mid">
              <div class="li-indicator">{{ indicatorBrief(o.indicator) }}</div>
              <div class="li-value num-mono" :class="o.abnormal ? 'danger' : ''">
                {{ o.value }} <span class="li-unit">{{ o.unit }}</span>
              </div>
            </div>
            <div class="li-right">
              <span v-if="o.abnormal" class="badge danger">异常</span>
              <span v-else class="badge ok">正常</span>
            </div>
          </div>
          <el-empty v-if="!latest.length" description="暂无数据" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { Cpu, Search, Refresh } from '@element-plus/icons-vue'
import { useEcharts } from '@/hooks/useEcharts'
import { apiSensorAll } from '@/api/sensor'
import { apiObservationSeries, apiObservationLatest } from '@/api/observation'

const chartEl = ref()
const chart = useEcharts(chartEl)

const sensors = ref([])
const search = ref('')
const latest = ref([])
const loading = ref(false)

const rangeKey = ref('24h')
const filter = reactive({
  sensorId: null,
  indicator: 'RAINFALL_HOURLY',
  range: [dayjs().subtract(24, 'hour').toISOString(), dayjs().toISOString()]
})

const TYPE_MAP = {
  rain_gauge: '雨量计',
  displacement: '位移监测',
  soil_moisture: '土壤含水率',
  weather_station: '气象站'
}
function typeLabel(t) { return TYPE_MAP[t] || t }
function indicatorBrief(i) { return ({ RAINFALL_HOURLY: '降雨', DISPLACEMENT_24H: '位移', SOIL_MOISTURE: '湿度' })[i] || i }

const indicatorLabel = computed(() => ({
  RAINFALL_HOURLY: '降雨量 (mm/h)',
  DISPLACEMENT_24H: '位移 (mm/24h)',
  SOIL_MOISTURE: '土壤含水率 (%)'
})[filter.indicator])

const filteredSensors = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return sensors.value
  return sensors.value.filter(s =>
    s.name?.toLowerCase().includes(q) || s.code?.toLowerCase().includes(q)
  )
})

const currentSensor = computed(() => sensors.value.find(s => s.id === filter.sensorId))

const liveMetrics = computed(() => {
  const last = latest.value.find(o => o.sensorId === filter.sensorId)
  if (!last) return [{ key: 'v', label: '当前值', value: '-', unit: '-', color: '#94A3B8' }]
  return [
    { key: 'v', label: '当前值', value: last.value, unit: last.unit || '-', color: last.abnormal ? '#EF4444' : '#22C55E' },
    { key: 'avg', label: '24h 均值', value: '23.4', unit: 'mm', color: '#38BDF8' },
    { key: 'max', label: '24h 峰值', value: '68.2', unit: 'mm', color: '#F59E0B' }
  ]
})

function formatTime(t) { return dayjs(t).format('HH:mm:ss') }

function onPickSensor(s) {
  filter.sensorId = s.id
  loadSeries()
}

function onRangeKey(k) {
  const map = { '1h': [1, 'hour'], '6h': [6, 'hour'], '24h': [24, 'hour'], '7d': [7, 'day'] }
  const [n, u] = map[k]
  filter.range = [dayjs().subtract(n, u).toISOString(), dayjs().toISOString()]
  loadSeries()
}

async function loadSeries() {
  if (!filter.sensorId) {
    chart.setOption({ series: [{ type: 'line', data: [] }] }, true)
    return
  }
  loading.value = true
  try {
    const data = await apiObservationSeries({
      sensorId: filter.sensorId,
      indicator: filter.indicator,
      from: filter.range[0],
      to: filter.range[1]
    })
    const sorted = [...data].sort((a, b) => new Date(a.observedAt) - new Date(b.observedAt))
    const threshold = ({ RAINFALL_HOURLY: 50, DISPLACEMENT_24H: 30, SOIL_MOISTURE: 85 })[filter.indicator]
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 30, left: 50, right: 16, bottom: 40 },
      xAxis: {
        type: 'category', boundaryGap: false,
        data: sorted.map(o => dayjs(o.observedAt).format('MM-DD HH:mm')),
        axisLine: { lineStyle: { color: '#CBD5E1' } },
        axisLabel: { color: '#94A3B8', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        axisLine: { show: false },
        axisLabel: { color: '#94A3B8', fontSize: 11 },
        splitLine: { lineStyle: { color: '#F1F5F9' } }
      },
      series: [
        {
          name: '观测',
          data: sorted.map(o => o.value),
          type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
          lineStyle: { color: '#3B82F6', width: 2.5 },
          itemStyle: { color: '#3B82F6' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(59, 130, 246, 0.3)' },
              { offset: 1, color: 'rgba(59, 130, 246, 0)' }
            ])
          },
          markLine: {
            silent: true,
            symbol: 'none',
            lineStyle: { color: '#F59E0B', type: 'dashed', width: 1.5 },
            data: [{ yAxis: threshold, label: { formatter: `阈值 ${threshold}`, position: 'end', color: '#F59E0B' } }]
          }
        }
      ]
    }, true)
  } finally { loading.value = false }
}

async function loadLatest() {
  latest.value = await apiObservationLatest({ limit: 30 })
}

let timer = null
onMounted(async () => {
  sensors.value = await apiSensorAll()
  if (sensors.value.length) filter.sensorId = sensors.value[0].id
  await Promise.all([loadSeries(), loadLatest()])
  // 每 30s 拉最新观测
  timer = setInterval(loadLatest, 30000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
.monitor-page {
  padding: 16px;
  display: flex; flex-direction: column;
  gap: 16px;
}

/* ====== 顶部 ====== */
.top-bar {
  display: grid; grid-template-columns: 1fr 380px; gap: 16px;
  min-height: 220px;
}
.sensor-picker {
  display: flex; flex-direction: column;
  .picker-head {
    display: flex; align-items: center; justify-content: space-between;
    padding: 14px 18px;
    border-bottom: 1px solid var(--border);
    .head-title {
      display: flex; align-items: center; gap: 8px;
      font-size: 14px; font-weight: 600; color: var(--text-1);
      .el-icon { color: var(--primary); }
      .count {
        background: var(--bg); padding: 0 8px; border-radius: 10px;
        font-size: 11px; color: var(--text-2); font-family: 'DIN Alternate', monospace;
      }
    }
  }
}
.picker-grid {
  flex: 1;
  display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
  padding: 12px 14px;
  overflow-y: auto;
  max-height: 260px;
}
.picker-cell {
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  background: #fff;
  transition: all 0.15s;
  &:hover { border-color: rgba(37, 99, 235, 0.4); }
  &.active {
    background: rgba(37, 99, 235, 0.06);
    border-color: var(--primary);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
  }
  &.off { opacity: 0.6; }
  .cell-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px;
    .cell-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
      &.on  { background: #22C55E; box-shadow: 0 0 6px #22C55E; }
      &.off { background: #94A3B8; }
    }
    .cell-name { font-size: 13px; color: var(--text-1); font-weight: 600;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
  }
  .cell-meta { display: flex; justify-content: space-between; font-size: 11px;
    .cell-type { color: var(--primary); }
    .cell-code { color: var(--text-3); font-family: 'DIN Alternate', monospace; }
  }
}

.current { padding: 0;
  .current-body { padding: 16px 20px; }
  .cur-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
    .cur-name { font-size: 16px; font-weight: 600; color: var(--text-1); }
    .cur-status {
      display: flex; align-items: center; gap: 4px;
      font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600;
      letter-spacing: 1px;
      .led { width: 6px; height: 6px; border-radius: 50%; }
      &.on  { background: rgba(34,197,94,0.1);  color: #16A34A;  .led { background: #22C55E; box-shadow: 0 0 6px #22C55E; } }
      &.off { background: rgba(148,163,184,0.1);color: #64748B;  .led { background: #94A3B8; } }
    }
  }
  .cur-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 6px;
    font-size: 12px; color: var(--text-2);
    margin-bottom: 16px;
    div { display: flex; }
    .lbl { color: var(--text-3); width: 40px; flex-shrink: 0; }
  }
  .cur-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px;
    padding-top: 12px; border-top: 1px dashed var(--border);
  }
  .cur-stat {
    text-align: center;
    .m-value { font-size: 22px; font-weight: 700; line-height: 1; }
    .m-label { font-size: 11px; color: var(--text-3); margin-top: 4px; }
    .m-unit  { color: var(--text-3); margin-left: 2px; }
  }
}

/* ====== 筛选 ====== */
.filter-bar { padding: 12px 16px;
  :deep(.el-form-item) { margin-bottom: 0; }
}

/* ====== 图表 + 列表 ====== */
.charts-row { display: grid; grid-template-columns: 1.5fr 1fr; gap: 16px; min-height: 420px; }
.chart-card .gcsj-card-header,
.list-card .gcsj-card-header { padding: 14px 18px; border-bottom: 1px solid var(--border);
  display: flex; align-items: center; justify-content: space-between;
  .title { font-size: 14px; font-weight: 600; color: var(--text-1);
    display: flex; align-items: center; gap: 8px;
    &::before { content: ''; width: 3px; height: 14px; background: var(--primary); border-radius: 2px; }
  }
  .header-extra { display: flex; gap: 12px;
    .legend-item { font-size: 11px; color: var(--text-3); display: flex; align-items: center; gap: 4px;
      .dot { width: 8px; height: 8px; border-radius: 50%; }
      .dot.dashed { width: 14px; height: 2px; border-radius: 1px; }
    }
  }
}
.chart-main { width: 100%; height: 360px; padding: 0 16px; }

.latest-list { padding: 8px 12px; max-height: 360px; overflow-y: auto; }
.latest-item {
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px dashed var(--border);
  &:last-child { border-bottom: none; }
  &.abnormal { background: rgba(239,68,68,0.04); }
  .li-left { width: 90px; flex-shrink: 0;
    .li-time { font-size: 13px; color: var(--text-1); }
    .li-sensor { font-size: 11px; color: var(--text-3); margin-top: 2px; }
  }
  .li-mid { flex: 1; min-width: 0;
    .li-indicator { font-size: 11px; color: var(--text-3); }
    .li-value { font-size: 16px; font-weight: 700; color: var(--text-1);
      &.danger { color: #EF4444; }
      .li-unit { font-size: 11px; color: var(--text-3); font-weight: 400; margin-left: 4px; }
    }
  }
  .li-right { flex-shrink: 0;
    .badge { font-size: 11px; padding: 2px 8px; border-radius: 10px;
      &.ok      { background: rgba(34,197,94,0.1); color: #16A34A; }
      &.danger  { background: rgba(239,68,68,0.1); color: #DC2626; }
    }
  }
}
</style>
