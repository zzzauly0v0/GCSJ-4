import request from '@/utils/request'

export const apiOrgTree = () => request.get('/orgs/tree')
