import request from '@/utils/request'

export const apiDictionaryByType = (type) => request.get('/dictionaries', { params: { type } })
export const apiDictionaryListAll = () => request.get('/dictionaries')
export const apiDictionaryTypes = () => request.get('/dictionaries/types')
export const apiDictionaryAdd = (data) => request.post('/dictionaries', data)
export const apiDictionaryUpdate = (id, data) => request.put(`/dictionaries/${id}`, data)
export const apiDictionaryDelete = (id) => request.delete(`/dictionaries/${id}`)
