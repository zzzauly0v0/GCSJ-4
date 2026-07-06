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
            <span class="au-grad-text">实时分析</span>
          </h1>
          <p class="hero-sub">
            综合气象观测、地质形变监测与空间分析，提供分级预警与决策支持
          </p>
        </div>
      </div>
    </section>
    <section class="timeline-section">
      <AuTimelinePlayer />
    </section>

    <!-- ============================================================
         HISTORY — 2020–2023 历史气象灾害概览 (真实 CSV + 判别结果)
    ============================================================ -->
    <section class="history-section">
      <AuCard
        title="2020–2023 历史气象灾害概览"
        subtitle="逐日气象观测 · 四类灾害判别"
        gradient-border
        dot
        flat
        class="card-history"
      >
        <template #extra>
          <div class="hist-toolbar">
            <AuSelect
              v-model="histYear"
              :options="[
                { value: 0,    label: '全部年份' },
                { value: 2020, label: '2020' },
                { value: 2021, label: '2021' },
                { value: 2022, label: '2022' },
                { value: 2023, label: '2023' },
              ]"
              :width="120"
            />
            <router-link to="/history" class="see-all">进入分析页 →</router-link>
          </div>
        </template>

        <div class="hist-grid">
          <!-- 数据规模 KPI -->
          <div class="hist-stats">
            <div class="hist-stat">
              <span class="hist-stat-val">{{ histSummary.stationCount }}</span>
              <span class="hist-stat-lbl">气象站点</span>
            </div>
            <div class="hist-stat">
              <span class="hist-stat-val">{{ histSummary.weatherDays }}</span>
              <span class="hist-stat-lbl">逐日观测(条)</span>
            </div>
            <div class="hist-stat danger">
              <span class="hist-stat-val">{{ histSummary.riskDays }}</span>
              <span class="hist-stat-lbl">判出风险日</span>
            </div>
          </div>

          <!-- 灾种分布饼 + 逐年趋势 -->
          <div ref="histPieEl" class="hist-chart" />
          <div ref="histYearEl" class="hist-chart" />

          <!-- Top 风险日列表 -->
          <div class="hist-top">
            <div class="hist-top-title">最高风险日 Top 8</div>
            <div v-if="!histSummary.topEvents.length" class="hist-empty">
              暂无判别结果，请先在分析页执行「执行灾害判别」
            </div>
            <div
              v-for="(ev, i) in histSummary.topEvents"
              :key="i"
              class="hist-top-row"
            >
              <span class="ht-bar" :style="{ background: LV_COLOR[ev.comp_level] }" />
              <span class="ht-station">{{ ev.station_name || ev.station_code }}</span>
              <span class="ht-date">{{ String(ev.obs_date).slice(0, 10) }}</span>
              <span class="ht-reff">R_eff {{ Number(ev.r_eff ?? 0).toFixed(0) }}</span>
              <span class="ht-lv" :style="{ color: LV_COLOR[ev.comp_level] }">{{ LV_NAME[ev.comp_level] }}</span>
            </div>
          </div>
        </div>
      </AuCard>
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
         MAP — 空间监测态势 (四川省专题底图)
    ============================================================ -->
    <section class="map-section">
      <AuCard
        title="四川省 · 空间监测态势"
        subtitle="EPSG:3857"
        gradient-border
        dot
        flat
        class="card-map"
      >
        <template #extra>
          <div class="map-toolbar">
            <div class="map-toggle-group">
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.province" @change="syncScLayer('province')" />
                <span>省界</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.city" @change="syncScLayer('city')" />
                <span>市州</span>
              </label>
              <span class="toggle-divider"></span>
              <label class="map-toggle">
                <input type="checkbox" class="toggle-heatmap" v-model="replayToggle.heatmap" @change="syncReplayLayer('heatmap')" />
                <span>风险热力</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="replayToggle.stations" @change="syncReplayLayer('stations')" />
                <span>站点</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="replayToggle.impact" @change="syncReplayLayer('impact')" />
                <span>站点名称</span>
              </label>
            </div>
            <div class="map-legend">
              <span class="lg-item"><span class="lg-dot" style="background:#DC2626" />红色等级</span>
              <span class="lg-item"><span class="lg-dot" style="background:#F97316" />橙色等级</span>
              <span class="lg-item"><span class="lg-dot" style="background:#F59E0B" />黄色等级</span>
              <span class="lg-item"><span class="lg-dot" style="background:#3B82F6" />蓝色等级</span>
            </div>
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
        title="风险站点趋势"
        subtitle="按综合等级 · 逐日累积"
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
        subtitle="当日风险站点"
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
        :subtitle="`${dayEvents.length ? replayStore.virtualDate + ' · ' : ''}共 ${dayEvents.length} 条`"
        dot
        class="card-list"
      >
        <template #extra>
          <router-link to="/alerts" class="see-all">查看全部 →</router-link>
        </template>
        <div class="alert-list">
          <div
            v-for="a in dayEvents.slice(0, 7)"
            :key="a.station_code"
            class="alert-item"
          >
            <span
              class="alert-level-bar"
              :style="{ background: levelColor(a.comp_level) }"
            />
            <div class="alert-main">
              <div class="alert-title">{{ a.station_name || a.station_code }} · {{ topTypeName(a) }}</div>
              <div class="alert-meta">
                <span class="alert-region">R_eff {{ Number(a.r_eff ?? 0).toFixed(0) }}</span>
                <span class="alert-time">{{ String(a.obs_date).slice(0, 10) }}</span>
              </div>
            </div>
            <span class="alert-level-tag" :style="levelTagStyle(a.comp_level)">
              {{ levelLabel(a.comp_level) }}
            </span>
          </div>
          <div v-if="!dayEvents.length" class="alert-empty">
            <span>{{ replayStore.virtualDate || '当前' }} 无风险事件</span>
            <span class="muted">点击 ▶ 播放推进回放</span>
          </div>
        </div>
      </AuCard>

      <AuCard
        title="当日风险指标"
        subtitle="随回放日更新"
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
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'

