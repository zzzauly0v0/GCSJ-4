import request from '@/utils/request'

export const apiSensorPage = (params) => request.get('/sensors', { params })
export const apiSensorAll = () => request.get('/sensors/all')
export const apiSensorGet = (id) => request.get(`/sensors/${id}`)
export const apiSensorCreate = (data) => request.post('/sensors', data)
export const apiSensorUpdate = (id, data) => request.put(`/sensors/${id}`, data)
export const apiSensorDelete = (id) => request.delete(`/sensors/${id}`)
export const apiSensorGeoJson = () => request.get('/sensors/geojson')
