<template>
  <!--
    MonitorDashboard — GCSJ Command Console Main Screen
    Full staggered boot reveal sequence:
      t=700ms:  KPI row cascade (delay-1 → delay-4)
      t=860ms:  Left alert panel (delay-3)
      t=940ms:  Map panel (delay-4)
      t=1020ms: Disaster type pie (delay-5)
      t=1100ms: Level stats (delay-6)
      t=1180ms: 24h trend chart (delay-7)
      t=1260ms: Sensor readouts (delay-8)

    Data-ink principle: charts show conclusions, not decoration.
    All numeric values rendered with JetBrains Mono via CSS classes.
  -->
  <div class="monitor-root console-bg">

    <!-- ============================================================
         KPI ROW — 4 metric cards, staggered delay-1 to delay-4
    ============================================================ -->
    <div class="kpi-row">
      <MetricReadout
        v-for="(kpi, i) in kpis"
        :key="kpi.key"
        :label="kpi.label"
        :value="kpi.value"
        :unit="kpi.unit"
        :status="kpi.status"
        :trend="kpi.trend"
        :trend-invert-semantics="kpi.trendInvert"
        :spark-data="kpi.sparkData"
        :show-sparkline="true"
        class="reveal"
        :class="`reveal-delay-${i + 1}`"
      />
    </div>

    <!-- ============================================================
         MAIN BODY — 3-column grid
    ============================================================ -->
    <div class="dash-grid">

      <!-- ========== LEFT COLUMN ========== -->
      <div class="col col-left">

        <!-- Active Alerts Panel (delay-3) -->
        <DataPanel
          title="实时预警"
          :subtitle="`共 ${alertStore.latest.length} 条`"
          :status="maxAlertStatus"
          :live="true"
          :show-footer="true"
          :footer-text="`最后更新 ${lastRefresh}`"
          class="panel-alerts reveal reveal-delay-3"
          :style="{ flex: '1.4' }"
        >
          <template #header-extra>
            <router-link to="/alerts" class="see-all-link">查看全部</router-link>
          </template>

          <div class="alert-list">
            <AlertCard
              v-for="(a, idx) in alertStore.latest.slice(0, 8)"
              :key="a.id"
              :alert="a"
              class="reveal-fast"
              :class="`reveal-delay-${Math.min(idx + 4, 12)}`"
              style="margin-bottom: 4px;"
              @click="onAlertClick"
            />
            <div v-if="!alertStore.latest.length" class="empty-state">
              <span class="empty-icon data-value">[ NO ACTIVE ALERTS ]</span>
              <span class="empty-sub">系统运行正常</span>
            </div>
          </div>
        </DataPanel>

        <!-- Disaster Type Distribution pie (delay-5) -->
        <DataPanel
          title="灾种分布"
          subtitle="按灾害类型"
          status="normal"
          :live="true"
          class="panel-pie reveal reveal-delay-5"
          :style="{ flex: '1', minHeight: '200px' }"
        >
          <StormChart
            :option="pieOption"
            height="100%"
            title=""
            subtitle=""
            style="padding: 4px;"
          />
        </DataPanel>
      </div>

      <!-- ========== CENTER COLUMN — MAP ========== -->
      <div class="col col-center">
        <DataPanel
          title="空间监测态势"
          subtitle="EPSG:3857 实时"
          status="normal"
          :live="true"
          class="panel-map reveal reveal-delay-4"
          :style="{ flex: 1 }"
        >
          <div class="map-inner" ref="mapEl">
            <!-- StormMap is rendered inside the useMap-mounted mapEl div -->
            <StormMap
              :layers="mapLayers"
              :on-toggle-layer="toggleLayer"
              @ready="onMapReady"
            />
          </div>
        </DataPanel>
      </div>

      <!-- ========== RIGHT COLUMN ========== -->
      <div class="col col-right">

        <!-- Level distribution (delay-6) -->
        <DataPanel
          title="等级分布"
          subtitle="当前活跃"
          status="normal"
          :live="false"
          class="panel-levels reveal reveal-delay-6"
        >
          <div class="level-grid">
            <div v-for="lv in [4,3,2,1]" :key="lv" class="level-cell" :class="`lv-${lv}`">
              <div class="level-ring" :style="{ '--lc': levelMeta(lv).stormColor, '--lp': lvlPercent(lv) }">
                <span class="level-num data-value" :style="{ color: levelMeta(lv).stormColor }">
                  {{ levelCount(lv) }}
                </span>
              </div>
              <div class="level-label" :style="{ color: levelMeta(lv).stormColor }">
                {{ levelMeta(lv).label }}预警
              </div>
              <div class="level-pct data-value">{{ lvlPercent(lv) }}%</div>
            </div>
          </div>
        </DataPanel>

        <!-- 24h Trend stacked area (delay-7) -->
        <DataPanel
          title="24小时预警趋势"
          subtitle="按等级 · 每小时"
          status="normal"
          :live="true"
          class="panel-trend reveal reveal-delay-7"
          :style="{ flex: 1, minHeight: '200px' }"
        >
          <StormChart
            :option="trendOption"
            height="100%"
            title=""
            subtitle=""
            style="padding: 6px 4px;"
          />
        </DataPanel>

        <!-- Sensor readout row (delay-8) -->
        <DataPanel
          title="传感数据流"
          subtitle="LIVE FEED"
          status="normal"
          :live="true"
          class="panel-sensors reveal reveal-delay-8"
        >
          <div class="sensor-grid">
            <div v-for="s in sensorReadings" :key="s.key" class="sensor-cell">
              <span class="sensor-label">{{ s.label }}</span>
              <span class="sensor-val data-value" :style="{ color: s.color }">{{ s.value }}</span>
              <span class="sensor-unit">{{ s.unit }}</span>
            </div>
          </div>
        </DataPanel>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'

