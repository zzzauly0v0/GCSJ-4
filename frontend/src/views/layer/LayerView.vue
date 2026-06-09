<template>
  <div class="layer-page">
    <el-tabs v-model="tab" class="layer-tabs" type="card">
      <!-- ============ 图层管理 ============ -->
      <el-tab-pane label="图层管理面板" name="manage">
        <div class="layer-workspace">
          <!-- 左侧：图层勾选与样式 -->
          <aside class="layer-side">
            <div class="side-block">
              <div class="block-title">底图</div>
              <el-radio-group v-model="basemap" @change="onBasemapChange">
                <el-radio value="osm">OSM 街图</el-radio>
                <el-radio value="amap">高德矢量</el-radio>
                <el-radio value="amap_dark">高德深色</el-radio>
              </el-radio-group>
            </div>

            <div class="side-block">
              <div class="block-title">业务图层</div>
              <div v-for="l in layers" :key="l.code" class="layer-row">
                <el-checkbox :model-value="l.visible" @change="onToggle(l, $event)">
                  <span class="dot" :style="{ background: l.color }" />
                  {{ l.name }}
                </el-checkbox>
                <div class="layer-meta">
                  <span class="meta-tag" :class="`tag-${l.type}`">{{ l.type }}</span>
                  <span class="meta-count">{{ l.count != null ? l.count : '—' }}</span>
                </div>
                <el-slider
                  v-if="l.visible && l.type !== 'group'"
                  class="opacity-slider"
                  :model-value="l.opacity * 100"
                  :show-tooltip="false"
                  size="small"
                  @input="onOpacity(l, $event)"
                />
              </div>
            </div>

            <div class="side-block">
              <div class="block-title">视口</div>
              <el-button size="small" @click="zoomToSichuan">飞回四川</el-button>
              <div class="coord-line">
                <span>LON {{ centerLon }}</span>
                <span>LAT {{ centerLat }}</span>
                <span>Z {{ zoomLevel }}</span>
              </div>
            </div>
          </aside>

          <!-- 右侧：地图 -->
          <section class="layer-map-wrap">
            <div ref="mapEl" class="layer-map" />
            <div v-if="loading" class="map-loading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>加载图层数据 …</span>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <!-- ============ 数据维护 ============ -->
      <el-tab-pane label="图层数据维护" name="crud">
        <el-card class="gcsj-card">
          <div class="header">
            <span class="title">空间图层目录 (gis_layer)</span>
            <el-button type="primary" @click="onAdd">新增图层</el-button>
          </div>
          <el-table :data="rows" border size="small">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="code" label="编码" width="160" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="sourceUrl" label="数据源" />
            <el-table-column label="可见" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.visible" @change="onSwitchVisible(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="onEdit(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="onDel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-dialog v-model="dlg" :title="form.id ? '编辑图层' : '新增图层'" width="520px">
          <el-form :model="form" label-width="120px">
            <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
            <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
            <el-form-item label="类型">
              <el-select v-model="form.type">
                <el-option v-for="t in ['vector','raster','wms','wmts','xyz']" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源 URL"><el-input v-model="form.sourceUrl" /></el-form-item>
            <el-form-item label="GeoServer 工作区"><el-input v-model="form.workspace" /></el-form-item>
            <el-form-item label="GeoServer 图层名"><el-input v-model="form.layerName" /></el-form-item>
            <el-form-item label="样式"><el-input v-model="form.style" /></el-form-item>
            <el-form-item label="层级"><el-input-number v-model="form.zIndex" /></el-form-item>
            <el-form-item label="可见"><el-switch v-model="form.visible" /></el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dlg = false">取消</el-button>
            <el-button type="primary" @click="onSave">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import 'ol/ol.css'
import OlMap from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import XYZ from 'ol/source/XYZ'
import GeoJSON from 'ol/format/GeoJSON'
import { fromLonLat, toLonLat } from 'ol/proj'
import { defaults as defaultControls, ScaleLine } from 'ol/control'
import { Style, Stroke, Fill, Circle as CircleStyle, Text } from 'ol/style'
import { apiLayerList, apiLayerCreate, apiLayerUpdate, apiLayerDelete } from '@/api/layer'
import { apiRegionsGeoJson, apiRiversGeoJson, apiSettlementsGeoJson } from '@/api/gis'

/* ------------------------------ Tab ------------------------------ */
const tab = ref('manage')

/* ------------------------------ 地图实例 ------------------------------ */
const mapEl = ref(null)
let olMap = null
const loading = ref(false)

const SICHUAN_CENTER = [102.7, 30.65]
const SICHUAN_ZOOM = 6.6

const centerLon = ref(SICHUAN_CENTER[0].toFixed(3))
const centerLat = ref(SICHUAN_CENTER[1].toFixed(3))
const zoomLevel = ref(SICHUAN_ZOOM.toFixed(1))

/* ------------------------------ 图层注册 ------------------------------ */
const basemap = ref('osm')
let baseLayer = null

// OL 实例的引用 (key = layer.code)
const olLayers = {}

// 与左侧面板绑定的图层定义 (响应式)
const layers = ref([
  {
    code: 'sc_province', name: '四川省界', type: 'polygon', color: '#22D3EE',
    visible: true, opacity: 0.85, zIndex: 20, count: null,
    fetcher: () => apiRegionsGeoJson({ level: 1, adcode: '510000' }),
    style: { stroke: '#22D3EE', strokeWidth: 2.4, fill: 'rgba(34,211,238,0.04)', strokeDash: null }
  },
  {
    code: 'sc_city', name: '四川市州界', type: 'polygon', color: '#60A5FA',
    visible: false, opacity: 0.75, zIndex: 21, count: null,
    fetcher: () => apiRegionsGeoJson({ level: 2, parent: '510000' }),
    style: { stroke: '#60A5FA', strokeWidth: 1.4, fill: 'rgba(96,165,250,0.04)', strokeDash: null, label: 'name' }
  },
  {
    code: 'sc_county', name: '四川区县界', type: 'polygon', color: '#A78BFA',
    visible: false, opacity: 0.7, zIndex: 22, count: null,
    fetcher: () => apiRegionsGeoJson({ level: 3 }),
    style: { stroke: '#A78BFA', strokeWidth: 0.9, fill: 'rgba(167,139,250,0.03)', strokeDash: [3, 3] }
  },
  {
    code: 'sc_river', name: '主要河流', type: 'line', color: '#38BDF8',
    visible: true, opacity: 0.9, zIndex: 25, count: null,
    fetcher: () => apiRiversGeoJson(),
    style: { stroke: '#38BDF8', strokeWidth: 2.0, label: 'name' }
  },
  {
    code: 'sc_settlement', name: '居民点', type: 'point', color: '#F59E0B',
    visible: false, opacity: 0.95, zIndex: 30, count: null,
    fetcher: () => apiSettlementsGeoJson(),
    style: { stroke: '#1F2937', fill: '#F59E0B', radius: 5, label: 'name' }
  }
])

/* ------------------------------ 底图 ------------------------------ */
function buildBasemap(kind) {
  if (kind === 'amap') {
    return new TileLayer({
      source: new XYZ({
        url: 'https://webrd0{1-4}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}',
        crossOrigin: 'anonymous'
      }),
      zIndex: 0
    })
  }
  if (kind === 'amap_dark') {
    return new TileLayer({
      source: new XYZ({
        url: 'https://webst0{1-4}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
        crossOrigin: 'anonymous'
      }),
      zIndex: 0
    })
  }
  return new TileLayer({ source: new OSM({ crossOrigin: 'anonymous' }), zIndex: 0 })
}

/* ------------------------------ 矢量样式 ------------------------------ */
function makeStyleFn(meta) {
  return (feature, resolution) => {
    const s = meta.style || {}
    const styles = []
    if (meta.type === 'point') {
      styles.push(new Style({
        image: new CircleStyle({
          radius: s.radius || 5,
          fill: new Fill({ color: s.fill || meta.color }),
          stroke: new Stroke({ color: s.stroke || '#fff', width: 1.4 })
        })
      }))
    } else {
      styles.push(new Style({
        stroke: new Stroke({
          color: s.stroke || meta.color,
          width: s.strokeWidth || 1.5,
          lineDash: s.strokeDash || undefined
        }),
        fill: s.fill ? new Fill({ color: s.fill }) : undefined
      }))
    }
    if (s.label && resolution < 4500) {
      const txt = feature.get(s.label)
      if (txt) {
        styles.push(new Style({
          text: new Text({
            text: String(txt),
            font: '11px "PingFang SC", sans-serif',
            fill: new Fill({ color: '#0F172A' }),
            stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 3 }),
            offsetY: meta.type === 'point' ? -12 : 0
          })
        }))
      }
    }
    return styles
  }
}

