import request from '@/utils/request'

export const apiIngestWeather = () => request.post('/ingest/weather')
