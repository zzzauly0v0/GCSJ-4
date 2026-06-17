/**
 * 时间轴回放接口
 * 数据窗口: 2022-09-05 ~ 09-07 四川泸定地震 3 天
 * 后端 ReplayController 已 permitAll, 无需 token
 */
import request from '@/utils/request'

export const apiReplayWindow      = ()           => request.get('/replay/window')
export const apiReplaySnapshot    = (at)         => request.get('/replay/snapshot', { params: { at } })
export const apiReplayAlerts      = (at, limit = 100) => request.get('/replay/alerts', { params: { at, limit } })
export const apiReplayEvents      = (at)         => request.get('/replay/events/geojson', { params: { at } })
export const apiReplayEarthquakes = (at)         => request.get('/replay/earthquakes', { params: { at } })
export const apiReplayStations    = (type)       => request.get('/replay/stations', { params: type ? { type } : {} })
export const apiReplayWeatherSnapshot = (at)     => request.get('/replay/weather/snapshot', { params: { at } })
export const apiReplayWeatherSeries = (stationCode, at, hours = 24) =>
  request.get('/replay/weather/series', { params: { stationCode, at, hours } })
export const apiReplayGeoSeries = (stationCode, metric, at, hours = 24) =>
  request.get('/replay/geo/series', { params: { stationCode, metric, at, hours } })