import { useMap } from '@/hooks/useMap'
import { useAlertStore } from '@/store/alert'
import { useMapStore } from '@/store/map'
import { useWS } from '@/utils/ws'
import { apiAlertGeoJson } from '@/api/alert'
import { apiDisasterGeoJson } from '@/api/disaster'
import { apiLayerList } from '@/api/layer'
import { levelMeta } from '@/utils/format'
import { buildStormGrid, STORM_COLORS } from '@/utils/echartsTheme'

import DataPanel from '@/components/console/DataPanel.vue'
import AlertCard from '@/components/console/AlertCard.vue'
import MetricReadout from '@/components/console/MetricReadout.vue'
import StormChart from '@/components/charts/StormChart.vue'
import StormMap from '@/components/map/StormMap.vue'

// ---- Stores & refs ----
const alertStore = useAlertStore()
const mapStore = useMapStore()
const ws = useWS()
const mapEl = ref(null)
const lastRefresh = ref(dayjs().format('HH:mm:ss'))

// ---- Layer registry ----
let layerRegistry = []
try { layerRegistry = await apiLayerList() || [] } catch (_) { /* fallback OSM */ }

const { loadGeoJson, setLayerVisible, flyTo } = useMap(mapEl, {
  center: [104, 35],
  zoom: 5,
  layerRegistry,
})

// ---- Map layer state ----
const mapLayers = ref([
  { key: 'alerts',    name: '预警点',   visible: true, color: '#FF2D2D', count: 0 },
  { key: 'disasters', name: '灾害事件', visible: true, color: '#FF6A1A', count: 0 },
])
function toggleLayer(l) {
  setLayerVisible(l.key, l.visible)
}
function onMapReady(_el) {
  // Map is mounted by useMap into mapEl ref above; no action needed.
}

// ---- Max alert status for panel status prop ----
const maxAlertStatus = computed(() => {
  const maxLv = Math.max(0, ...alertStore.latest.map(a => a.level || 0))
  if (maxLv >= 4) return 'alert'
  if (maxLv >= 3) return 'warning'
  return 'normal'
})

