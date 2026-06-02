<template>
  <!--
    AuroraDashboard — 学术云蓝主题样板
    白底 + 蓝紫青渐变 hero / KPI / 图表 / 自定义 div Select
    与原 MonitorDashboard 完全独立，路由 /dashboard/aurora
  -->
  <div class="aurora-theme aurora-root">

    <!-- ============================================================
         HERO — 渐变 banner + 标题 + 全局筛选
    ============================================================ -->
    <section class="au-hero hero">
      <div class="hero-inner">
        <div class="hero-text">
          <span class="au-pill-grad">气象 · 地质灾害监测</span>
          <h1 class="hero-title">
            <span class="au-grad-text">实时态势</span>
            <span class="hero-title-sub">综合研判平台</span>
          </h1>
          <p class="hero-sub">
            综合气象观测、地质形变监测与空间分析，提供分级预警与决策支持
          </p>
        </div>

        <div class="hero-controls">
          <div class="hero-control">
            <label>区域</label>
            <AuSelect
              v-model="filterRegion"
              :options="regionOptions"
              placeholder="全部区域"
              searchable
            />
          </div>
          <div class="hero-control">
            <label>时段</label>
            <AuSelect
              v-model="filterRange"
              :options="rangeOptions"
              placeholder="近 24 小时"
            />
          </div>
          <div class="hero-control">
            <label>灾种</label>
            <AuSelect
              v-model="filterType"
              :options="typeOptions"
              placeholder="全部灾种"
            />
          </div>
          <AuButton variant="primary" size="md" @click="reloadAll">
            刷新数据
          </AuButton>
        </div>
      </div>
    </section>

    <!-- ============================================================
         KPI ROW
    ============================================================ -->
    <section class="kpi-row">
      <AuKpiCard
        v-for="k in kpis"
        :key="k.key"
        :label="k.label"
        :value="k.value"
        :unit="k.unit"
        :trend="k.trend"
        :trend-invert="k.trendInvert"
        :spark-data="k.sparkData"
        :tone="k.tone"
      />
    </section>

    <!-- ============================================================
         MAP — 空间监测态势
    ============================================================ -->
    <section class="map-section">
      <AuCard
        title="空间监测态势"
        subtitle="EPSG:3857 · OpenLayers"
        gradient-border
        dot
        flat
        class="card-map"
      >
        <template #extra>
          <div class="map-legend">
            <span class="lg-item"><span class="lg-dot" style="background:#DC2626" />红色</span>
            <span class="lg-item"><span class="lg-dot" style="background:#F97316" />橙色</span>
            <span class="lg-item"><span class="lg-dot" style="background:#F59E0B" />黄色</span>
            <span class="lg-item"><span class="lg-dot" style="background:#3B82F6" />蓝色</span>
          </div>
        </template>
        <div ref="mapEl" class="au-map-canvas" />
      </AuCard>
    </section>

    <!-- ============================================================
         BODY — 主图表区
    ============================================================ -->
    <section class="body-grid">

      <AuCard
        title="24 小时预警趋势"
        subtitle="按等级 · 每小时聚合"
        gradient-border
        dot
        class="card-trend"
      >
        <template #extra>
          <AuSelect
            v-model="trendStackMode"
            :options="[
              { value: 'stack', label: '堆叠面积' },
              { value: 'line',  label: '折线对比' },
            ]"
            :width="120"
          />
        </template>
        <div ref="trendEl" class="chart-canvas" />
      </AuCard>

      <AuCard
        title="灾害类型分布"
        subtitle="过去 7 天"
        dot
        class="card-pie"
      >
        <div ref="pieEl" class="chart-canvas" />
      </AuCard>

      <AuCard
        title="预警等级"
        subtitle="当前活跃"
        dot
        class="card-levels"
      >
        <div class="level-grid">
          <div
            v-for="lv in [4,3,2,1]"
            :key="lv"
            class="level-cell"
            :style="{ '--lv-color': levelColor(lv) }"
          >
            <div class="level-ring" :style="ringStyle(lv)">
              <span class="level-num">{{ levelCount(lv) }}</span>
            </div>
            <div class="level-label">{{ levelLabel(lv) }}预警</div>
            <div class="level-pct">{{ lvPercent(lv) }}%</div>
          </div>
        </div>
      </AuCard>

      <AuCard
        title="实时预警列表"
        :subtitle="`共 ${alertStore.latest.length} 条`"
        dot
        class="card-list"
      >
        <template #extra>
          <router-link to="/alerts" class="see-all">查看全部 →</router-link>
        </template>
        <div class="alert-list">
          <div
            v-for="a in alertStore.latest.slice(0, 7)"
            :key="a.id"
            class="alert-item"
          >
            <span
              class="alert-level-bar"
              :style="{ background: levelColor(a.level) }"
            />
            <div class="alert-main">
              <div class="alert-title">{{ a.title || a.disasterType || '预警事件' }}</div>
              <div class="alert-meta">
                <span class="alert-region">{{ a.region || a.location || '—' }}</span>
                <span class="alert-time">{{ formatTime(a.triggeredAt) }}</span>
              </div>
            </div>
            <span class="alert-level-tag" :style="levelTagStyle(a.level)">
              {{ levelLabel(a.level) }}
            </span>
          </div>
          <div v-if="!alertStore.latest.length" class="alert-empty">
            <span>当前无活跃预警</span>
            <span class="muted">系统监测正常运行</span>
          </div>
        </div>
      </AuCard>

      <AuCard
        title="传感数据流"
        subtitle="LIVE · 3s 刷新"
        dot
        class="card-sensors"
      >
        <div class="sensor-grid">
          <div v-for="s in sensorReadings" :key="s.key" class="sensor-cell">
            <span class="sensor-label">{{ s.label }}</span>
            <span class="sensor-val" :style="{ color: s.color }">{{ s.value }}</span>
            <span class="sensor-unit">{{ s.unit }}</span>
          </div>
        </div>
      </AuCard>
    </section>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'