function buildVectorLayer(meta) {
  const lyr = new VectorLayer({
    source: new VectorSource(),
    style: makeStyleFn(meta),
    visible: !!meta.visible,
    opacity: meta.opacity ?? 1,
    zIndex: meta.zIndex ?? 10
  })
  return lyr
}

/* ------------------------------ 数据加载 ------------------------------ */
const fmt = new GeoJSON()

async function loadLayer(meta) {
  if (!meta.fetcher) return
  loading.value = true
  try {
    const geojson = await meta.fetcher()
    const lyr = olLayers[meta.code]
    if (!lyr) return
    const features = fmt.readFeatures(geojson, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857'
    })
    lyr.getSource().clear()
    lyr.getSource().addFeatures(features)
    meta.count = features.length
    meta.loaded = true
  } catch (e) {
    ElMessage.error(`图层 [${meta.name}] 加载失败: ${e?.message || e}`)
  } finally {
    loading.value = false
  }
}

/* ------------------------------ UI 交互 ------------------------------ */
async function onToggle(meta, visible) {
  meta.visible = !!visible
  const lyr = olLayers[meta.code]
  if (lyr) lyr.setVisible(meta.visible)
  if (meta.visible && !meta.loaded) {
    await loadLayer(meta)
  }
}

function onOpacity(meta, val) {
  meta.opacity = val / 100
  const lyr = olLayers[meta.code]
  if (lyr) lyr.setOpacity(meta.opacity)
}

