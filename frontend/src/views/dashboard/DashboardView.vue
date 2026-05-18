<template>
  <div class="dash-root dashboard">
    <!-- ============ 顶部标题栏 ============ -->
    <div class="dash-header">
      <div class="dash-header-left">
        <h1 class="dash-title">气象地质灾害监测预警中心</h1>
        <span class="dash-sub">{{ date }} · {{ weekday }}</span>
      </div>
      <div class="dash-header-right">
        <div class="weather">
          <el-icon><Sunny /></el-icon>
          <span>晴 23℃</span>
        </div>
        <div class="time num-mono">{{ time }}</div>
        <span class="env-tag">{{ envLabel }}</span>
      </div>
    </div>

    <!-- ============ 顶部统计卡 ============ -->
    <div class="kpi-row">
      <div v-for="k in kpis" :key="k.key" class="kpi-card" :style="{'--kpi-color': k.color}">
        <div class="kpi-icon"><el-icon><component :is="k.icon" /></el-icon></div>
        <div class="kpi-body">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">
            <span class="num num-mono">{{ k.value }}</span>
            <span class="unit">{{ k.unit }}</span>
          </div>
          <div class="kpi-trend" :class="k.trend > 0 ? 'up' : 'down'">
            <el-icon><CaretTop v-if="k.trend > 0" /><CaretBottom v-else /></el-icon>
            <span>{{ Math.abs(k.trend) }}%</span>
            <span class="trend-label">较昨日</span>
          </div>
        </div>
        <div class="kpi-spark" :ref="el => sparkRefs[k.key] = el" />
      </div>
    </div>

    <!-- ============ 主体区 ============ -->
    <div class="dash-body">

      <!-- 左列 -->
      <div class="col col-l">
        <!-- 实时预警 -->
        <div class="dash-panel panel-alerts">
          <div class="dash-panel-title">
            <span class="dot" />实时预警
            <span class="extra">共 {{ alertStore.latest.length }} 条</span>
          </div>
          <div class="alert-list dash-scroll">
            <div
              v-for="a in alertStore.latest"
              :key="a.id"
              class="alert-card"
              :class="`lvl-${a.level}`"
              @click="onAlertClick(a)"
            >
              <div class="alert-stripe" />
              <div class="alert-body">
                <div class="alert-row1">
                  <span class="lvl-tag" :class="`lvl-${a.level}`">{{ levelMeta(a.level).label }}</span>
                  <span class="alert-title">{{ a.title }}</span>
                </div>
                <div class="alert-row2">
                  <span class="code">{{ a.code }}</span>
                  <span class="time">{{ formatDateTime(a.triggeredAt) }}</span>
                </div>
              </div>
              <div v-if="a.status === 1" class="alert-status pending">未发送</div>
              <div v-else-if="a.status === 2" class="alert-status sent">已发送</div>
            </div>
            <el-empty v-if="!alertStore.latest.length" description="暂无预警" :image-size="60" />
          </div>
        </div>

        <!-- 灾种分布 -->
        <div class="dash-panel panel-types">
          <div class="dash-panel-title"><span class="dot" />灾种分布</div>
          <div ref="pieEl" class="chart" />
        </div>
      </div>

      <!-- 中列 - 地图 -->
      <div class="col col-c">
        <div class="dash-panel panel-map">
          <div ref="mapEl" class="map-el" />

          <!-- 图层切换 -->
          <div class="map-layers">
            <div
              v-for="l in mapLayers"
              :key="l.key"
              class="layer-item"
              :class="{ active: l.visible }"
              @click="toggleLayer(l)"
            >
              <span class="layer-dot" :style="{ background: l.color }" />
              <span class="layer-name">{{ l.name }}</span>
              <span class="layer-count">{{ l.count }}</span>
            </div>
          </div>

          <!-- 图例 -->
          <div class="map-legend">
            <div class="lg-title">预警等级</div>
            <div class="lg-row" v-for="lv in [1,2,3,4]" :key="lv">
              <span class="lg-dot" :style="{ background: levelMeta(lv).color }" />
              <span>{{ levelMeta(lv).label }}</span>
            </div>
          </div>

          <!-- 坐标信息 -->
          <div class="map-corner-info">
            <div>EPSG:3857 · WebGIS</div>
            <div>{{ mapStore.center[0].toFixed(2) }}°E · {{ mapStore.center[1].toFixed(2) }}°N</div>
          </div>
        </div>
      </div>

      <!-- 右列 -->
      <div class="col col-r">
        <!-- 等级分布 -->
        <div class="dash-panel panel-pie">
          <div class="dash-panel-title"><span class="dot" />预警等级分布</div>
          <div class="lvl-stats">
            <div v-for="lv in [4,3,2,1]" :key="lv" class="lvl-stat">
              <div class="lvl-ring" :style="{ '--c': levelMeta(lv).color, '--p': lvlPercent(lv) }">
                <span class="lvl-num num-mono">{{ levelCount(lv) }}</span>
              </div>
              <div class="lvl-label" :style="{ color: levelMeta(lv).color }">{{ levelMeta(lv).label }}预警</div>
            </div>
          </div>
        </div>

        <!-- 24h 趋势 -->
        <div class="dash-panel panel-trend">
          <div class="dash-panel-title">
            <span class="dot" />24h 观测趋势
            <span class="extra">实时刷新</span>
          </div>
          <div ref="lineEl" class="chart" />
        </div>

        <!-- 传感器状态 -->
        <div class="dash-panel panel-sensors">
          <div class="dash-panel-title">
            <span class="dot" />传感器状态
            <span class="extra">{{ sensorStat.online }} / {{ sensorStat.total }}</span>
          </div>
          <div class="sensor-grid">
            <div v-for="s in sensors.slice(0, 16)" :key="s.id" class="sensor-cell" :class="s.status === 1 ? 'on' : 'off'">
              <el-tooltip :content="`${s.name} - ${s.code}`" placement="top">
                <span class="cell-dot" />
              </el-tooltip>
            </div>
          </div>
          <div class="sensor-legend">
            <span><span class="dot on" />在线 {{ sensorStat.online }}</span>
            <span><span class="dot off" />离线 {{ sensorStat.offline }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import { CaretTop, CaretBottom, Sunny } from '@element-plus/icons-vue'

import { useMap } from '@/hooks/useMap'
import { useEcharts } from '@/hooks/useEcharts'
import { useAlertStore } from '@/store/alert'
import { useMapStore } from '@/store/map'
import { useWS } from '@/utils/ws'
import { apiSensorGeoJson, apiSensorAll } from '@/api/sensor'
import { apiAlertGeoJson } from '@/api/alert'
import { apiDisasterGeoJson } from '@/api/disaster'
import { apiObservationLatest } from '@/api/observation'
import { formatDateTime, levelMeta } from '@/utils/format'

const mapEl = ref()
const pieEl = ref()
const lineEl = ref()
const sparkRefs = reactive({})

const alertStore = useAlertStore()
const mapStore = useMapStore()
const ws = useWS()

const { loadGeoJson, setLayerVisible, flyTo } = useMap(mapEl, { center: [104, 35], zoom: 5 })
const pie = useEcharts(pieEl)
const line = useEcharts(lineEl)

const sensors = ref([])

const envLabel = import.meta.env.MODE === 'production' ? 'PROD' : 'DEV'

/* ---------- 时钟 ---------- */
const time = ref(dayjs().format('HH:mm:ss'))
const date = ref(dayjs().format('YYYY-MM-DD'))
const weekday = ref('星期' + ['日','一','二','三','四','五','六'][dayjs().day()])
let clockTimer = null

/* ---------- KPI ---------- */
const kpis = computed(() => {
  const total = alertStore.latest.length
  const red = alertStore.latest.filter(a => a.level === 4).length
  return [
    { key: 'total',  label: '今日预警', value: total, unit: '条', trend: 12, color: '#0EA5E9', icon: 'Bell' },
    { key: 'red',    label: '红色预警', value: red,   unit: '条', trend: -8, color: '#EF4444', icon: 'Warning' },
    { key: 'sensor', label: '在线传感器', value: sensorStat.value.online, unit: `/${sensorStat.value.total}`, trend: 3, color: '#10B981', icon: 'Cpu' },
    { key: 'event',  label: '灾害事件', value: 8, unit: '起', trend: 5, color: '#F59E0B', icon: 'DataLine' }
  ]
})

const sensorStat = computed(() => {
  const online = sensors.value.filter(s => s.status === 1).length
  return {
    total: sensors.value.length || 0,
    online,
    offline: (sensors.value.length || 0) - online
  }
})

/* ---------- 图层 ---------- */
const mapLayers = ref([
  { key: 'sensors',   name: '传感器', visible: true, color: '#10B981', count: 0 },
  { key: 'alerts',    name: '预警点', visible: true, color: '#EF4444', count: 0 },
  { key: 'disasters', name: '灾害事件', visible: true, color: '#F59E0B', count: 0 }
])
function toggleLayer(l) {
  l.visible = !l.visible
  setLayerVisible(l.key, l.visible)
}

/* ---------- 等级统计 ---------- */
function levelCount(lv) {
  return alertStore.latest.filter(a => a.level === lv).length
}
function lvlPercent(lv) {
  const t = alertStore.latest.length
  return t === 0 ? 0 : Math.round((levelCount(lv) / t) * 100)
}

function onAlertClick(a) {
  if (a.longitude != null && a.latitude != null) flyTo(a.longitude, a.latitude, 11)
}

/* ---------- 数据加载 ---------- */
async function loadAll() {
  const [sensorsGeo, alertsGeo, disastersGeo, sensorList] = await Promise.all([
    apiSensorGeoJson(), apiAlertGeoJson(), apiDisasterGeoJson(), apiSensorAll()
  ])
  loadGeoJson('sensors', sensorsGeo)
  loadGeoJson('alerts', alertsGeo)
  loadGeoJson('disasters', disastersGeo)
  sensors.value = sensorList || []

  mapLayers.value[0].count = sensorsGeo.features?.length || 0
  mapLayers.value[1].count = alertsGeo.features?.length || 0
  mapLayers.value[2].count = disastersGeo.features?.length || 0

  await alertStore.fetchLatest(20)
  renderPie()
  await renderLine()
  renderSparks()
}

/* ---------- 浅色图表通用配置 ---------- */
const chartTextColor = '#475569'
const chartAxisColor = '#94A3B8'
const chartGridColor = '#EEF1F6'
const chartTooltip = {
  backgroundColor: '#FFFFFF',
  borderColor: '#E5E9F0',
  borderWidth: 1,
  textStyle: { color: '#0F172A', fontSize: 12 },
  extraCssText: 'box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08); border-radius: 8px;'
}

function renderPie() {
  const data = [1,2,3,4].map(lv => ({
    name: levelMeta(lv).label, value: levelCount(lv),
    itemStyle: { color: levelMeta(lv).color }
  }))
  pie.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', ...chartTooltip },
    legend: {
      textStyle: { color: chartTextColor, fontSize: 12 },
      bottom: 4, itemWidth: 10, itemHeight: 10, icon: 'circle'
    },
    series: [{
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { color: chartTextColor, fontSize: 11 },
      labelLine: { lineStyle: { color: '#CBD5E1' } },
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      data
    }]
  })
}

