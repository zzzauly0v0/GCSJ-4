import request from '@/utils/request'

export const apiRoleList = () => request.get('/roles')
export const apiRoleCreate = (data) => request.post('/roles', data)
export const apiRoleUpdate = (id, data) => request.put(`/roles/${id}`, data)
export const apiRoleDelete = (id) => request.delete(`/roles/${id}`)
