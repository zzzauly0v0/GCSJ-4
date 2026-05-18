import request from '@/utils/request'

export const apiObservationIngest = (data) => request.post('/observations', data)
export const apiObservationPage = (params) => request.get('/observations', { params })
export const apiObservationSeries = (params) => request.get('/observations/series', { params })
export const apiObservationLatest = (params) => request.get('/observations/latest', { params })