async function renderLine() {
  const latest = await apiObservationLatest({ limit: 50 })
  const sorted = [...latest].sort((a, b) => new Date(a.observedAt) - new Date(b.observedAt))
  line.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      ...chartTooltip,
      axisPointer: { type: 'line', lineStyle: { color: '#CBD5E1', type: 'dashed' } }
    },
    grid: { top: 16, left: 40, right: 16, bottom: 24 },
    xAxis: {
      type: 'category', boundaryGap: false,
      data: sorted.map(o => dayjs(o.observedAt).format('HH:mm')),
      axisLabel: { color: chartAxisColor, fontSize: 11 },
      axisLine: { lineStyle: { color: chartGridColor } },
      axisTick: { show: false },
      splitLine: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: chartAxisColor, fontSize: 11 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: chartGridColor, type: 'dashed' } }
    },
    series: [{
      data: sorted.map(o => o.value),
      type: 'line', smooth: true, symbol: 'none',
      lineStyle: { color: '#0EA5E9', width: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(14,165,233,0.18)' },
          { offset: 1, color: 'rgba(14,165,233,0)' }
        ])
      }
    }]
  })
}

function renderSparks() {
  Object.entries(sparkRefs).forEach(([k, el]) => {
    if (!el) return
    const inst = echarts.init(el)
    const c = ({ total: '#0EA5E9', red: '#EF4444', sensor: '#10B981', event: '#F59E0B' })[k]
    const data = Array.from({ length: 12 }, () => Math.round(Math.random() * 50 + 20))
    inst.setOption({
      grid: { top: 2, left: 0, right: 0, bottom: 2 },
      xAxis: { type: 'category', show: false, data },
      yAxis: { type: 'value', show: false, min: 'dataMin', max: 'dataMax' },
      series: [{
        type: 'line', data, smooth: true, symbol: 'none',
        lineStyle: { color: c, width: 1.6 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: c + '40' },
            { offset: 1, color: c + '00' }
          ])
        }
      }]
    })
  })
}