import { useAlertStore } from '@/store/alert'
import { apiDisasterGeoJson } from '@/api/disaster'
import { apiAlertGeoJson } from '@/api/alert'
import { useMap } from '@/hooks/useMap'

import AuCard from '@/components/aurora/AuCard.vue'
import AuSelect from '@/components/aurora/AuSelect.vue'
import AuButton from '@/components/aurora/AuButton.vue'
import AuKpiCard from '@/components/aurora/AuKpiCard.vue'

const alertStore = useAlertStore()

// ---------- Map ----------
const mapEl = ref(null)
const { loadGeoJson } = useMap(mapEl, {
  center: [104, 35],
  zoom: 5,
  // 简易内置注册表：OSM 底图 + 预警 / 灾害事件矢量层
  // 后端 gis_layer 表如有自定义图层，可在 onMounted 之后通过 apiLayerList 扩展
  layerRegistry: [
    {
      code: 'base_osm',
      type: 'xyz',
      sourceUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      visible: true,
      zIndex: 0,
    },
    { code: 'biz_disasters', type: 'vector', visible: true, zIndex: 20 },
    { code: 'biz_alerts',    type: 'vector', visible: true, zIndex: 30 },
  ],
})

// ---------- Filters ----------
const filterRegion = ref(null)
const filterRange = ref('24h')
const filterType = ref(null)
const trendStackMode = ref('stack')

