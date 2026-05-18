/**
 * OpenLayers 初始化 hook
 * 存储 EPSG:4326 → 显示 EPSG:3857, 由 ol/proj 自动转换
 *
 * 视觉:
 *   - 暗色底图 (Carto Dark, 兜底 OSM)
 *   - 传感器: 绿色发光小点
 *   - 预警:   按等级配色, 红色带脉冲动画
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

export function useMap(target, options = {}) {
  const map = ref(null)
  const layers = {}

  function buildBaseLayer() {
    const tileUrl = import.meta.env.VITE_MAP_TILE_URL
    // 优先暗色底图 (CartoDB dark_all, 公开瓦片)
    const darkUrl = 'https://{a-d}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png'
    if (tileUrl && tileUrl.includes('cartocdn')) {
      return new Tile({ source: new XYZ({ url: tileUrl, crossOrigin: 'anonymous' }), opacity: 0.95 })
    }
    if (tileUrl && !tileUrl.includes('openstreetmap')) {
      return new Tile({ source: new XYZ({ url: tileUrl, crossOrigin: 'anonymous' }), opacity: 0.95 })
    }
    // 默认走暗色 (兜底 OSM)
    return new Tile({
      source: new XYZ({ url: darkUrl, crossOrigin: 'anonymous', attributions: '© OpenStreetMap · © CARTO' }),
      opacity: 0.92
    })
  }

  function fallbackToOSM(layer) {
    layer.setSource(new OSM({ crossOrigin: 'anonymous' }))
  }

  /* ---------- 样式 ---------- */
  function buildSensorStyle(feat) {
    return [
      new Style({
        image: new CircleStyle({
          radius: 8,
          fill: new Fill({ color: 'rgba(34,197,94,0.15)' }),
          stroke: new Stroke({ color: 'rgba(34,197,94,0)', width: 0 })
        })
      }),
      new Style({
        image: new CircleStyle({
          radius: 4,
          fill: new Fill({ color: '#22C55E' }),
          stroke: new Stroke({ color: '#fff', width: 1 })
        }),
        text: new Text({
          text: feat.get('name') || '',
          offsetY: -14,
          font: '500 10px PingFang SC, sans-serif',
          fill: new Fill({ color: '#E2E8F0' }),
          stroke: new Stroke({ color: 'rgba(15, 28, 60, 0.85)', width: 3 })
        })
      })
    ]
  }

  function buildAlertStyle(feat) {
    const level = feat.get('level') || 1
    const color = ALERT_COLOR[level]
    const radius = 6 + level * 2
    return [
      // 外圈光晕
      new Style({
        image: new CircleStyle({
          radius: radius + 8,
          fill: new Fill({ color: hexA(color, 0.15) })
        })
      }),
      // 主点
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

  function hexA(hex, a) {
    const h = hex.replace('#', '')
    const r = parseInt(h.slice(0, 2), 16)
    const g = parseInt(h.slice(2, 4), 16)
    const b = parseInt(h.slice(4, 6), 16)
    return `rgba(${r},${g},${b},${a})`
  }

  /* ---------- 脉冲动画 ---------- */
  let pulseStart = Date.now()
  function pulseStyle(feat) {
    const level = feat.get('level') || 1
    if (level < 3) return [] // 仅高等级脉冲
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

  onMounted(() => {
    const center = options.center || [104.0, 35.0]
    const zoom = options.zoom || 5

    layers.base = buildBaseLayer()
    layers.base.getSource().on('tileloaderror', () => fallbackToOSM(layers.base))

    layers.sensors = new VectorLayer({
      source: new VectorSource(),
      style: buildSensorStyle,
      zIndex: 10
    })
    layers.alerts = new VectorLayer({
      source: new VectorSource(),
      style: buildAlertStyle,
      zIndex: 30
    })
    layers.alertPulse = new VectorLayer({
      source: layers && new VectorSource(),
      zIndex: 25
    })
    layers.disasters = new VectorLayer({
      source: new VectorSource(),
      style: buildDisasterStyle,
      zIndex: 15
    })

    map.value = new Map({
      target: typeof target === 'string' ? target : target.value,
      layers: [layers.base, layers.disasters, layers.sensors, layers.alertPulse, layers.alerts],
      view: new View({
        center: fromLonLat(center),
        zoom,
        projection: 'EPSG:3857'
      }),
      controls: []
    })

    // 脉冲层渲染
    layers.alertPulse.setSource(layers.alerts.getSource())
    layers.alertPulse.setStyle(pulseStyle)

    // 持续重绘脉冲层 (轻量, 仅 5 fps)
    pulseTimer = setInterval(() => {
      if (layers.alertPulse) layers.alertPulse.changed()
    }, 200)
  })

  let pulseTimer = null
  onBeforeUnmount(() => {
    if (pulseTimer) clearInterval(pulseTimer)
    if (map.value) {
      map.value.setTarget(null)
      map.value = null
    }
  })

  function loadGeoJson(layerKey, geojson) {
    if (!geojson || !layers[layerKey]) return
    const fmt = new GeoJSON()
    const feats = fmt.readFeatures(geojson, {
      dataProjection: 'EPSG:4326',
      featureProjection: 'EPSG:3857'
    })
    const src = layers[layerKey].getSource()
    src.clear()
    src.addFeatures(feats)
  }

  function setLayerVisible(layerKey, visible) {
    if (layers[layerKey]) layers[layerKey].setVisible(visible)
    if (layerKey === 'alerts' && layers.alertPulse) layers.alertPulse.setVisible(visible)
  }

  function flyTo(lon, lat, zoom = 10) {
    if (map.value) {
      map.value.getView().animate({ center: fromLonLat([lon, lat]), zoom, duration: 700 })
    }
  }

  return { map, layers, loadGeoJson, setLayerVisible, flyTo }
}
