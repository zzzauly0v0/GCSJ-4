import request from '@/utils/request'

export const apiPlanList = () => request.get('/plans')
export const apiPlanGet = (id) => request.get(`/plans/${id}`)
export const apiPlanCreate = (data) => request.post('/plans', data)
export const apiPlanUpdate = (id, data) => request.put(`/plans/${id}`, data)
export const apiPlanDelete = (id) => request.delete(`/plans/${id}`)
export const apiPlanApplicable = (params) => request.get('/plans/applicable', { params })
