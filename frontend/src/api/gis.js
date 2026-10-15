/**
 * 四川空间数据 GeoJSON 接口
 * - 行政区: level=1 省 / 2 市 / 3 县, parent=父级 adcode
 * - 河流  : 全部 LineString
 * - 居民点: type 可选 city/county/town
 *
 * 这些接口在 backend 侧 permitAll, 前端不强制带 token, 也能从 LayerView/Dashboard 调用
 */
import request from '@/utils/request'

export const apiRegionsGeoJson = (params = {}) => request.get('/regions/geojson', { params })
export const apiRiversGeoJson = () => request.get('/rivers/geojson')
export const apiSettlementsGeoJson = (params = {}) => request.get('/settlements/geojson', { params })