// ---- KPI data ----
const eventCount = ref(0)
const kpis = computed(() => {
  const total = alertStore.latest.length
  const red   = alertStore.latest.filter(a => a.level === 4).length
  const conf  = alertStore.latest.filter(a => a.status === 3).length
  const mkSpark = (base) => Array.from({ length: 12 }, (_, i) =>
    Math.max(0, Math.round(base * (0.6 + Math.sin(i * 0.7) * 0.4 + Math.random() * 0.3)))
  )
  return [
    {
      key: 'total', label: '今日预警', value: total, unit: '条',
      status: total > 10 ? 'warning' : 'normal',
      trend: 12, trendInvert: true,
      sparkData: mkSpark(Math.max(total, 5)),
    },
    {
      key: 'red', label: '红色预警', value: red, unit: '条',
      status: red > 0 ? 'critical' : 'normal',
      trend: red > 0 ? 8 : -5, trendInvert: true,
      sparkData: mkSpark(Math.max(red, 2)),
    },
    {
      key: 'confirmed', label: '已确认', value: conf, unit: '条',
      status: 'success',
      trend: 6, trendInvert: false,
      sparkData: mkSpark(Math.max(conf, 3)),
    },
    {
      key: 'events', label: '灾害事件', value: eventCount.value, unit: '起',
      status: eventCount.value > 5 ? 'warning' : 'normal',
      trend: 3, trendInvert: true,
      sparkData: mkSpark(Math.max(eventCount.value, 2)),
    },
  ]
})

// ---- Level stats ----
function levelCount(lv) {
  return alertStore.latest.filter(a => a.level === lv).length
}
function lvlPercent(lv) {
  const t = alertStore.latest.length
  return t === 0 ? 0 : Math.round((levelCount(lv) / t) * 100)
}

// ---- Alert click → fly map ----
function onAlertClick(a) {
  if (a.longitude != null && a.latitude != null) flyTo(a.longitude, a.latitude, 11)
}

// ---- Sensor readings (simulated) ----
const sensorReadings = ref([
  { key: 'rain',  label: '降雨量',  value: '12.4', unit: 'mm',   color: 'var(--signal-crystal)' },
  { key: 'wind',  label: '风速',    value: '8.2',  unit: 'm/s',  color: 'var(--signal-crystal)' },
  { key: 'disp',  label: '位移',    value: '0.3',  unit: 'mm',   color: 'var(--signal-hazard)' },
  { key: 'soil',  label: '土壤湿度', value: '78',   unit: '%',   color: 'var(--signal-lava)' },
  { key: 'temp',  label: '温度',    value: '24.5', unit: '°C',  color: 'var(--text-secondary)' },
])

// Update sensors on a live interval
let sensorTimer = null
function refreshSensors() {
  sensorReadings.value[0].value = (8 + Math.random() * 20).toFixed(1)
  sensorReadings.value[1].value = (4 + Math.random() * 12).toFixed(1)
  sensorReadings.value[2].value = (Math.random() * 1.5).toFixed(2)
  sensorReadings.value[3].value = Math.round(60 + Math.random() * 30).toString()
  sensorReadings.value[4].value = (20 + Math.random() * 10).toFixed(1)
}

