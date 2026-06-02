import request from '@/utils/request'

export const apiAlertPage = (params) => request.get('/alerts', { params })
export const apiAlertGet = (id) => request.get(`/alerts/${id}`)
export const apiAlertCreate = (data) => request.post('/alerts', data)
export const apiAlertCreateFromEvent = (eventId, params) =>
  request.post(`/alerts/from-event/${eventId}`, null, { params })
export const apiAlertConfirm = (id) => request.post(`/alerts/${id}/confirm`)
export const apiAlertClose = (id) => request.post(`/alerts/${id}/close`)
export const apiAlertLatest = (params) => request.get('/alerts/latest', { params })
export const apiAlertGeoJson = () => request.get('/alerts/geojson')
