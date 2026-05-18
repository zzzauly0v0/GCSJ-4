import request from '@/utils/request'

export const apiPermissionTree = () => request.get('/permissions/tree')