import { useReplayStore } from '@/store/replay'
import { apiDisasterGeoJson } from '@/api/disaster'
import { apiRegionsGeoJson, apiRiversGeoJson, apiSettlementsGeoJson } from '@/api/gis'
import { apiEvalSummary, apiEvalPush } from '@/api/disasterEval'
import { useMap } from '@/hooks/useMap'
import { useReplayLayers } from '@/hooks/useReplayLayers'
import { useDisasterSocket } from '@/hooks/useDisasterSocket'

import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import GeoJSON from 'ol/format/GeoJSON'
import { Style, Stroke, Fill, Circle as CircleStyle, Text } from 'ol/style'
import { fromLonLat } from 'ol/proj'

import AuCard from '@/components/aurora/AuCard.vue'
import AuSelect from '@/components/aurora/AuSelect.vue'
import AuKpiCard from '@/components/aurora/AuKpiCard.vue'
import AuTimelinePlayer from '@/components/aurora/AuTimelinePlayer.vue'

const router = useRouter()
const replayStore = useReplayStore()

// ---------- Map ----------
// 默认聚焦四川 (102.7°E, 30.65°N), 大屏地图框已放大到主视觉区
const SC_CENTER = [102.7, 30.65]
const SC_ZOOM = 6.6

const mapEl = ref(null)
const { map: olMapRef, loadGeoJson } = useMap(mapEl, {
  center: SC_CENTER,
  zoom: SC_ZOOM,
  // 简易内置注册表：OSM 底图 + 预警 / 灾害事件矢量层
  // 四川行政区/河流/居民点四个矢量图层在 onMounted 后动态挂载
  layerRegistry: [
    {
      code: 'base_osm',
      type: 'xyz',
      sourceUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      visible: true,
      zIndex: 0,
    },
    { code: 'biz_disasters', type: 'vector', visible: true, zIndex: 30 },
    { code: 'biz_alerts',    type: 'vector', visible: true, zIndex: 31 },
  ],
})

