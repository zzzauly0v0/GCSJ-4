import request from '@/utils/request'

export const apiDictionaryByType = (type) => request.get('/dictionaries', { params: { type } })
