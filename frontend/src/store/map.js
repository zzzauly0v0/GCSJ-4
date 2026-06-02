import { defineStore } from 'pinia'

export const useMapStore = defineStore('map', {
  state: () => ({
    // 默认中心: 中国大致几何中心 (EPSG:4326), 用于初始化地图
    center: [104.0, 35.0],
    zoom: 5,
    activeLayers: ['base', 'alerts', 'disasters'],
    alerts: { type: 'FeatureCollection', features: [] },
    disasters: { type: 'FeatureCollection', features: [] }
  }),
  actions: {
    setView({ center, zoom }) {
      if (center) this.center = center
      if (zoom != null) this.zoom = zoom
    },
    toggleLayer(key) {
      const i = this.activeLayers.indexOf(key)
      if (i >= 0) this.activeLayers.splice(i, 1)
      else this.activeLayers.push(key)
    },
    setAlerts(geo) { this.alerts = geo },
    setDisasters(geo) { this.disasters = geo }
  }
})