// 回放专题图层 (雨量热力 / 气象站 / 事件影响)
const replayLayers = useReplayLayers(olMapRef)
const replayToggle = ref({ heatmap: true, stations: true, impact: true })
function syncReplayLayer(key) {
  replayLayers.toggle(key, replayToggle.value[key])
  if (key === 'impact') replayLayers.toggle('eventDot', replayToggle.value[key])
}

// 四川专题图层 (动态挂到地图实例上, 不走 useMap 注册表)
const scLayerToggle = ref({ province: true, city: false, river: true, settlement: false })
const scOlLayers = {}
const geoFmt = new GeoJSON()

function makeRegionStyle(strokeColor, strokeWidth, withLabel = false) {
  return (feature, resolution) => {
    const styles = [new Style({
      stroke: new Stroke({ color: strokeColor, width: strokeWidth }),
      fill: new Fill({ color: strokeColor + '0F' })
    })]
    if (withLabel && resolution < 4500) {
      const name = feature.get('name')
      if (name) {
        styles.push(new Style({
          text: new Text({
            text: name,
            font: '11px "PingFang SC", sans-serif',
            fill: new Fill({ color: '#1E293B' }),
            stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 3 })
          })
        }))
      }
    }
    return styles
  }
}

function makeRiverStyle() {
  return new Style({ stroke: new Stroke({ color: '#0EA5E9', width: 2 }) })
}

function makeSettlementStyle() {
  return (feature, resolution) => {
    const styles = [new Style({
      image: new CircleStyle({
        radius: 4,
        fill: new Fill({ color: '#F59E0B' }),
        stroke: new Stroke({ color: '#1F2937', width: 1 })
      })
    })]
    if (resolution < 3000) {
      const name = feature.get('name')
      if (name) {
        styles.push(new Style({
          text: new Text({
            text: name,
            font: '10px "PingFang SC", sans-serif',
            fill: new Fill({ color: '#0F172A' }),
            stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 2 }),
            offsetY: -10
          })
        }))
      }
    }
    return styles
  }
}

const scLayerSpecs = [
  { key: 'province',   zIndex: 10, fetcher: () => apiRegionsGeoJson({ level: 1, adcode: '510000' }), styleFn: makeRegionStyle('#0EA5E9', 2.4) },
  { key: 'city',       zIndex: 11, fetcher: () => apiRegionsGeoJson({ level: 2, parent: '510000' }), styleFn: makeRegionStyle('#6366F1', 1.2, true) },
  { key: 'river',      zIndex: 20, fetcher: () => apiRiversGeoJson(), styleFn: makeRiverStyle() },
  { key: 'settlement', zIndex: 25, fetcher: () => apiSettlementsGeoJson(), styleFn: makeSettlementStyle() }
]

async function attachSichuanLayers() {
  const olMap = olMapRef.value
  if (!olMap) return
  for (const spec of scLayerSpecs) {
    const lyr = new VectorLayer({
      source: new VectorSource(),
      style: spec.styleFn,
      visible: !!scLayerToggle.value[spec.key],
      zIndex: spec.zIndex
    })
    scOlLayers[spec.key] = lyr
    olMap.addLayer(lyr)
    if (scLayerToggle.value[spec.key]) await loadSichuanLayer(spec)
  }
}

