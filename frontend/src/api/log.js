import request from '@/utils/request'

export const apiLogPage = (params) => request.get('/logs', { params })