const regionOptions = [
  { value: 'sichuan',   label: '四川' },
  { value: 'yunnan',    label: '云南' },
  { value: 'gansu',     label: '甘肃' },
  { value: 'guizhou',   label: '贵州' },
  { value: 'shaanxi',   label: '陕西' },
]
const rangeOptions = [
  { value: '6h',  label: '近 6 小时' },
  { value: '24h', label: '近 24 小时' },
  { value: '7d',  label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]
const typeOptions = [
  { value: 'landslide', label: '滑坡' },
  { value: 'mudflow',   label: '泥石流' },
  { value: 'collapse',  label: '崩塌' },
  { value: 'subsidence', label: '地面沉降' },
  { value: 'flood',     label: '洪涝' },
]

// ---------- Level meta ----------
const LEVEL_COLORS = { 1: '#2563EB', 2: '#F59E0B', 3: '#F97316', 4: '#DC2626' }
const LEVEL_LABELS = { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
function levelColor(lv) { return LEVEL_COLORS[lv] || '#94A3B8' }
function levelLabel(lv) { return LEVEL_LABELS[lv] || '未知' }
function levelCount(lv) { return alertStore.latest.filter(a => a.level === lv).length }
function lvPercent(lv) {
  const t = alertStore.latest.length
  return t === 0 ? 0 : Math.round((levelCount(lv) / t) * 100)
}
function levelTagStyle(lv) {
  const c = levelColor(lv)
  return { color: c, background: c + '1A', border: `1px solid ${c}33` }
}
function ringStyle(lv) {
  const c = levelColor(lv)
  const pct = lvPercent(lv)
  return {
    background: `conic-gradient(${c} ${pct * 3.6}deg, #E2E8F0 0deg)`,
  }
}

// ---------- KPIs ----------
const eventCount = ref(0)
function mkSpark(base) {
  return Array.from({ length: 14 }, (_, i) =>
    Math.max(0, Math.round(base * (0.55 + Math.sin(i * 0.65) * 0.4 + Math.random() * 0.3)))
  )
}
const kpis = computed(() => {
  const total = alertStore.latest.length
  const red   = alertStore.latest.filter(a => a.level === 4).length
  const conf  = alertStore.latest.filter(a => a.status === 3).length
  return [
    { key: 'total',  label: '今日预警总数', value: total,            unit: '条', trend: 12, trendInvert: true,  sparkData: mkSpark(Math.max(total, 6)), tone: 'info' },
    { key: 'red',    label: '红色级别预警', value: red,              unit: '条', trend: red ? 8 : -10, trendInvert: true, sparkData: mkSpark(Math.max(red, 2)), tone: 'danger' },
    { key: 'conf',   label: '已确认事件',   value: conf,             unit: '条', trend: 6, trendInvert: false, sparkData: mkSpark(Math.max(conf, 3)), tone: 'success' },
    { key: 'event',  label: '活跃灾害事件', value: eventCount.value, unit: '起', trend: 3, trendInvert: true,  sparkData: mkSpark(Math.max(eventCount.value, 2)), tone: 'warning' },
  ]
})

// ---------- Sensor readings ----------
const sensorReadings = ref([
  { key: 'rain', label: '降雨量',  value: '12.4', unit: 'mm',  color: '#2563EB' },
  { key: 'wind', label: '风速',    value: '8.2',  unit: 'm/s', color: '#06B6D4' },
  { key: 'disp', label: '位移',    value: '0.30', unit: 'mm',  color: '#F59E0B' },
  { key: 'seis', label: '地震烈度', value: '0.08', unit: 'gal', color: '#475569' },
  { key: 'soil', label: '土壤湿度', value: '78',   unit: '%',   color: '#10B981' },
  { key: 'temp', label: '温度',    value: '24.5', unit: '°C',  color: '#F97316' },
])
let sensorTimer = null
function refreshSensors() {
  sensorReadings.value[0].value = (8 + Math.random() * 20).toFixed(1)
  sensorReadings.value[1].value = (4 + Math.random() * 12).toFixed(1)
  sensorReadings.value[2].value = (Math.random() * 1.5).toFixed(2)
  sensorReadings.value[3].value = (Math.random() * 0.2).toFixed(3)
  sensorReadings.value[4].value = Math.round(60 + Math.random() * 30).toString()
  sensorReadings.value[5].value = (20 + Math.random() * 10).toFixed(1)
}

// ---------- Charts ----------
const trendEl = ref(null)
const pieEl = ref(null)
let trendChart = null
let pieChart = null
let resizeFn = null

function buildTrendOption() {
  const buckets = Array.from({ length: 24 }, () => ({ 1: 0, 2: 0, 3: 0, 4: 0 }))
  const now = dayjs()
  alertStore.latest.forEach(a => {
    const diff = now.diff(dayjs(a.triggeredAt), 'hour')
    if (diff >= 0 && diff < 24) buckets[23 - diff][a.level] = (buckets[23 - diff][a.level] || 0) + 1
  })
  // 没有真实数据时给点示意数据，避免空白
  if (alertStore.latest.length === 0) {
    for (let i = 0; i < 24; i++) {
      buckets[i] = {
        1: Math.round(Math.random() * 4 + 1),
        2: Math.round(Math.random() * 3),
        3: Math.round(Math.random() * 2),
        4: Math.round(Math.random()),
      }
    }
  }
  const xData = Array.from({ length: 24 }, (_, i) => now.subtract(23 - i, 'hour').format('HH:00'))
  const isStack = trendStackMode.value === 'stack'

  const series = [4, 3, 2, 1].map(lv => ({
    name: levelLabel(lv) + '预警',
    type: 'line',
    stack: isStack ? 'total' : undefined,
    smooth: true,
    symbol: 'none',
    lineStyle: { color: LEVEL_COLORS[lv], width: 2 },
    areaStyle: isStack ? {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: LEVEL_COLORS[lv] + '70' },
        { offset: 1, color: LEVEL_COLORS[lv] + '08' },
      ]),
    } : undefined,
    data: buckets.map(b => b[lv] || 0),
  }))

  return {
    backgroundColor: 'transparent',
    color: [LEVEL_COLORS[4], LEVEL_COLORS[3], LEVEL_COLORS[2], LEVEL_COLORS[1]],
    grid: { top: 36, left: 40, right: 18, bottom: 30, containLabel: false },
    legend: {
      top: 4, right: 4,
      icon: 'roundRect', itemWidth: 10, itemHeight: 6,
      textStyle: { color: '#475569', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#FFFFFF',
      borderColor: '#E2E8F0',
      borderWidth: 1,
      textStyle: { color: '#1E293B', fontSize: 12 },
      extraCssText: 'box-shadow: 0 10px 30px rgba(30,64,175,0.10); border-radius: 10px; padding: 10px 12px;',
      axisPointer: { type: 'line', lineStyle: { color: '#94A3B8', type: 'dashed' } },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      axisLine: { lineStyle: { color: '#CBD5E1' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748B', fontSize: 10, interval: 3 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#64748B', fontSize: 10 },
      splitLine: { lineStyle: { color: '#E2E8F0', type: 'dashed' } },
    },
    series,
  }
}

function buildPieOption() {
  const types = [
    { name: '滑坡',     value: 28, color: '#6366F1' },
    { name: '泥石流',   value: 22, color: '#06B6D4' },
    { name: '崩塌',     value: 16, color: '#3B82F6' },
    { name: '地面沉降', value: 12, color: '#8B5CF6' },
    { name: '洪涝',     value:  9, color: '#0EA5E9' },
  ]
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: '#FFFFFF',
      borderColor: '#E2E8F0',
      textStyle: { color: '#1E293B', fontSize: 12 },
      extraCssText: 'box-shadow: 0 10px 30px rgba(30,64,175,0.10); border-radius: 10px;',
      formatter: (p) => `${p.name}<br/><b>${p.value}</b> 起 (${p.percent}%)`,
    },
    legend: {
      bottom: 0, left: 'center',
      icon: 'circle', itemWidth: 8, itemHeight: 8,
      textStyle: { color: '#475569', fontSize: 11 },
    },
    series: [{
      type: 'pie',
      radius: ['52%', '78%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { show: false },
      labelLine: { show: false },
      itemStyle: {
        borderColor: '#FFFFFF',
        borderWidth: 3,
        borderRadius: 4,
      },
      emphasis: {
        label: {
          show: true,
          formatter: '{b}\n{d}%',
          color: '#0F172A',
          fontSize: 12,
          fontWeight: 600,
        },
        itemStyle: {
          shadowBlur: 16,
          shadowColor: 'rgba(99,102,241,0.30)',
        },
      },
      data: types.map(t => ({
        name: t.name,
        value: t.value,
        itemStyle: { color: t.color },
      })),
    }],
  }
}

