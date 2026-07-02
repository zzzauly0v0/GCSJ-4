/**
 * 回放专用图层组合: 风险热力 / 高风险站点 / 灾害影响圈
 *
 * 数据来源: WebSocket /topic/disasters 按日推送的灾害判别结果
 * (由 POST /api/disaster-eval/push?date= 广播), 不再拉取已下线的回放快照接口.
 *
 * 用法 (在 dashboard 里):
 *   const replayLayers = useReplayLayers(olMapRef)
 *   replayLayers.attach()                        // 一次性挂上图层
 *   replayLayers.renderDisasters(events)         // 收到推送时调用
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

const LEVEL_COLOR = { 1: '#3B82F6', 2: '#F59E0B', 3: '#F97316', 4: '#DC2626' }

/** 综合风险指数/等级 -> 热力权重 0..1 */
function riskWeight(compIndex, compLevel) {
  if (compIndex != null) return Math.max(0, Math.min(1, compIndex))
  return Math.max(0, Math.min(1, (compLevel || 0) / 4))
}

/** 灾害等级 -> 影响半径 (米) */
function impactRadiusByLevel(level) {
  return { 1: 3000, 2: 6000, 3: 10000, 4: 18000 }[level] || 4000
}

export function useReplayLayers(olMapRef) {
  const layers = {
    heatmap:    null,    // 风险热力 (按综合风险指数)
    stations:   null,    // 高风险站点位 (按综合等级染色)
    impact:     null,    // 事件影响范围 (Circle 几何, Web Mercator 米单位)
    eventDot:   null,    // 事件中心点
  }
  let attached = false

  function makeStationStyle(feat) {
    const level = feat.get('comp_level') || 1
    const color = LEVEL_COLOR[level]
    const radius = 4 + level * 1.5
    const styles = [
      new Style({ image: new CircleStyle({ radius: radius + 4, fill: new Fill({ color: color + '33' }) }) }),
      new Style({ image: new CircleStyle({ radius, fill: new Fill({ color }), stroke: new Stroke({ color: '#fff', width: 1.5 }) }) })
    ]
    return styles
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
  }

  function detach() {
    attached = false
    const olMap = olMapRef.value
    if (!olMap) return
    Object.values(layers).forEach(l => { if (l) olMap.removeLayer(l) })
  }

  /**
   * 渲染某日推送的灾害判别结果。
   * events: [{ station_code, station_name, lon, lat, comp_level, comp_index, ... }]
   */
  function renderDisasters(events) {
    if (!attached) return
    const olMap = olMapRef.value
    if (!olMap) return

    const list = events || []
    const heatSrc = layers.heatmap.getSource()
    const stationSrc = layers.stations.getSource()
    const impactSrc = layers.impact.getSource()
    const dotSrc = layers.eventDot.getSource()
    heatSrc.clear(); stationSrc.clear(); impactSrc.clear(); dotSrc.clear()

    list.forEach(e => {
      const lon = e.lon, lat = e.lat
      if (lon == null || lat == null) return
      const center3857 = fromLonLat([lon, lat])
      const level = e.comp_level || 1
      const title = e.station_name || e.station_code

      // 风险热力
      const heatFeat = new Feature({ geometry: new Point(center3857) })
      heatFeat.set('weight', riskWeight(e.comp_index, level))
      heatSrc.addFeature(heatFeat)

      // 高风险站点
      const stFeat = new Feature({ geometry: new Point(center3857) })
      stFeat.set('comp_level', level)
      stFeat.set('code', e.station_code)
      stFeat.set('name', e.station_name)
      stationSrc.addFeature(stFeat)

      // 影响圈
      const cf = new Feature({ geometry: new Circle(center3857, impactRadiusByLevel(level)) })
      cf.set('level', level)
      cf.set('title', title)
      impactSrc.addFeature(cf)

      // 中心点标注
      const df = new Feature({ geometry: new Point(center3857) })
      df.set('level', level)
      df.set('title', title)
      df.set('id', e.station_code)
      dotSrc.addFeature(df)
    })
  }

  /** 清空所有回放专题图层 (回放跳转到无风险日时用) */
  function clear() {
    if (!attached) return
    Object.values(layers).forEach(l => l && l.getSource().clear())
  }

  function toggle(key, visible) {
    if (layers[key]) layers[key].setVisible(visible)
  }

  return { attach, detach, renderDisasters, clear, toggle, layers }
}
