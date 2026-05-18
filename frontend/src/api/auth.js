import request from '@/utils/request'

export const apiLogin = (data) => request.post('/auth/login', data)
export const apiProfile = () => request.get('/auth/profile')
export const apiLogout = () => request.post('/auth/logout')
