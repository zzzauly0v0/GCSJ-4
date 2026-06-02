/**
 * OpenLayers 初始化 hook
 *
 * 数据流:
 *   存储 EPSG:4326 → 显示 EPSG:3857, 由 ol/proj 自动转换
 *
 * 图层来源:
 *   底图 + 业务图层均来自后端 gis_layer 表 (apiLayerList)
 *   - 底图: code 以 base_ 开头, type=xyz, 直接用 source_url 模板
 *   - 业务: code 以 biz_ 开头, type=vector, 由调用方通过 loadGeoJson(code, geojson) 灌数据
 *   注册表中的 visible / z_index 决定初始可见性与叠放顺序
 *
 * 视觉:
 *   - 预警:   按等级配色, 高等级 (>=3) 带脉冲动画
 *   - 灾害:   半透明等级色多边形
 */
import { onMounted, onBeforeUnmount, ref } from 'vue'
import 'ol/ol.css'
import Map from 'ol/Map'
import View from 'ol/View'
import Tile from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import XYZ from 'ol/source/XYZ'
import GeoJSON from 'ol/format/GeoJSON'
import { fromLonLat } from 'ol/proj'
import { Style, Circle as CircleStyle, Stroke, Fill, Text, RegularShape } from 'ol/style'

const ALERT_COLOR = { 1: '#3B82F6', 2: '#FBBF24', 3: '#F97316', 4: '#EF4444' }

