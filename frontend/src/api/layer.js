import request from '@/utils/request'

export const apiLayerList = () => request.get('/layers')
export const apiLayerGet = (id) => request.get(`/layers/${id}`)
export const apiLayerCreate = (data) => request.post('/layers', data)
export const apiLayerUpdate = (id, data) => request.put(`/layers/${id}`, data)
export const apiLayerDelete = (id) => request.delete(`/layers/${id}`)
// 发布到 GeoServer（后端当前为占位接口，会返回 GEOSERVER_ERROR）
export const apiLayerPublish = (id, data) => request.post(`/layers/${id}/publish`, data)