async function loadSichuanLayer(spec) {
  try {
    const geo = await spec.fetcher()
    const lyr = scOlLayers[spec.key]
    if (!lyr || !geo) return
    const feats = geoFmt.readFeatures(geo, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' })
    lyr.getSource().clear()
    lyr.getSource().addFeatures(feats)
    lyr.set('loaded', true)
  } catch (_) { /* 静默, 已由 request 拦截器提示 */ }
}

async function syncScLayer(key) {
  const lyr = scOlLayers[key]
  const visible = scLayerToggle.value[key]
  if (!lyr) return
  lyr.setVisible(visible)
  if (visible && !lyr.get('loaded')) {
    const spec = scLayerSpecs.find(s => s.key === key)
    if (spec) await loadSichuanLayer(spec)
  }
}

// ---------- Filters ----------
const trendStackMode = ref('stack')

// ---------- History overview (2020–2023 真实判别结果) ----------
const LV_COLOR = ['#94A3B8', '#2563EB', '#F59E0B', '#F97316', '#DC2626']
const LV_NAME = ['无', '蓝色', '黄色', '橙色', '红色']
const TYPE_NAME = { landslide: '滑坡', mudslide: '暴雨', freezethaw: '高温热浪', collapse: '干旱' }
const TYPE_COLOR = { landslide: '#6366F1', mudslide: '#06B6D4', freezethaw: '#3B82F6', collapse: '#8B5CF6' }

// 概览年份默认跟随回放年份 (回放切到哪年, 概览就展示哪年)
const histYear = ref(replayStore.year)
const histSummary = ref({
  stationCount: 0, weatherDays: 0, riskDays: 0,
  byType: {}, byLevel: [], byYear: [], topEvents: [],
})
const histPieEl = ref(null)
const histYearEl = ref(null)
let histPieChart = null
let histYearChart = null

async function loadHistSummary() {
  try {
    const data = await apiEvalSummary(histYear.value || undefined)
    histSummary.value = {
      stationCount: data?.stationCount ?? 0,
      weatherDays: data?.weatherDays ?? 0,
      riskDays: data?.riskDays ?? 0,
      byType: data?.byType ?? {},
      byLevel: data?.byLevel ?? [],
      byYear: data?.byYear ?? [],
      topEvents: data?.topEvents ?? [],
    }
  } catch (_) { /* request 拦截器已提示 */ }
  renderHistCharts()
}

function buildHistPieOption() {
  const bt = histSummary.value.byType || {}
  const data = Object.keys(TYPE_NAME)
    .map(k => ({ name: TYPE_NAME[k], value: bt[k] || 0, itemStyle: { color: TYPE_COLOR[k] } }))
    .filter(d => d.value > 0)
  return {
    backgroundColor: 'transparent',
    title: { text: '灾种风险日分布', left: 'center', top: 4, textStyle: { color: '#475569', fontSize: 12, fontWeight: 600 } },
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}<br/><b>${p.value}</b> 天 (${p.percent}%)` },
    legend: { bottom: 0, left: 'center', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: '#475569', fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['46%', '70%'], center: ['50%', '50%'],
      avoidLabelOverlap: true, label: { show: false }, labelLine: { show: false },
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 2, borderRadius: 4 },
      emphasis: { label: { show: true, formatter: '{b}\n{d}%', color: '#0F172A', fontSize: 12, fontWeight: 600 } },
      data: data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#E2E8F0' } }],
    }],
  }
}

function buildHistYearOption() {
  const rows = histSummary.value.byYear || []
  const years = rows.map(r => String(r.year))
  const counts = rows.map(r => Number(r.cnt) || 0)
  return {
    backgroundColor: 'transparent',
    title: { text: '逐年风险日趋势', left: 'center', top: 4, textStyle: { color: '#475569', fontSize: 12, fontWeight: 600 } },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { top: 40, left: 36, right: 16, bottom: 24, containLabel: false },
    xAxis: { type: 'category', data: years.length ? years : ['2020', '2021', '2022', '2023'],
      axisLine: { lineStyle: { color: '#CBD5E1' } }, axisTick: { show: false }, axisLabel: { color: '#64748B', fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1, axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: '#64748B', fontSize: 10 }, splitLine: { lineStyle: { color: '#E2E8F0', type: 'dashed' } } },
    series: [{
      type: 'bar', data: counts.length ? counts : [0, 0, 0, 0], barWidth: '46%',
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#6366F1' }, { offset: 1, color: '#06B6D4' },
        ]),
      },
    }],
  }
}

function renderHistCharts() {
  if (histPieEl.value) {
    if (!histPieChart) histPieChart = echarts.init(histPieEl.value)
    histPieChart.setOption(buildHistPieOption(), true)
  }
  if (histYearEl.value) {
    if (!histYearChart) histYearChart = echarts.init(histYearEl.value)
    histYearChart.setOption(buildHistYearOption(), true)
  }
}

// ---------- 当日风险事件 (由 /topic/disasters 每日推送驱动全部面板) ----------
// events: [{ station_code, station_name, obs_date, lon, lat, r_eff, dtr,
//            landslide_level, mudslide_level, freezethaw_level, collapse_level,
//            comp_level, comp_index }]
const dayEvents = ref([])
// 逐日累积趋势历史: [{ date, 1, 2, 3, 4 }] — 随回放推进而增长
const dailyHistory = ref([])

/** 事件中占主导的灾种名 (取等级最高的灾种列) */
function topTypeName(e) {
  const cols = [
    ['landslide_level', '滑坡'], ['mudslide_level', '暴雨'],
    ['freezethaw_level', '高温热浪'], ['collapse_level', '干旱'],
  ]
  let best = null, bestLv = 0
  for (const [col, name] of cols) {
    const lv = e[col] || 0
    if (lv > bestLv) { bestLv = lv; best = name }
  }
  return best || '综合风险'
}

// ---------- Level meta ----------
const LEVEL_COLORS = { 1: '#2563EB', 2: '#F59E0B', 3: '#F97316', 4: '#DC2626' }
const LEVEL_LABELS = { 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
function levelColor(lv) { return LEVEL_COLORS[lv] || '#94A3B8' }
function levelLabel(lv) { return LEVEL_LABELS[lv] || '未知' }
function levelCount(lv) { return dayEvents.value.filter(a => a.comp_level === lv).length }
function lvPercent(lv) {
  const t = dayEvents.value.length
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
function mkSpark(base) {
  return Array.from({ length: 14 }, (_, i) =>
    Math.max(0, Math.round(base * (0.55 + Math.sin(i * 0.65) * 0.4 + Math.random() * 0.3)))
  )
}
/** 环比: (今值 - 昨值)/昨值 的百分比整数; 昨值为 0 时今值>0 记 100, 同为 0 记 0 */
function pctChange(today, prev) {
  if (prev == null) return null            // 无前一日数据 -> 不显示趋势
  if (prev === 0) return today > 0 ? 100 : 0
  return Math.round(((today - prev) / prev) * 100)
}
const kpis = computed(() => {
  const evs = dayEvents.value
  const total = evs.length
  const red   = evs.filter(a => a.comp_level === 4).length
  const high  = evs.filter(a => a.comp_level >= 3).length
  const maxReff = evs.reduce((m, a) => Math.max(m, Number(a.r_eff ?? 0)), 0)

  // 环比昨日: 取累积历史的倒数第二天 (当前日为最后一天)
  const hist = dailyHistory.value
  const prev = hist.length >= 2 ? hist[hist.length - 2] : null
  const prevTotal = prev ? (prev[1] + prev[2] + prev[3] + prev[4]) : null
  const prevRed   = prev ? prev[4] : null
  const prevHigh  = prev ? (prev[3] + prev[4]) : null
  const prevReff  = prev ? (prev.maxReff ?? null) : null

  // spark 用累积历史的当日总数序列, 反映回放推进
  const spark = hist.slice(-14).map(d => (d[1] + d[2] + d[3] + d[4]))
  return [
    { key: 'total',  label: '当日风险站点', value: total,             unit: '站', trend: pctChange(total, prevTotal), trendInvert: true,  sparkData: spark.length ? spark : mkSpark(Math.max(total, 6)), tone: 'info' },
    { key: 'red',    label: '红色级别',     value: red,               unit: '站', trend: pctChange(red, prevRed),     trendInvert: true,  sparkData: mkSpark(Math.max(red, 2)),  tone: 'danger' },
    { key: 'high',   label: '橙红高风险',   value: high,              unit: '站', trend: pctChange(high, prevHigh),   trendInvert: true,  sparkData: mkSpark(Math.max(high, 3)), tone: 'warning' },
    { key: 'reff',   label: '最大有效雨量', value: maxReff.toFixed(0), unit: 'mm', trend: pctChange(Math.round(maxReff), prevReff != null ? Math.round(prevReff) : null), trendInvert: true, sparkData: mkSpark(Math.max(maxReff / 10, 2)), tone: 'success' },
  ]
})

// ---------- 当日风险指标 (由 dayEvents 聚合, 随回放日更新) ----------
const sensorReadings = computed(() => {
  const evs = dayEvents.value
  const n = evs.length
  const avg = (fn) => n ? (evs.reduce((s, e) => s + (Number(fn(e)) || 0), 0) / n) : 0
  const max = (fn) => evs.reduce((m, e) => Math.max(m, Number(fn(e)) || 0), 0)
  const cnt = (col) => evs.filter(e => (e[col] || 0) >= 1).length
  return [
    { key: 'reff', label: '平均有效雨量', value: avg(e => e.r_eff).toFixed(1),   unit: 'mm', color: '#2563EB' },
    { key: 'maxr', label: '最大有效雨量', value: max(e => e.r_eff).toFixed(1),   unit: 'mm', color: '#06B6D4' },
    { key: 'dtr',  label: '平均日较差',   value: avg(e => e.dtr).toFixed(1),     unit: '°C', color: '#F59E0B' },
    { key: 'ls',   label: '滑坡风险站',   value: String(cnt('landslide_level')), unit: '站', color: '#8B5CF6' },
    { key: 'ms',   label: '暴雨风险站',   value: String(cnt('mudslide_level')),  unit: '站', color: '#F97316' },
  ]
})

// ---------- Charts ----------
const trendEl = ref(null)
const pieEl = ref(null)
let trendChart = null
let pieChart = null
let resizeFn = null

function buildTrendOption() {
  // 逐日回放累积: 每个回放日一根 x 轴刻度, 4 个等级站点数
  const hist = dailyHistory.value.slice(-30)
  const xData = hist.map(d => String(d.date).slice(5))   // MM-DD
  const isStack = trendStackMode.value === 'stack'

  const series = [4, 3, 2, 1].map(lv => ({
    name: levelLabel(lv) + '级',
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
    data: hist.map(d => d[lv] || 0),
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
      axisLabel: { color: '#64748B', fontSize: 10, interval: 'auto' },
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
  // 当日风险站点按灾种统计 (每站可能命中多灾种, 各计一次)
  const evs = dayEvents.value
  const defs = [
    { col: 'landslide_level',  name: '滑坡',   color: '#6366F1' },
    { col: 'mudslide_level',   name: '暴雨',   color: '#06B6D4' },
    { col: 'freezethaw_level', name: '高温',   color: '#3B82F6' },
    { col: 'collapse_level',   name: '干旱',   color: '#8B5CF6' },
  ]
  const types = defs
    .map(d => ({ name: d.name, value: evs.filter(e => (e[d.col] || 0) >= 1).length, color: d.color }))
    .filter(t => t.value > 0)
  if (!types.length) types.push({ name: '当日无风险', value: 1, color: '#E2E8F0' })
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

// ---------- 回放推送 ----------
// 收到 /topic/disasters 按日推送: 地图专题图层 + 全部面板 (KPI/等级/列表/图表/指标) 一起刷新
useDisasterSocket((payload) => {
  const events = payload?.events || []
  replayLayers.renderDisasters(events)

  // 驱动全部面板的当日数据
  dayEvents.value = events

  // 累积逐日趋势 (同一天重复推送则覆盖, 保留最近 60 天)
  if (payload?.date) {
    const bucket = { date: payload.date, 1: 0, 2: 0, 3: 0, 4: 0, maxReff: 0 }
    events.forEach(e => {
      const lv = e.comp_level || 0
      if (bucket[lv] != null) bucket[lv]++
      bucket.maxReff = Math.max(bucket.maxReff, Number(e.r_eff ?? 0))
    })
    const hist = dailyHistory.value
    const last = hist[hist.length - 1]
    if (last && last.date === payload.date) hist[hist.length - 1] = bucket
    else hist.push(bucket)
    if (hist.length > 60) hist.shift()
    dailyHistory.value = [...hist]
  }

  renderCharts()
})

// ---------- Lifecycle ----------
/**
 * reloadAll — 底图静态灾害点 GeoJSON (仅初始化一次即可) + 触发当前虚拟日推送。
 * 所有面板/专题图层的实时数据均来自 pushDay() -> /topic/disasters 推送。
 */
async function reloadAll() {
  await replayStore.init()
  try {
    const eventsGeo = await apiDisasterGeoJson().catch(() => ({ type: 'FeatureCollection', features: [] }))
    loadGeoJson('disasters', eventsGeo)
  } catch (_) {}
  // 首屏 / 时间推进后推送当前虚拟日的风险事件 (回推后驱动全部面板)
  pushDay()
}

/** 按当前虚拟日期触发后端推送 (WebSocket 回渲染地图) */
let _lastPushedDate = null
async function pushDay() {
  const date = replayStore.virtualDate
  if (!date || date === _lastPushedDate) return
  _lastPushedDate = date
  try {
    // minLevel=1: 蓝/黄/橙/红全部风险日都上图, 地图数据更丰富、贴合时间轴推进
    await apiEvalPush(date, 1)   // 结果经 /topic/disasters 回推, 由 useDisasterSocket 渲染
  } catch (_) {
    replayLayers.clear()
  }
}

onMounted(async () => {
  await reloadAll()
  await nextTick()
  // 等 useMap 内部 onMounted 把 olMapRef 拼出来再挂图层 (微任务跳一拍)
  setTimeout(async () => {
    await attachSichuanLayers()
    replayLayers.attach()
    // 图层挂好后, 重新推送当前虚拟日 (reloadAll 时可能早于图层挂载)
    _lastPushedDate = null
    pushDay()
  }, 0)
  renderCharts()
  loadHistSummary()
  resizeFn = () => { trendChart?.resize(); pieChart?.resize(); histPieChart?.resize(); histYearChart?.resize() }
  window.addEventListener('resize', resizeFn)
  // 回放模式下不再用 mock sensor 抖动, 由 reloadAll 在 virtualNow 变化时刷新
})

onBeforeUnmount(() => {
  if (resizeFn) window.removeEventListener('resize', resizeFn)
  trendChart?.dispose(); trendChart = null
  pieChart?.dispose();   pieChart = null
  histPieChart?.dispose();  histPieChart = null
  histYearChart?.dispose(); histYearChart = null
  replayLayers.detach()
})

// 年份切换 -> 重新拉历史概览; 选中具体年份时同步驱动回放年份 (双向锁定)
watch(histYear, (y) => {
  loadHistSummary()
  if (y && y !== replayStore.year) replayStore.setYear(y)
})

// 趋势图堆叠/折线切换 -> 重绘
watch(trendStackMode, () => renderCharts())

// 回放年份切换 -> 清空逐日累积历史 (新的一年重新累积) + 同步概览年份
watch(() => replayStore.year, (y) => {
  dailyHistory.value = []
  dayEvents.value = []
  // 概览面板始终与回放年份保持一致 (上面选 2020, 下面概览也切到 2020)
  if (histYear.value !== y) histYear.value = y
})

// 虚拟"日期"推进 -> 重新拉数据 (数据按天, 只在跨天时触发, 天然与地图推送同步)
watch(() => replayStore.virtualDate, (d) => {
  if (d) reloadAll()
})
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

.timeline-section {
  width: 100%;
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
.entry-card {
  margin-top: 16px;
  display: inline-block;
  cursor: pointer;
  padding: 10px 16px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.12);
  border: 1px solid rgba(59, 130, 246, 0.4);
  transition: background 0.2s;
}
.entry-card:hover { background: rgba(59, 130, 246, 0.24); }
.entry-title { font-size: 14px; font-weight: 600; color: var(--au-text-primary); }
.entry-sub { font-size: 12px; color: var(--au-text-secondary); margin-top: 2px; }
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
   HISTORY SECTION (2020–2023 概览)
============================================================ */
.history-section { display: flex; }
.card-history { flex: 1; }
.hist-toolbar { display: flex; align-items: center; gap: 14px; }
.hist-grid {
  display: grid;
  grid-template-columns: 200px 1fr 1fr 1.2fr;
  gap: 16px;
  align-items: stretch;
  min-height: 240px;
}
.hist-stats {
  display: flex;
  flex-direction: column;
  gap: 12px;
  justify-content: center;
}
.hist-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px 14px;
  background: var(--au-bg-subtle);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-md);
}
.hist-stat.danger { border-color: #FCA5A5; background: rgba(220, 38, 38, 0.06); }
.hist-stat-val {
  font-family: var(--au-font-num);
  font-size: 24px;
  font-weight: 700;
  color: var(--au-text-strong);
  font-feature-settings: var(--au-font-feat);
}
.hist-stat.danger .hist-stat-val { color: #DC2626; }
.hist-stat-lbl { font-size: 12px; color: var(--au-text-secondary); }
.hist-chart { min-height: 240px; height: 100%; }
.hist-top {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.hist-top-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--au-text-secondary);
  margin-bottom: 2px;
}
.hist-empty {
  font-size: 12px;
  color: var(--au-text-tertiary);
  padding: 20px 8px;
  text-align: center;
}
.hist-top-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: var(--au-bg-subtle);
  border-radius: var(--au-radius-sm, 6px);
  font-size: 12px;
}
.ht-bar { width: 3px; height: 18px; border-radius: 999px; flex-shrink: 0; }
.ht-station { flex: 1; min-width: 0; color: var(--au-text-strong); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ht-date { color: var(--au-text-tertiary); font-family: var(--au-font-num); }
.ht-reff { color: var(--au-text-secondary); font-family: var(--au-font-num); }
.ht-lv { font-weight: 600; flex-shrink: 0; }
@media (max-width: 1280px) {
  .hist-grid { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 768px) {
  .hist-grid { grid-template-columns: 1fr; }
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
  height: 600px;
  min-height: 520px;
}
.map-toolbar {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-end;
}
.map-toggle-group {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--au-text-secondary);
}
.map-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}
.map-toggle input { accent-color: #6366F1; }
.map-toggle input.toggle-heatmap { accent-color: #F97316; }
.toggle-divider {
  width: 1px;
  height: 14px;
  background: var(--au-border, #E5E7EB);
  margin: 0 4px;
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

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .hero-title { font-size: 20px; }
  .hero-title-sub { font-size: 14px; }
  .hero-sub { font-size: 12px; }
  .hero-controls { width: 100%; }
  .hero-control { min-width: 100px; flex: 1; }

  .kpi-row { grid-template-columns: 1fr 1fr !important; gap: 10px; }

  .map-section { display: block; }
  .card-map { height: 360px !important; min-height: 320px !important; }

  .body-grid {
    display: flex !important;
    flex-direction: column !important;
    grid-template-columns: none !important;
    grid-template-rows: none !important;
    grid-template-areas: none !important;
    gap: 10px !important;
  }
  .card-trend, .card-pie, .card-levels,
  .card-list, .card-sensors {
    min-height: 280px;
  }

  .level-grid { gap: 12px; }
  .level-ring { width: 52px; height: 52px; }
  .level-num { font-size: 16px; }

  .alert-title { font-size: 12px; }
  .alert-meta { gap: 8px; font-size: 10px; }
}

@media (max-width: 480px) {
  .kpi-row { grid-template-columns: 1fr !important; }
  .hero-title { font-size: 18px; }
}
</style>
