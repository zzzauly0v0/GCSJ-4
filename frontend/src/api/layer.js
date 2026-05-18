import request from '@/utils/request'

export const apiLayerList = () => request.get('/layers')
export const apiLayerGet = (id) => request.get(`/layers/${id}`)
export const apiLayerCreate = (data) => request.post('/layers', data)
export const apiLayerUpdate = (id, data) => request.put(`/layers/${id}`, data)
export const apiLayerDelete = (id) => request.delete(`/layers/${id}`)
