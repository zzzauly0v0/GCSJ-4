import request from '@/utils/request'

export const apiAlertPage = (params) => request.get('/alerts', { params })
export const apiAlertGet = (id) => request.get(`/alerts/${id}`)
export const apiAlertCreate = (data) => request.post('/alerts', data)
export const apiAlertConfirm = (id) => request.post(`/alerts/${id}/confirm`)
export const apiAlertClose = (id) => request.post(`/alerts/${id}/close`)
export const apiAlertLatest = (params) => request.get('/alerts/latest', { params })
export const apiAlertGeoJson = () => request.get('/alerts/geojson')

export const apiAlertRuleList = () => request.get('/alert-rules')
export const apiAlertRuleCreate = (data) => request.post('/alert-rules', data)
export const apiAlertRuleUpdate = (id, data) => request.put(`/alert-rules/${id}`, data)
export const apiAlertRuleDelete = (id) => request.delete(`/alert-rules/${id}`)