function hexA(hex, a) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${a})`
}

function buildAlertStyle(feat) {
  const level = feat.get('level') || 1
  const color = ALERT_COLOR[level]
  const radius = 6 + level * 2
  return [
    new Style({
      image: new CircleStyle({
        radius: radius + 8,
        fill: new Fill({ color: hexA(color, 0.15) })
      })
    }),
    new Style({
      image: new CircleStyle({
        radius,
        fill: new Fill({ color }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      })
    })
  ]
}

function buildDisasterStyle(feat) {
  const level = feat.get('level') || 1
  const color = ALERT_COLOR[level]
  return new Style({
    stroke: new Stroke({ color, width: 2, lineDash: [6, 4] }),
    fill: new Fill({ color: hexA(color, 0.18) }),
    image: new RegularShape({
      points: 4,
      radius: 8,
      angle: Math.PI / 4,
      fill: new Fill({ color }),
      stroke: new Stroke({ color: '#fff', width: 1.5 })
    })
  })
}

function defaultVectorStyle() {
  return new Style({
    image: new CircleStyle({
      radius: 4,
      fill: new Fill({ color: '#0EA5E9' }),
      stroke: new Stroke({ color: '#fff', width: 1 })
    })
  })
}

// 业务图层 code → 渲染样式映射
const VECTOR_STYLE_REGISTRY = {
  biz_alerts:   buildAlertStyle,
  biz_events:   buildDisasterStyle,
  biz_disasters: buildDisasterStyle  // 向后兼容旧 code
}

export function useMap(target, options = {}) {
  const map = ref(null)
  const olLayers = {}            // code → ol/layer 实例
  const layerMeta = {}            // code → 注册表原始记录 (visible / zIndex / sourceUrl)

  /* ---------- URL 模板替换: tk / 环境变量占位 ---------- */
  function resolveTileUrl(rawUrl) {
    if (!rawUrl) return ''
    return rawUrl.replace(/\$\{(VITE_[A-Z0-9_]+)\}/g, (_, key) => {
      const v = import.meta.env[key]
      return v == null ? '' : v
    })
  }

  /* ---------- 底图构造 ---------- */
  function buildTileLayer(meta) {
    const url = resolveTileUrl(meta.sourceUrl)
    const tile = new Tile({
      source: new XYZ({ url, crossOrigin: 'anonymous' }),
      visible: !!meta.visible,
      zIndex: meta.zIndex ?? 0,
      opacity: 0.95
    })
    // 加载失败 (天地图 tk 缺失等) 兜底走 OSM
    tile.getSource().on('tileloaderror', () => {
      tile.setSource(new OSM({ crossOrigin: 'anonymous' }))
    })
    return tile
  }

  /* ---------- 业务图层构造 ---------- */
  function buildVectorLayer(meta) {
    const styleFn = VECTOR_STYLE_REGISTRY[meta.code] || defaultVectorStyle
    return new VectorLayer({
      source: new VectorSource(),
      style: styleFn,
      visible: !!meta.visible,
      zIndex: meta.zIndex ?? 10
    })
  }

  /* ---------- 默认底图 (注册表为空时兜底) ---------- */
  function defaultOsmLayer() {
    return new Tile({
      source: new OSM({ crossOrigin: 'anonymous' }),
      zIndex: 0,
      opacity: 0.95
    })
  }

  /* ---------- 脉冲动画 (alerts 高等级) ---------- */
  let pulseStart = Date.now()
  function pulseStyle(feat) {
    const level = feat.get('level') || 1
    if (level < 3) return []
    const color = ALERT_COLOR[level]
    const elapsed = (Date.now() - pulseStart) % 1800
    const t = elapsed / 1800
    const r = 8 + t * 26
    const op = 1 - t
    return [new Style({
      image: new CircleStyle({
        radius: r,
        stroke: new Stroke({ color: hexA(color, op * 0.6), width: 2 }),
        fill: new Fill({ color: hexA(color, op * 0.05) })
      })
    })]
  }

  let pulseTimer = null
  let alertPulseLayer = null

  onMounted(() => {
    const center = options.center || [104.0, 35.0]
    const zoom = options.zoom || 5
    const registry = Array.isArray(options.layerRegistry) ? options.layerRegistry : []

    const olLayerList = []

    if (registry.length === 0) {
      // 兜底: 注册表为空 (例如未登录 / 接口失败), 给一个 OSM
      olLayers.base_osm = defaultOsmLayer()
      olLayerList.push(olLayers.base_osm)
    } else {
      for (const meta of registry) {
        layerMeta[meta.code] = meta
        let lyr = null
        if (meta.type === 'xyz' || meta.type === 'wmts') {
          lyr = buildTileLayer(meta)
        } else if (meta.type === 'vector') {
          lyr = buildVectorLayer(meta)
        } else {
          // 其它类型 (raster/wms) 暂未实现, 跳过
          continue
        }
        olLayers[meta.code] = lyr
        olLayerList.push(lyr)
      }
    }

    // 预警脉冲层: 共享 alerts 的 source, 单独一个图层叠加
    if (olLayers.biz_alerts) {
      alertPulseLayer = new VectorLayer({
        source: olLayers.biz_alerts.getSource(),
        style: pulseStyle,
        zIndex: (layerMeta.biz_alerts?.zIndex ?? 30) - 1,
        visible: olLayers.biz_alerts.getVisible()
      })
      olLayerList.push(alertPulseLayer)
    }

    map.value = new Map({
      target: typeof target === 'string' ? target : target.value,
      layers: olLayerList,
      view: new View({
        center: fromLonLat(center),
        zoom,
        projection: 'EPSG:3857'
      }),
      controls: []
    })

    if (alertPulseLayer) {
      pulseTimer = setInterval(() => alertPulseLayer.changed(), 200)
    }
  })

  onBeforeUnmount(() => {
    if (pulseTimer) clearInterval(pulseTimer)
    if (map.value) {
      map.value.setTarget(null)
      map.value = null
    }
  })

  /* ---------- 对外 API ---------- */
  function loadGeoJson(layerKey, geojson) {
    if (!geojson) return
    const lyr = olLayers[layerKey] || olLayers[`biz_${layerKey}`]
    if (!lyr || !lyr.getSource) return
    const fmt = new GeoJSON()
    const feats = fmt.readFeatures(geojson, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857'
    })
    const src = lyr.getSource()
    src.clear()
    src.addFeatures(feats)
  }

  function setLayerVisible(layerKey, visible) {
    const lyr = olLayers[layerKey] || olLayers[`biz_${layerKey}`]
    if (lyr) lyr.setVisible(visible)
    if ((layerKey === 'alerts' || layerKey === 'biz_alerts') && alertPulseLayer) {
      alertPulseLayer.setVisible(visible)
    }
  }

  function flyTo(lon, lat, zoom = 10) {
    if (map.value) {
      map.value.getView().animate({ center: fromLonLat([lon, lat]), zoom, duration: 700 })
    }
  }

  return { map, layers: olLayers, loadGeoJson, setLayerVisible, flyTo }
}