function onBasemapChange(kind) {
  if (!olMap) return
  if (baseLayer) olMap.removeLayer(baseLayer)
  baseLayer = buildBasemap(kind)
  olMap.getLayers().insertAt(0, baseLayer)
}

function zoomToSichuan() {
  if (!olMap) return
  olMap.getView().animate({ center: fromLonLat(SICHUAN_CENTER), zoom: SICHUAN_ZOOM, duration: 600 })
}

/* ------------------------------ 数据维护 (CRUD) ------------------------------ */
const rows = ref([])
const dlg = ref(false)
const form = reactive({ id: null, name: '', code: '', type: 'vector', sourceUrl: '', workspace: '', layerName: '', style: '', visible: true, zIndex: 0 })

async function loadCrud() { rows.value = await apiLayerList() }
function reset() { Object.assign(form, { id: null, name: '', code: '', type: 'vector', sourceUrl: '', workspace: '', layerName: '', style: '', visible: true, zIndex: 0 }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  if (form.id) await apiLayerUpdate(form.id, form)
  else await apiLayerCreate(form)
  ElMessage.success('已保存')
  dlg.value = false
  loadCrud()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除图层 [${row.name}]?`, '确认')
  await apiLayerDelete(row.id)
  ElMessage.success('已删除')
  loadCrud()
}
async function onSwitchVisible(row) {
  await apiLayerUpdate(row.id, row)
  ElMessage.success('已更新')
}

/* ------------------------------ 生命周期 ------------------------------ */
onMounted(async () => {
  await nextTick()

  baseLayer = buildBasemap(basemap.value)

  layers.value.forEach(meta => {
    olLayers[meta.code] = buildVectorLayer(meta)
  })

  olMap = new OlMap({
    target: mapEl.value,
    layers: [baseLayer, ...Object.values(olLayers)],
    view: new View({
      center: fromLonLat(SICHUAN_CENTER),
      zoom: SICHUAN_ZOOM,
      projection: 'EPSG:3857',
      minZoom: 4,
      maxZoom: 14
    }),
    controls: defaultControls({ zoom: true, attribution: false }).extend([new ScaleLine()])
  })

  olMap.on('moveend', () => {
    const view = olMap.getView()
    const c = toLonLat(view.getCenter() || [0, 0])
    centerLon.value = c[0].toFixed(3)
    centerLat.value = c[1].toFixed(3)
    zoomLevel.value = view.getZoom().toFixed(1)
  })

  // 默认勾选的图层立即加载
  for (const meta of layers.value) {
    if (meta.visible) await loadLayer(meta)
  }

  loadCrud()
})

onBeforeUnmount(() => {
  if (olMap) {
    olMap.setTarget(null)
    olMap = null
  }
})
</script>

<style scoped lang="scss">
.layer-page {
  padding: 12px;
  height: calc(100vh - 64px);
  box-sizing: border-box;
  background: #F8FAFC;
}

:deep(.layer-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;

  .el-tabs__content {
    flex: 1;
    overflow: hidden;
  }
  .el-tab-pane {
    height: 100%;
  }
}

.layer-workspace {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 12px;
  height: 100%;
}

.layer-side {
  background: #FFFFFF;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  padding: 14px 14px 8px;
  overflow-y: auto;
  font-size: 13px;
}

.side-block {
  margin-bottom: 18px;

  .block-title {
    font-weight: 600;
    color: #334155;
    letter-spacing: 0.06em;
    margin-bottom: 8px;
    font-size: 12px;
    text-transform: uppercase;
  }

  :deep(.el-radio-group) {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
}

.layer-row {
  border-top: 1px dashed #E2E8F0;
  padding: 8px 0;

  &:first-child { border-top: none; padding-top: 0; }

  .dot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    margin-right: 6px;
    box-shadow: 0 0 6px currentColor;
  }
  .layer-meta {
    margin-top: 2px;
    margin-left: 22px;
    display: flex;
    gap: 8px;
    font-size: 11px;
    color: #94A3B8;
  }
  .meta-tag {
    padding: 0 6px;
    border-radius: 4px;
    background: #EEF2FF;
    color: #6366F1;
    font-family: monospace;
  }
  .meta-tag.tag-line { background: #E0F2FE; color: #0284C7; }
  .meta-tag.tag-point { background: #FFF7ED; color: #C2410C; }
  .opacity-slider {
    margin-top: 6px;
    margin-left: 22px;
    width: calc(100% - 22px);
  }
}

.coord-line {
  margin-top: 8px;
  font-family: monospace;
  font-size: 11px;
  color: #64748B;
  display: flex;
  justify-content: space-between;
}

.layer-map-wrap {
  position: relative;
  background: #FFFFFF;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  overflow: hidden;
}
.layer-map {
  width: 100%;
  height: 100%;
}
.map-loading {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(15, 23, 42, 0.82);
  color: #fff;
  padding: 6px 14px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  z-index: 10;
}

.header { display: flex; justify-content: space-between; margin-bottom: 12px;
  .title { font-size: 16px; font-weight: 600; }
}

.gcsj-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  :deep(.el-card__body) {
    flex: 1;
    overflow: auto;
  }
}
</style>
