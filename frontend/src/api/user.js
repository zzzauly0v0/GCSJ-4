import request from '@/utils/request'

export const apiUserPage = (params) => request.get('/users', { params })
export const apiUserGet = (id) => request.get(`/users/${id}`)
export const apiUserCreate = (data) => request.post('/users', data)
export const apiUserUpdate = (id, data) => request.put(`/users/${id}`, data)
export const apiUserDelete = (id) => request.delete(`/users/${id}`)