// ---- Pie chart option — Disaster type distribution ----
const pieOption = computed(() => {
  const disasterTypes = ['滑坡', '暴雨', '高温热浪', '干旱']
  const pieData = disasterTypes.map((name, i) => ({
    name,
    value: Math.round(10 + Math.random() * 25),
    itemStyle: {
      color: [
        STORM_COLORS.crystal,
        STORM_COLORS.lava,
        STORM_COLORS.hazard,
        STORM_COLORS.blue,
        STORM_COLORS.plasma,
      ][i],
    },
  }))
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (p) => `<span style="font-family:var(--font-mono);font-size:11px;">${p.name}<br/><b>${p.value}</b> 起 (${p.percent}%)</span>`,
    },
    legend: {
      orient: 'horizontal',
      bottom: 4,
      textStyle: {
        fontFamily: "'JetBrains Mono', monospace",
        color: '#7D9BB8',
        fontSize: 10,
      },
      icon: 'rect',
      itemWidth: 8,
      itemHeight: 6,
    },
    series: [{
      name: '灾害类型',
      type: 'pie',
      radius: ['42%', '66%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      label: {
        show: false,
      },
      emphasis: {
        label: {
          show: true,
          fontFamily: "'JetBrains Mono', monospace",
          fontSize: 11,
          fontWeight: 'bold',
          formatter: '{b}\n{c}起',
        },
        itemStyle: {
          shadowBlur: 12,
          shadowColor: 'rgba(0,229,255,0.30)',
        },
      },
      itemStyle: {
        borderColor: 'rgba(10,14,26,0.9)',
        borderWidth: 2,
      },
      data: pieData,
    }],
  }
})

// ---- 24h trend chart option (stacked area by level) ----
const trendOption = computed(() => {
  const buckets = Array.from({ length: 24 }, () => ({ 1: 0, 2: 0, 3: 0, 4: 0 }))
  const now = dayjs()
  alertStore.latest.forEach(a => {
    const diff = now.diff(dayjs(a.triggeredAt), 'hour')
    if (diff >= 0 && diff < 24) {
      const idx = 23 - diff
      buckets[idx][a.level] = (buckets[idx][a.level] || 0) + 1
    }
  })
  const xData = Array.from({ length: 24 }, (_, i) =>
    now.subtract(23 - i, 'hour').format('HH:00')
  )

  const levelColors = {
    4: STORM_COLORS.alert,
    3: STORM_COLORS.lava,
    2: STORM_COLORS.hazard,
    1: STORM_COLORS.blue,
  }

  const series = [4, 3, 2, 1].map(lv => ({
    name: levelMeta(lv).label,
    type: 'line',
    stack: 'total',
    smooth: true,
    symbol: 'none',
    lineStyle: { color: levelColors[lv], width: 1.5 },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: levelColors[lv] + '50' },
        { offset: 1, color: levelColors[lv] + '08' },
      ]),
    },
    data: buckets.map(b => b[lv] || 0),
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: 'rgba(0,229,255,0.25)', type: 'dashed', width: 1 } },
    },
    legend: {
      top: 0,
      right: 0,
      textStyle: { fontFamily: "'JetBrains Mono', monospace", color: '#3D5A73', fontSize: 9 },
      icon: 'rect',
      itemWidth: 8,
      itemHeight: 5,
    },
    grid: buildStormGrid({ top: 28, left: 36, right: 60, bottom: 22 }),
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: xData,
      axisLabel: {
        color: '#3D5A73',
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 9,
        interval: 5,
        formatter: (v) => v,
      },
      axisLine: { lineStyle: { color: 'rgba(0,229,255,0.12)' } },
      axisTick: { show: false },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        color: '#3D5A73',
        fontFamily: "'JetBrains Mono', monospace",
        fontSize: 9,
        formatter: (v) => String(v).padStart(2, ' '),
      },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        lineStyle: { color: 'rgba(0,229,255,0.06)', type: 'dashed', width: 1 },
      },
    },
    series,
  }
})

// ---- Data load ----
async function loadAll() {
  const [alertsGeo, disastersGeo] = await Promise.all([
    apiAlertGeoJson().catch(() => ({ features: [] })),
    apiDisasterGeoJson().catch(() => ({ features: [] })),
  ])
  loadGeoJson('alerts', alertsGeo)
  loadGeoJson('disasters', disastersGeo)
  mapLayers.value[0].count = alertsGeo.features?.length || 0
  mapLayers.value[1].count = disastersGeo.features?.length || 0
  eventCount.value = disastersGeo.features?.length || 0
  await alertStore.fetchLatest(20).catch(() => {})
  lastRefresh.value = dayjs().format('HH:mm:ss')
}

