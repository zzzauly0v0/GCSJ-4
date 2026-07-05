import { onMounted, onBeforeUnmount, ref } from 'vue'
import 'ol/ol.css'
import Map from 'ol/Map'
import View from 'ol/View'
import Tile from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import XYZ from 'ol/source/XYZ'
import TileWMS from 'ol/source/TileWMS'
import GeoJSON from 'ol/format/GeoJSON'
import { fromLonLat } from 'ol/proj'
import { Style, Circle as CircleStyle, Stroke, Fill, RegularShape } from 'ol/style'

/* =========================================================================
 * 1. 基础配置与工具函数（必须在最顶部，供下方样式函数调用）
 * ========================================================================= */
const ALERT_COLOR = { 1: '#3B82F6', 2: '#FBBF24', 3: '#F97316', 4: '#EF4444' }

function hexA(hex, a) {
  const h = hex.replace('#', '')
  const r = parseInt(h.slice(0, 2), 16)
  const g = parseInt(h.slice(2, 4), 16)
  const b = parseInt(h.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${a})`
}

/* =========================================================================
 * 2. 动画性能优化：提取全局复用实例，杜绝内存泄漏
 * ========================================================================= */
const sharedFill = new Fill()
const sharedStroke = new Stroke()
const sharedCircle = new CircleStyle({ radius: 1, fill: sharedFill, stroke: sharedStroke })
const sharedPulseStyle = new Style({ image: sharedCircle })

/* =========================================================================
 * 3. 业务图层样式构建函数
 * ========================================================================= */
function buildAlertStyle(feat) {
  const level = feat.get('level') || 1
  const color = ALERT_COLOR[level]
  const radius = 6 + level * 2
  return [
    new Style({ image: new CircleStyle({ radius: radius + 8, fill: new Fill({ color: hexA(color, 0.15) }) }) }),
    new Style({ image: new CircleStyle({ radius, fill: new Fill({ color }), stroke: new Stroke({ color: '#fff', width: 2 }) }) })
  ]
}

function buildDisasterStyle(feat) {
  const level = feat.get('level') || 1
  const color = ALERT_COLOR[level]
  return new Style({
    stroke: new Stroke({ color, width: 2, lineDash: [6, 4] }),
    fill: new Fill({ color: hexA(color, 0.18) }),
    image: new RegularShape({
      points: 4, radius: 8, angle: Math.PI / 4,
      fill: new Fill({ color }), stroke: new Stroke({ color: '#fff', width: 1.5 })
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

/* =========================================================================
 * 4. 业务图层映射注册表（此时 buildAlertStyle 等函数已全部就绪）
 * ========================================================================= */
const VECTOR_STYLE_REGISTRY = {
  biz_alerts: buildAlertStyle,
  biz_events: buildDisasterStyle,
  biz_disasters: buildDisasterStyle 
}

/* =========================================================================
 * 5. 核心 OpenLayers 初始化 Hook
 * ========================================================================= */
export function useMap(target, options = {}) {
  const map = ref(null)
  let olLayers = {}            
  let layerMeta = {}            
  let alertPulseLayer = null
  let animationFrameId = null

  function resolveTileUrl(rawUrl) {
    if (!rawUrl) return ''
    return rawUrl.replace(/\$\{(VITE_[A-Z0-9_]+)\}/g, (_, key) => {
      return import.meta.env[key] ?? ''
    })
  }

  function buildTileLayer(meta) {
    const url = resolveTileUrl(meta.sourceUrl)
    const tile = new Tile({
      source: new XYZ({ url, crossOrigin: 'anonymous' }),
      visible: !!meta.visible,
      zIndex: meta.zIndex ?? 0,
      opacity: 0.95
    })
    tile.getSource().on('tileloaderror', () => {
      tile.setSource(new OSM({ crossOrigin: 'anonymous' }))
    })
    return tile
  }

  function buildWMSLayer(meta) {
    const url = resolveTileUrl(meta.sourceUrl)
    const layerName = meta.layerName || meta.code
    return new Tile({
      source: new TileWMS({
        url,
        params: { LAYERS: layerName, TILED: true },
        crossOrigin: 'anonymous',
        serverType: 'geoserver'
      }),
      visible: !!meta.visible,
      zIndex: meta.zindex ?? 5,
      opacity: 0.95
    })
  }

  function buildVectorLayer(meta) {
    const styleFn = VECTOR_STYLE_REGISTRY[meta.code] || defaultVectorStyle
    return new VectorLayer({
      source: new VectorSource(),
      style: styleFn,
      visible: !!meta.visible,
      zIndex: meta.zIndex ?? 10
    })
  }

  function defaultOsmLayer() {
    return new Tile({ source: new OSM({ crossOrigin: 'anonymous' }), zIndex: 0, opacity: 0.95 })
  }

  let pulseStart = Date.now()
  function pulseStyle(feat) {
    const level = feat.get('level') || 1
    if (level < 3) return null 
    
    const color = ALERT_COLOR[level]
    const elapsed = (Date.now() - pulseStart) % 1800
    const t = elapsed / 1800
    const r = 8 + t * 26
    const op = 1 - t

    sharedFill.setColor(hexA(color, op * 0.05))
    sharedStroke.setColor(hexA(color, op * 0.6))
    sharedStroke.setWidth(2)
    sharedCircle.setRadius(r)

    return sharedPulseStyle
  }

  function animatePulse() {
    if (!map.value) return
    if (alertPulseLayer && alertPulseLayer.getVisible()) {
      alertPulseLayer.changed() 
    }
    animationFrameId = requestAnimationFrame(animatePulse)
  }

  onMounted(() => {
    const center = options.center || [104.0, 35.0]
    const zoom = options.zoom || 5
    const registry = Array.isArray(options.layerRegistry) ? options.layerRegistry : []
    const olLayerList = []

    if (registry.length === 0) {
      olLayers.base_osm = defaultOsmLayer()
      olLayerList.push(olLayers.base_osm)
    } else {
      for (const meta of registry) {
        layerMeta[meta.code] = meta
        let lyr = null
        if (meta.type === 'xyz' || meta.type === 'wmts') {
          lyr = buildTileLayer(meta)
        } else if (meta.type === 'wms' || meta.type === 'raster') {
          lyr = buildWMSLayer(meta)
        } else if (meta.type === 'vector') {
          lyr = buildVectorLayer(meta)
        } else {
          continue
        }
        olLayers[meta.code] = lyr
        olLayerList.push(lyr)
      }
    }

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
      view: new View({ center: fromLonLat(center), zoom, projection: 'EPSG:3857' }),
      controls: []
    })

    if (alertPulseLayer) {
      animatePulse()
    }
  })

  onBeforeUnmount(() => {
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    if (map.value) {
      map.value.setTarget(null)
      map.value = null
    }
    olLayers = {}
    layerMeta = {}
    alertPulseLayer = null
  })

  /* ---------- 对外 API ---------- */
  function loadGeoJson(layerKey, geojson) {
    if (!geojson) return
    const lyr = olLayers[layerKey] || olLayers[`biz_${layerKey}`]
    if (!lyr || !lyr.getSource) return
    const fmt = new GeoJSON()
    const feats = fmt.readFeatures(geojson, { dataProjection: 'EPSG:4326', featureProjection: 'EPSG:3857' })
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