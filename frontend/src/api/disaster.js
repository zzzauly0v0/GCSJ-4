import request from '@/utils/request'

export const apiDisasterPage = (params) => request.get('/disasters', { params })
export const apiDisasterGet = (id) => request.get(`/disasters/${id}`)
export const apiDisasterCreate = (data) => request.post('/disasters', data)
export const apiDisasterUpdate = (id, data) => request.put(`/disasters/${id}`, data)
export const apiDisasterDelete = (id) => request.delete(`/disasters/${id}`)
export const apiDisasterLatest = (params) => request.get('/disasters/latest', { params })
export const apiDisasterGeoJson = () => request.get('/disasters/geojson')