function renderCharts() {
  if (trendEl.value) {
    if (!trendChart) trendChart = echarts.init(trendEl.value)
    trendChart.setOption(buildTrendOption(), true)
  }
  if (pieEl.value) {
    if (!pieChart) pieChart = echarts.init(pieEl.value)
    pieChart.setOption(buildPieOption(), true)
  }
}

// ---------- Time formatter ----------
function formatTime(t) {
  if (!t) return '—'
  return dayjs(t).format('MM-DD HH:mm')
}

// ---------- Lifecycle ----------
async function reloadAll() {
  try { await alertStore.fetchLatest(20) } catch (_) {}
  try {
    const [alertsGeo, disastersGeo] = await Promise.all([
      apiAlertGeoJson().catch(() => ({ features: [] })),
      apiDisasterGeoJson().catch(() => ({ features: [] })),
    ])
    loadGeoJson('alerts', alertsGeo)
    loadGeoJson('disasters', disastersGeo)
    eventCount.value = disastersGeo?.features?.length || 0
  } catch (_) {}
  renderCharts()
}

onMounted(async () => {
  await reloadAll()
  await nextTick()
  renderCharts()
  resizeFn = () => { trendChart?.resize(); pieChart?.resize() }
  window.addEventListener('resize', resizeFn)
  sensorTimer = setInterval(refreshSensors, 3000)
})

