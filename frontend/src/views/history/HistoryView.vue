<template>
  <div class="history-view">
    <el-card shadow="never" class="toolbar">
      <el-form :inline="true">
        <el-form-item label="年份">
          <el-select v-model="year" style="width: 120px" @change="reload">
            <el-option v-for="y in YEARS" :key="y" :label="y" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="灾种">
          <el-select v-model="type" style="width: 140px" @change="reloadEvents">
            <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="热力指标">
          <el-select v-model="metric" style="width: 150px" @change="loadHeatmap">
            <el-option label="累计/平均降水" value="rainfall" />
            <el-option label="平均风险等级" value="risk" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="running" @click="runEval">执行灾害判别</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="grid">
      <el-card shadow="never" class="map-card">
        <template #header>
          站点风险分布（{{ year }}）
          <span class="legend">
            <span v-for="(c, i) in LEVEL_COLOR" :key="i" class="legend-item">
              <i :style="{ background: c }"></i>{{ LEVEL_NAME[i] }}
            </span>
          </span>
        </template>
        <div ref="mapEl" class="map"></div>
      </el-card>

      <el-card shadow="never" class="heat-card">
        <template #header>区县热力 Top</template>
        <el-table :data="heatmap" size="small" height="360">
          <el-table-column prop="region_name" label="区县" />
          <el-table-column prop="value" label="数值" :formatter="r => Number(r.value ?? 0).toFixed(1)" />
        </el-table>
      </el-card>
    </div>

    <el-card shadow="never" class="events-card">
      <template #header>风险日列表</template>
      <el-table :data="events" size="small">
        <el-table-column prop="station_name" label="站点" />
        <el-table-column prop="obs_date" label="日期" :formatter="r => fmtDate(r.obs_date)" />
        <el-table-column prop="r_eff" label="R_eff" />
        <el-table-column label="综合等级">
          <template #default="{ row }">
            <el-tag :color="LEVEL_COLOR[row.comp_level]" effect="dark" disable-transitions>
              {{ LEVEL_NAME[row.comp_level] }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        layout="total, prev, pager, next" :total="total" :page-size="size"
        :current-page="page" @current-change="onPage" style="margin-top: 12px" />
    </el-card>

    <el-dialog v-model="dlg" :title="`${curStation?.name || ''} 逐日时序`" width="70%" @opened="renderChart">
      <div ref="chartEl" class="chart"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import 'ol/ol.css'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import OSM from 'ol/source/OSM'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'
import { fromLonLat } from 'ol/proj'
import { Style, Circle as CircleStyle, Fill, Stroke } from 'ol/style'
import {
  apiEvalRun, apiEvalStations, apiEvalEvents, apiEvalSeries, apiEvalHeatmap
} from '@/api/disasterEval'

const YEARS = [2020, 2021, 2022, 2023]
const TYPES = [
  { label: '综合', value: 'comp' },
  { label: '降雨滑坡', value: 'landslide' },
  { label: '降雨泥石流', value: 'mudslide' },
  { label: '冻融滑坡', value: 'freezethaw' },
  { label: '坡面崩塌', value: 'collapse' }
]
const LEVEL_COLOR = ['#9CA3AF', '#3B82F6', '#FBBF24', '#F97316', '#EF4444']
const LEVEL_NAME = ['无风险', '蓝色', '黄色', '橙色', '红色']

const year = ref(2022)
const type = ref('comp')
const metric = ref('rainfall')
const running = ref(false)

const events = ref([])
const total = ref(0)
const page = ref(1)
const size = 20
const heatmap = ref([])

const mapEl = ref(null)
const chartEl = ref(null)
let olMap = null
let stationLayer = null
let chartInst = null
const dlg = ref(false)
const curStation = ref(null)
const seriesData = ref([])

const fmtDate = (d) => (d ? String(d).slice(0, 10) : '')

function stationStyle(level) {
  return new Style({
    image: new CircleStyle({
      radius: 5 + level,
      fill: new Fill({ color: LEVEL_COLOR[level] || LEVEL_COLOR[0] }),
      stroke: new Stroke({ color: '#fff', width: 1 })
    })
  })
}

async function loadStations() {
  const { data } = await apiEvalStations(year.value)
  const features = (data || [])
    .filter(s => s.lon != null && s.lat != null)
    .map(s => {
      const f = new Feature({ geometry: new Point(fromLonLat([s.lon, s.lat])), station: s })
      f.setStyle(stationStyle(s.max_level || 0))
      return f
    })
  stationLayer.getSource().clear()
  stationLayer.getSource().addFeatures(features)
}

async function loadEvents() {
  const { data } = await apiEvalEvents({ year: year.value, type: type.value, page: page.value, size })
  events.value = data.list || []
  total.value = data.total || 0
}

async function loadHeatmap() {
  const { data } = await apiEvalHeatmap(year.value, metric.value)
  heatmap.value = data || []
}

function reload() { loadStations(); loadEvents(); loadHeatmap() }
function reloadEvents() { page.value = 1; loadEvents() }
function onPage(p) { page.value = p; loadEvents() }

async function runEval() {
  running.value = true
  try {
    const { data } = await apiEvalRun({ year: year.value })
    ElMessage.success(`判别完成: ${data.stations} 站 / ${data.rowsWritten} 行`)
    reload()
  } catch (e) {
    ElMessage.error('判别执行失败')
  } finally {
    running.value = false
  }
}

async function openStation(station) {
  curStation.value = station
  const { data } = await apiEvalSeries(station.code, `${year.value}-01-01`, `${year.value}-12-31`)
  seriesData.value = data || []
  dlg.value = true
}

function renderChart() {
  const data = seriesData.value
  if (chartInst) chartInst.dispose()
  chartInst = echarts.init(chartEl.value)
  chartInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['降水', 'R_eff', '平均气温'] },
    xAxis: { type: 'category', data: data.map(d => fmtDate(d.obs_date)) },
    yAxis: [{ type: 'value', name: 'mm' }, { type: 'value', name: '℃' }],
    series: [
      { name: '降水', type: 'bar', data: data.map(d => d.rainfall) },
      { name: 'R_eff', type: 'line', data: data.map(d => d.r_eff) },
      { name: '平均气温', type: 'line', yAxisIndex: 1, data: data.map(d => d.temp_avg) }
    ]
  })
}

onMounted(() => {
  olMap = new Map({
    target: mapEl.value,
    layers: [new TileLayer({ source: new OSM() })],
    view: new View({ center: fromLonLat([102.7, 30.6]), zoom: 6 })
  })
  stationLayer = new VectorLayer({ source: new VectorSource() })
  olMap.addLayer(stationLayer)
  olMap.on('singleclick', (evt) => {
    olMap.forEachFeatureAtPixel(evt.pixel, (f) => {
      const s = f.get('station')
      if (s) { openStation(s); return true }
    })
  })
  reload()
})

onBeforeUnmount(() => {
  if (chartInst) chartInst.dispose()
  if (olMap) olMap.setTarget(null)
})
</script>

<style scoped>
.history-view { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.grid { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.map { width: 100%; height: 420px; }
.chart { width: 100%; height: 420px; }
.legend { float: right; font-size: 12px; font-weight: normal; }
.legend-item { margin-left: 10px; display: inline-flex; align-items: center; }
.legend-item i { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 3px; }
</style>
