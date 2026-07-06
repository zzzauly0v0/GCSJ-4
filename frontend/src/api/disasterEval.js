/**
 * 历史气象灾害判别接口 (后端 /api/disaster-eval/**, 已 permitAll)
 */
import request from '@/utils/request'

export const apiEvalRun = (params = {}) => request.post('/disaster-eval/run', null, { params })

export const apiEvalSeries = (stationCode, from, to) =>
  request.get('/disaster-eval/series', { params: { stationCode, from, to } })

export const apiEvalStations = (year) =>
  request.get('/disaster-eval/stations', { params: year ? { year } : {} })

export const apiEvalEvents = (params = {}) =>
  request.get('/disaster-eval/events', { params })

export const apiEvalHeatmap = (year, metric = 'rainfall') =>
  request.get('/disaster-eval/heatmap', { params: { year, metric } })

export const apiEvalSummary = (year) =>
  request.get('/disaster-eval/summary', { params: year ? { year } : {} })

/** 按日推送该日高等级(橙/红)风险事件到 WebSocket /topic/disasters */
export const apiEvalPush = (date, minLevel = 3) =>
  request.post('/disaster-eval/push', null, { params: { date, minLevel } })

/** 预警统计 (等级分布 + 灾种分布) */
export const apiEvalStats = (params) => request.get('/disaster-eval/stats', { params })

/** 单条预警详情 (含气象监测数据、地形数据、判别依据) */
export const apiEvalDetail = (stationCode, obsDate) =>
  request.get('/disaster-eval/detail', { params: { stationCode, obsDate } })