onBeforeUnmount(() => {
  if (resizeFn) window.removeEventListener('resize', resizeFn)
  if (sensorTimer) clearInterval(sensorTimer)
  trendChart?.dispose(); trendChart = null
  pieChart?.dispose();   pieChart = null
})

watch([() => alertStore.latest.length, trendStackMode], () => renderCharts())
</script>

<style scoped>
/* ============================================================
   ROOT
============================================================ */
.aurora-root {
  min-height: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background: var(--au-bg-page);
}

/* ============================================================
   HERO
============================================================ */
.hero { padding: 26px 28px; }
.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.hero-text { min-width: 260px; flex: 1; }
.hero-title {
  margin: 10px 0 6px;
  font-size: 28px;
  font-weight: 700;
  color: var(--au-text-strong);
  letter-spacing: -0.02em;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: baseline;
}
.hero-title-sub {
  font-size: 18px;
  font-weight: 500;
  color: var(--au-text-secondary);
}
.hero-sub {
  margin: 0;
  font-size: 13px;
  color: var(--au-text-secondary);
  max-width: 540px;
  line-height: 1.6;
}
.hero-controls {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
.hero-control {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
}
.hero-control label {
  font-size: 11px;
  font-weight: 600;
  color: var(--au-text-secondary);
  letter-spacing: 0.04em;
}

/* ============================================================
   KPI ROW
============================================================ */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
@media (max-width: 1100px) {
  .kpi-row { grid-template-columns: repeat(2, 1fr); }
}

/* ============================================================
   MAP SECTION
============================================================ */
.map-section { display: flex; }
.card-map {
  flex: 1;
  height: 380px;
  min-height: 320px;
}
.au-map-canvas {
  width: 100%;
  height: 100%;
  background: var(--au-bg-subtle);
  border-radius: 0 0 var(--au-radius-lg) var(--au-radius-lg);
}
.map-legend {
  display: flex;
  gap: 12px;
  align-items: center;
}
.lg-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--au-text-secondary);
  font-weight: 500;
}
.lg-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

