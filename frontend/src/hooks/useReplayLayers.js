/**
 * 回放专用图层组合: 雨量热力 / 气象站 / 地震震中 (含波纹) / 灾害事件影响圈
 *
 * 用法 (在 dashboard 里):
 *   const replayLayers = useReplayLayers(olMapRef)
 *   await replayLayers.attach()                  // 一次性挂上四个图层
 *   replayLayers.refresh(virtualNowIso)          // 时间轴每次推进调用
 *   replayLayers.toggle('heatmap', visible)      // 控制单层显隐
 *
 * 注意: 所有渲染都建立在已存在的 olMap 实例上, 不接管 useMap 的注册表.
 */
import Heatmap from 'ol/layer/Heatmap'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import Feature from 'ol/Feature'
import Point from 'ol/geom/Point'
import Circle from 'ol/geom/Circle'
import { fromLonLat } from 'ol/proj'
import { Style, Stroke, Fill, Circle as CircleStyle, Text } from 'ol/style'
import {
  apiReplayWeatherSnapshot,
  apiReplayEarthquakes,
  apiReplayEvents,
} from '@/api/replay'

const LEVEL_COLOR = { 1: '#3B82F6', 2: '#F59E0B', 3: '#F97316', 4: '#DC2626' }

/** 雨强 -> 0..1 (24h 雨量, 100mm 满档) */
function rainWeight(rain24h) {
  if (rain24h == null) return 0
  return Math.max(0, Math.min(1, rain24h / 100))
}

/** 雨强 -> 染色 */
function rainColor(rain1h) {
  if (rain1h == null || rain1h < 1) return '#94A3B8'
  if (rain1h < 8)   return '#60A5FA'
  if (rain1h < 16)  return '#FBBF24'
  if (rain1h < 32)  return '#F97316'
  return '#DC2626'
}

/** 灾害等级 -> 影响半径 (米) */
function impactRadiusByLevel(level) {
  return { 1: 3000, 2: 6000, 3: 10000, 4: 18000 }[level] || 4000
}