onMounted(async () => {
  clockTimer = setInterval(() => {
    time.value = dayjs().format('HH:mm:ss')
    date.value = dayjs().format('YYYY-MM-DD')
    weekday.value = '星期' + ['日','一','二','三','四','五','六'][dayjs().day()]
  }, 1000)
  await loadAll()
  try {
    await ws.connect()
    ws.subscribe('/topic/alerts', async () => {
      const geo = await apiAlertGeoJson()
      loadGeoJson('alerts', geo)
      mapLayers.value[1].count = geo.features?.length || 0
      renderPie()
    })
    ws.subscribe('/topic/observations', async () => {
      await renderLine()
    })
  } catch (_) { /* WS 失败不阻塞 */ }
})
onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped lang="scss">
.dashboard {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  padding: 0;
  gap: 14px;
  overflow: hidden;
  background: var(--bg);
}

/* ============ 顶部标题栏 ============ */
.dash-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px 4px;
  flex-shrink: 0;
}
.dash-header-left {
  display: flex;
  align-items: baseline;
  gap: 14px;
}
.dash-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-1);
  margin: 0;
  letter-spacing: 0.5px;
}
.dash-sub {
  font-size: 13px;
  color: var(--text-3);
}
.dash-header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.weather {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; color: var(--text-2);
  padding: 6px 12px;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 999px;
  .el-icon { color: #F59E0B; font-size: 15px; }
}
.time {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
  letter-spacing: 1px;
}
.env-tag {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 1px;
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: #047857;
}

/* ============ KPI ============ */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  padding: 0 16px;
  flex-shrink: 0;
}
.kpi-card {
  height: 104px;
  padding: 18px 20px;
  display: flex; align-items: center; gap: 16px;
  position: relative;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.2s, transform 0.2s;
  &:hover {
    box-shadow: var(--shadow-lg);
    transform: translateY(-1px);
  }

  .kpi-icon {
    width: 44px; height: 44px; border-radius: 12px;
    display: flex; align-items: center; justify-content: center;
    background: color-mix(in srgb, var(--kpi-color) 12%, transparent);
    color: var(--kpi-color);
    font-size: 22px;
    flex-shrink: 0;
  }
  .kpi-body { flex: 1; min-width: 0; }
  .kpi-label {
    font-size: 13px;
    color: var(--text-3);
    margin-bottom: 6px;
  }
  .kpi-value {
    display: flex; align-items: baseline; gap: 4px;
    .num {
      font-size: 28px;
      color: var(--text-1);
      line-height: 1;
      font-weight: 700;
    }
    .unit { font-size: 13px; color: var(--text-3); margin-left: 4px; }
  }
  .kpi-trend {
    margin-top: 8px;
    font-size: 12px;
    display: flex; align-items: center; gap: 2px;
    .el-icon { font-size: 12px; }
    &.up   { color: #DC2626; }   /* 预警上升用红，符合业务直觉 */
    &.down { color: #059669; }
    .trend-label { color: var(--text-3); margin-left: 6px; }
  }
  .kpi-spark { width: 90px; height: 56px; flex-shrink: 0; }
}

/* ============ 主体三栏 ============ */
.dash-body {
  flex: 1;
  display: grid;
  grid-template-columns: 320px 1fr 320px;
  gap: 14px;
  padding: 0 16px 16px;
  min-height: 0;
}
.col { display: flex; flex-direction: column; gap: 14px; min-height: 0; }

/* 左列 */
.panel-alerts { flex: 1.4; display: flex; flex-direction: column; min-height: 0; }
.alert-list { flex: 1; overflow-y: auto; padding: 8px; }
.alert-card {
  position: relative; display: flex; gap: 10px;
  padding: 10px 12px;
  margin-bottom: 6px;
  background: var(--panel);
  border: 1px solid var(--border-soft);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  overflow: hidden;
  &:hover {
    background: var(--panel-soft);
    border-color: var(--primary-soft);
  }
  .alert-stripe {
    width: 3px; border-radius: 2px;
    background: currentColor;
    flex-shrink: 0;
  }
  &.lvl-1 .alert-stripe { color: var(--alert-blue); }
  &.lvl-2 .alert-stripe { color: var(--alert-yellow); }
  &.lvl-3 .alert-stripe { color: var(--alert-orange); }
  &.lvl-4 .alert-stripe { color: var(--alert-red); }
  .alert-body { flex: 1; min-width: 0; }
  .alert-row1 { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
  .alert-title {
    color: var(--text-1); font-size: 13px; font-weight: 500;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    flex: 1;
  }
  .alert-row2 { display: flex; gap: 8px; font-size: 11px; color: var(--text-3);
    .code { font-family: 'SF Mono', monospace; color: var(--text-2); }
  }
  .alert-status {
    align-self: center;
    font-size: 10px;
    font-weight: 600;
    padding: 2px 8px; border-radius: 999px;
    flex-shrink: 0;
    &.pending { background: #FEF3C7; color: #B45309; }
    &.sent    { background: var(--accent-soft); color: #047857; }
  }
}
.panel-types { flex: 1; min-height: 220px; display: flex; flex-direction: column; }
.panel-types .chart { width: 100%; flex: 1; }

/* 中列 - 地图 */
.panel-map {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.map-el { width: 100%; height: 100%; flex: 1; border-radius: 12px; }
.map-layers {
  position: absolute; top: 12px; right: 12px; z-index: 5;
  display: flex; flex-direction: column; gap: 4px;
  .layer-item {
    display: flex; align-items: center; gap: 8px;
    padding: 6px 12px;
    background: rgba(255, 255, 255, 0.95);
    border: 1px solid var(--border);
    border-radius: 8px;
    cursor: pointer;
    font-size: 12px;
    color: var(--text-2);
    transition: all 0.15s;
    box-shadow: var(--shadow-sm);
    backdrop-filter: blur(8px);
    &:hover { border-color: var(--primary); color: var(--primary-deep); }
    &.active {
      color: var(--text-1);
      border-color: var(--primary);
      background: var(--primary-soft);
    }
    .layer-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
    .layer-count {
      font-family: 'SF Mono', monospace;
      margin-left: auto;
      padding: 1px 8px;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 999px;
      font-size: 11px;
      color: var(--text-2);
      min-width: 28px;
      text-align: center;
    }
  }
}
.map-legend {
  position: absolute; bottom: 16px; right: 12px; z-index: 5;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 10px 14px;
  box-shadow: var(--shadow-sm);
  backdrop-filter: blur(8px);
  .lg-title { font-size: 11px; color: var(--text-3); margin-bottom: 6px; font-weight: 600; }
  .lg-row { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-2); line-height: 1.7;
    .lg-dot { width: 8px; height: 8px; border-radius: 50%; }
  }
}
.map-corner-info {
  position: absolute; top: 12px; left: 12px; z-index: 5;
  font-size: 11px; color: var(--text-3);
  font-family: 'SF Mono', monospace;
  background: rgba(255, 255, 255, 0.92);
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  line-height: 1.6;
  box-shadow: var(--shadow-sm);
}

/* 右列 */
.panel-pie { flex: 0 0 auto; }
.lvl-stats {
  display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px;
  padding: 16px;
}
.lvl-stat { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.lvl-ring {
  --c: #0EA5E9; --p: 0;
  width: 70px; height: 70px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background:
    conic-gradient(var(--c) calc(var(--p) * 1%), #EEF1F6 0);
  position: relative;
  &::before {
    content: '';
    position: absolute; inset: 7px;
    border-radius: 50%;
    background: var(--panel);
  }
  .lvl-num {
    position: relative;
    font-size: 20px;
    color: var(--c);
    font-weight: 700;
  }
}
.lvl-label { font-size: 12px; font-weight: 500; }

.panel-trend { flex: 1.2; display: flex; flex-direction: column; }
.panel-trend .chart { width: 100%; flex: 1; }

.panel-sensors { flex: 0 0 auto; }
.sensor-grid {
  display: grid; grid-template-columns: repeat(8, 1fr);
  gap: 6px;
  padding: 14px;
}
.sensor-cell {
  aspect-ratio: 1;
  border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  position: relative;
  transition: transform 0.15s;
  &:hover { transform: scale(1.1); }
  &.on  { background: var(--accent-soft); }
  &.off { background: #FEE2E2; }
  .cell-dot {
    width: 6px; height: 6px; border-radius: 50%;
  }
  &.on  .cell-dot { background: #10B981; }
  &.off .cell-dot { background: #EF4444; }
}
.sensor-legend {
  display: flex; gap: 16px; padding: 0 14px 14px;
  font-size: 12px; color: var(--text-2);
  span { display: flex; align-items: center; gap: 6px; }
  .dot { width: 6px; height: 6px; border-radius: 50%;
    &.on  { background: #10B981; }
    &.off { background: #EF4444; }
  }
}
</style>