/* ============================================================
   BODY GRID
============================================================ */
.body-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  grid-template-rows: 320px 320px;
  grid-template-areas:
    "trend pie    levels"
    "trend list   sensors";
  gap: 14px;
  flex: 1;
  min-height: 0;
}
.card-trend   { grid-area: trend; }
.card-pie     { grid-area: pie; }
.card-levels  { grid-area: levels; }
.card-list    { grid-area: list; }
.card-sensors { grid-area: sensors; }

@media (max-width: 1280px) {
  .body-grid {
    grid-template-columns: 1fr 1fr;
    grid-template-rows: auto;
    grid-template-areas:
      "trend trend"
      "pie levels"
      "list sensors";
  }
}

.chart-canvas { width: 100%; height: 100%; min-height: 220px; }

/* ============================================================
   LEVEL GRID
============================================================ */
.level-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  padding: 6px 4px;
}
.level-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
.level-ring {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  transition: transform var(--au-dur-base) var(--au-ease);
}
.level-ring::before {
  content: '';
  position: absolute;
  inset: 6px;
  background: var(--au-bg-surface);
  border-radius: 50%;
}
.level-cell:hover .level-ring { transform: scale(1.06); }
.level-num {
  position: relative;
  z-index: 1;
  font-family: var(--au-font-num);
  font-size: 20px;
  font-weight: 700;
  color: var(--lv-color);
  font-feature-settings: var(--au-font-feat);
}
.level-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--lv-color);
}
.level-pct {
  font-family: var(--au-font-num);
  font-size: 11px;
  color: var(--au-text-tertiary);
  font-feature-settings: var(--au-font-feat);
}

/* ============================================================
   ALERT LIST
============================================================ */
.alert-list { display: flex; flex-direction: column; gap: 6px; }
.alert-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  background: var(--au-bg-subtle);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-md);
  transition: background var(--au-dur-fast) var(--au-ease),
              transform var(--au-dur-fast) var(--au-ease);
  cursor: pointer;
}
.alert-item:hover {
  background: var(--au-bg-hover);
  transform: translateX(2px);
}
.alert-level-bar {
  width: 3px;
  height: 30px;
  border-radius: 999px;
  flex-shrink: 0;
}
.alert-main { flex: 1; min-width: 0; }
.alert-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--au-text-strong);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.alert-meta {
  display: flex;
  gap: 12px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--au-text-tertiary);
}
.alert-level-tag {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  flex-shrink: 0;
}

.alert-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 32px 12px;
  color: var(--au-text-secondary);
  font-size: 13px;
}
.alert-empty .muted { color: var(--au-text-tertiary); font-size: 11px; }

.see-all {
  font-size: 12px;
  color: var(--au-info);
  text-decoration: none;
  font-weight: 500;
  transition: opacity var(--au-dur-fast) var(--au-ease);
}
.see-all:hover { opacity: 0.75; }

/* ============================================================
   SENSOR GRID
============================================================ */
.sensor-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.sensor-cell {
  background: var(--au-bg-subtle);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-md);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: border-color var(--au-dur-base) var(--au-ease),
              background var(--au-dur-base) var(--au-ease);
}
.sensor-cell:hover {
  background: var(--au-bg-surface);
  border-color: #C7D2FE;
}
.sensor-label {
  font-size: 11px;
  font-weight: 500;
  color: var(--au-text-secondary);
}
.sensor-val {
  font-family: var(--au-font-num);
  font-size: 18px;
  font-weight: 700;
  font-feature-settings: var(--au-font-feat);
  letter-spacing: -0.01em;
}
.sensor-unit {
  font-size: 10px;
  color: var(--au-text-tertiary);
}
</style>