export function useReplayLayers(olMapRef) {
  const layers = {
    heatmap:    null,    // 雨量热力
    stations:   null,    // 气象站点位 (按 1h 雨强染色)
    quake:      null,    // 地震震中波纹
    impact:     null,    // 事件影响范围 (Circle 几何, Web Mercator 米单位)
    eventDot:   null,    // 事件中心点
  }
  let pulseStart = Date.now()
  let rafId = null
  let attached = false

  function makeStationStyle(feat) {
    const r1h = feat.get('rainfall_1h') ?? 0
    const r24 = feat.get('rainfall_24h') ?? 0
    const color = rainColor(r1h)
    const radius = 4 + Math.min(8, r24 / 8)
    const styles = [
      new Style({ image: new CircleStyle({ radius: radius + 4, fill: new Fill({ color: color + '33' }) }) }),
      new Style({ image: new CircleStyle({ radius, fill: new Fill({ color }), stroke: new Stroke({ color: '#fff', width: 1.5 }) }) })
    ]
    return styles
  }

  function makeQuakeStyle(feat) {
    const mag = feat.get('magnitude') || 4
    const isMain = feat.get('isMain') === true
    const elapsed = (Date.now() - pulseStart) % 1800 / 1800
    const color = mag >= 6 ? '#DC2626' : (mag >= 5 ? '#F97316' : '#F59E0B')
    const baseR = 5 + (mag - 4) * 4
    return [
      new Style({
        image: new CircleStyle({
          radius: baseR + elapsed * 30,
          fill: new Fill({ color: color + Math.round((1 - elapsed) * 60).toString(16).padStart(2, '0') }),
          stroke: new Stroke({ color: color, width: 1.5 })
        })
      }),
      new Style({
        image: new CircleStyle({
          radius: baseR,
          fill: new Fill({ color }),
          stroke: new Stroke({ color: isMain ? '#FEF3C7' : '#fff', width: isMain ? 3 : 1.5 })
        }),
        text: isMain ? new Text({
          text: `M${mag.toFixed(1)} 主震`,
          font: 'bold 11px "PingFang SC", sans-serif',
          fill: new Fill({ color: '#7F1D1D' }),
          stroke: new Stroke({ color: 'rgba(255,255,255,0.9)', width: 3 }),
          offsetY: -baseR - 10,
        }) : new Text({
          text: `M${mag.toFixed(1)}`,
          font: '10px "DIN Alternate", monospace',
          fill: new Fill({ color: '#7F1D1D' }),
          stroke: new Stroke({ color: 'rgba(255,255,255,0.9)', width: 2 }),
          offsetY: -baseR - 8,
        })
      })
    ]
  }

  function makeImpactStyle(feat) {
    const level = feat.get('level') || 2
    const color = LEVEL_COLOR[level]
    return new Style({
      stroke: new Stroke({ color, width: 1.6, lineDash: [6, 4] }),
      fill:   new Fill({ color: color + '1A' }),
    })
  }

  function makeEventDotStyle(feat) {
    const level = feat.get('level') || 2
    const color = LEVEL_COLOR[level]
    return [
      new Style({ image: new CircleStyle({ radius: 6, fill: new Fill({ color: color + '40' }) }) }),
      new Style({
        image: new CircleStyle({
          radius: 4,
          fill: new Fill({ color }),
          stroke: new Stroke({ color: '#fff', width: 1.5 })
        }),
        text: new Text({
          text: feat.get('title') || '',
          font: '10px "PingFang SC", sans-serif',
          fill: new Fill({ color: '#0F172A' }),
          stroke: new Stroke({ color: 'rgba(255,255,255,0.85)', width: 2 }),
          offsetY: -10,
        })
      })
    ]
  }

  function attach() {
    const olMap = olMapRef.value
    if (!olMap || attached) return
    attached = true

    // 1) 雨量热力
    layers.heatmap = new Heatmap({
      source: new VectorSource(),
      blur: 24,
      radius: 18,
      weight: (feat) => feat.get('weight') ?? 0,
      gradient: ['#0EA5E9', '#22D3EE', '#FACC15', '#F97316', '#DC2626'],
      zIndex: 22,
      opacity: 0.65,
    })
    olMap.addLayer(layers.heatmap)

    // 2) 气象站点位
    layers.stations = new VectorLayer({
      source: new VectorSource(),
      style: makeStationStyle,
      zIndex: 26,
    })
    olMap.addLayer(layers.stations)

    // 3) 灾害事件影响圆 + 中心点 (在事件之上, 站点之下)
    layers.impact = new VectorLayer({
      source: new VectorSource(),
      style: makeImpactStyle,
      zIndex: 28,
    })
    olMap.addLayer(layers.impact)
    layers.eventDot = new VectorLayer({
      source: new VectorSource(),
      style: makeEventDotStyle,
      zIndex: 32,
    })
    olMap.addLayer(layers.eventDot)

    // 4) 地震震中 (zIndex 最高, 视觉穿透)
    layers.quake = new VectorLayer({
      source: new VectorSource(),
      style: makeQuakeStyle,
      zIndex: 34,
    })
    olMap.addLayer(layers.quake)

    // 波纹动画
    pulseStart = Date.now()
    const tick = () => {
      if (!attached) return
      if (layers.quake?.getVisible() && layers.quake.getSource().getFeatures().length) {
        layers.quake.changed()
      }
      rafId = requestAnimationFrame(tick)
    }
    tick()
  }

  function detach() {
    attached = false
    if (rafId) cancelAnimationFrame(rafId)
    rafId = null
    const olMap = olMapRef.value
    if (!olMap) return
    Object.values(layers).forEach(l => { if (l) olMap.removeLayer(l) })
  }

  async function refresh(atIso) {
    if (!attached || !atIso) return
    const olMap = olMapRef.value
    if (!olMap) return

    const [snapshot, quakes, eventsGeo] = await Promise.all([
      apiReplayWeatherSnapshot(atIso).catch(() => []),
      apiReplayEarthquakes(atIso).catch(() => []),
      apiReplayEvents(atIso).catch(() => ({ features: [] })),
    ])

    // --- 雨量热力 + 站点 ---
    const heatSrc = layers.heatmap.getSource()
    const stationSrc = layers.stations.getSource()
    heatSrc.clear()
    stationSrc.clear()
    ;(snapshot || []).forEach(s => {
      if (s.lon == null || s.lat == null) return
      const pt = new Point(fromLonLat([s.lon, s.lat]))
      const heatFeat = new Feature({ geometry: pt })
      heatFeat.set('weight', rainWeight(s.rainfall_24h))
      heatSrc.addFeature(heatFeat)

      const stFeat = new Feature({ geometry: pt })
      stFeat.set('rainfall_1h',  s.rainfall_1h)
      stFeat.set('rainfall_24h', s.rainfall_24h)
      stFeat.set('code', s.code)
      stFeat.set('name', s.name)
      stationSrc.addFeature(stFeat)
    })

    // --- 地震震中 (主震高亮) ---
    const quakeSrc = layers.quake.getSource()
    quakeSrc.clear()
    let mainQuake = null
    ;(quakes || []).forEach(q => {
      if (!mainQuake || (q.magnitude || 0) > (mainQuake.magnitude || 0)) mainQuake = q
    })
    ;(quakes || []).forEach(q => {
      if (q.lon == null || q.lat == null) return
      const f = new Feature({ geometry: new Point(fromLonLat([q.lon, q.lat])) })
      f.set('magnitude', q.magnitude)
      f.set('isMain', q === mainQuake)
      f.set('occurredAt', q.occurred_at)
      f.set('locationName', q.location_name)
      quakeSrc.addFeature(f)
    })

    // --- 灾害事件影响圈 + 中心点 ---
    const impactSrc = layers.impact.getSource()
    const dotSrc = layers.eventDot.getSource()
    impactSrc.clear(); dotSrc.clear()
    const features = eventsGeo?.features || []
    features.forEach(feat => {
      const coords = feat.geometry?.coordinates
      const props = feat.properties || {}
      if (!coords || coords[0] == null) return
      const center3857 = fromLonLat(coords)
      const radius = impactRadiusByLevel(props.level)
      const cf = new Feature({ geometry: new Circle(center3857, radius) })
      cf.set('level', props.level)
      cf.set('type',  props.type)
      cf.set('title', props.title)
      impactSrc.addFeature(cf)

      const df = new Feature({ geometry: new Point(center3857) })
      df.set('level', props.level)
      df.set('type',  props.type)
      df.set('title', props.title)
      df.set('id',    props.id)
      dotSrc.addFeature(df)
    })
  }

  function toggle(key, visible) {
    if (layers[key]) layers[key].setVisible(visible)
  }

  return { attach, detach, refresh, toggle, layers }
}