onMounted(async () => {
  await loadAll()

  sensorTimer = setInterval(() => {
    refreshSensors()
  }, 3000)

  try {
    await ws.connect()
    ws.subscribe('/topic/alerts', async () => {
      const geo = await apiAlertGeoJson()
      loadGeoJson('alerts', geo)
      mapLayers.value[0].count = geo.features?.length || 0
      await alertStore.fetchLatest(20)
      lastRefresh.value = dayjs().format('HH:mm:ss')
    })
  } catch (_) { /* WS optional */ }
})

onBeforeUnmount(() => {
  if (sensorTimer) clearInterval(sensorTimer)
})
</script>

<style scoped>
/* ================================================================
   ROOT
================================================================ */
.monitor-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px;
  gap: 12px;
  overflow: hidden;
}

/* ================================================================
   KPI ROW
================================================================ */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  flex-shrink: 0;
}

/* ================================================================
   MAIN 3-COLUMN GRID
================================================================ */
.dash-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 280px 1fr 280px;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

.col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

/* ================================================================
   ALERT LIST
================================================================ */
.panel-alerts {
  display: flex;
  flex-direction: column;
}

.alert-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 24px;
  flex: 1;
}
.empty-icon {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}
.empty-sub {
  font-family: var(--font-ui);
  font-size: 11px;
  color: var(--text-tertiary);
}

.see-all-link {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--signal-crystal);
  letter-spacing: 0.10em;
  text-transform: uppercase;
  text-decoration: none;
  opacity: 0.70;
  transition: opacity 120ms ease;
}
.see-all-link:hover { opacity: 1; }

/* ================================================================
   MAP PANEL
================================================================ */
.panel-map {
  flex: 1;
  min-height: 0;
}
.map-inner {
  width: 100%;
  height: 100%;
  position: relative;
}

/* ================================================================
   LEVEL GRID (2×2)
================================================================ */
.level-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 14px;
}
.level-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.level-ring {
  --lc: var(--signal-crystal);
  --lp: 0;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: conic-gradient(
    var(--lc) calc(var(--lp) * 1%),
    rgba(22, 32, 50, 0.80) 0%
  );
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-shadow: 0 0 0 1px rgba(255,255,255,0.04);
}
.level-ring::before {
  content: '';
  position: absolute;
  inset: 7px;
  border-radius: 50%;
  background: var(--bg-panel);
}

.level-num {
  position: relative;
  z-index: 1;
  font-family: var(--font-mono);
  font-size: 20px;
  font-weight: 700;
  font-feature-settings: "tnum" 1;
  line-height: 1;
}
.level-label {
  font-family: var(--font-ui);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
}
.level-pct {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

/* ================================================================
   SENSOR GRID
================================================================ */
.sensor-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  padding: 6px 8px 10px;
}
.sensor-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 6px;
  border-right: 1px solid var(--border-separator);
  border-bottom: 1px solid var(--border-separator);
}
.sensor-cell:nth-child(3n) { border-right: none; }
.sensor-cell:nth-child(n+4) { border-bottom: none; }

.sensor-label {
  font-family: var(--font-ui);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.10em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}
.sensor-val {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: -0.01em;
  font-feature-settings: "tnum" 1;
}
.sensor-unit {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

/* ============ 移动端适配 ============ */
@media (max-width: 1024px) {
  .dash-grid { grid-template-columns: 1fr 1fr; }
  .col:nth-child(2) { grid-column: 1 / -1; order: -1; }
}

@media (max-width: 768px) {
  .monitor-root {
    height: auto;
    overflow: visible;
    padding: 8px;
    gap: 8px;
  }
  .kpi-row { grid-template-columns: 1fr 1fr; }
  .dash-grid {
    grid-template-columns: 1fr;
    overflow: visible;
  }
  .col {
    overflow: visible;
    min-height: 0;
  }
  .col:nth-child(2) { order: 0; }
  .alert-list { max-height: 280px; }
}

@media (max-width: 480px) {
  .kpi-row { grid-template-columns: 1fr; }
}
</style>
