import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { compose } from 'ol/transform'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '监测大屏', icon: 'DataLine', permission: 'dashboard:view' }
      },
      {
        path: 'agent',
        name: 'Agent',
        component: () => import('@/views/agent/AgentView.vue'),
        meta: { title: 'AI灾害助手', icon: 'ChatDotRound', permission: 'dashboard:view' }
      },
      {
        path: 'disasters',
        name: 'Disasters',
        component: () => import('@/views/disaster/DisasterView.vue'),
        meta: { title: '灾害事件', icon: 'Warning', permission: 'disaster:view' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/alert/AlertView.vue'),
        meta: { title: '预警管理', icon: 'BellFilled', permission: 'alert:view' }
      },
      {
        path: 'layers',
        name: 'Layers',
        component: () => import('@/views/layer/LayerView.vue'),
        meta: { title: '空间图层', icon: 'MapLocation', permission: 'layer:view' }
      },
      {
        path: 'plans',
        name: 'Plans',
        component: () => import('@/views/plan/PlanView.vue'),
        meta: { title: '应急预案', icon: 'Document', permission: 'plan:view', hideInMenu: true }
      },
      {
        path: 'system',
        meta: { title: '系统管理', icon: 'Setting', permission: 'system:view' },
        children: [
          {
            path: 'users',
            name: 'SysUsers',
            component: () => import('@/views/system/UserView.vue'),
            meta: { title: '用户管理', icon: 'User', permission: 'user:view' }
          },
          {
            path: 'roles',
            name: 'SysRoles',
            component: () => import('@/views/system/RoleView.vue'),
            meta: { title: '角色权限', icon: 'UserFilled', permission: 'role:view' }
          },
          {
            path: 'permissions',
            name: 'SysPermissions',
            component: () => import('@/views/system/PermissionView.vue'),
            meta: { title: '权限树', icon: 'Lock', permission: 'permission:view', hideInMenu: true }
          },
          {
            path: 'dictionaries',
            name: 'SysDictionaries',
            component: () => import('@/views/system/DictionaryView.vue'),
            meta: { title: '数据字典', icon: 'Collection', permission: 'dict:view' }
          },
          {
            path: 'logs',
            name: 'SysLogs',
            component: () => import('@/views/system/LogView.vue'),
            meta: { title: '操作日志', icon: 'List', permission: 'log:view' }
          }
        ]
      }
    ]
  },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/error/NotFoundView.vue'), meta: { requiresAuth: false } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function hasPermission(userStore, code) {
  if (!code) return true
  if (userStore.roleCodes?.includes('ROLE_ADMIN')) return true
  return userStore.permissions?.includes(code) ?? false
}

router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const requiresAuth = to.meta.requiresAuth !== false
  if (requiresAuth) {
    if (!userStore.token) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
    if (!userStore.profile) {
      try {
        await userStore.fetchProfile()
      } catch (_) {
        next({ path: '/login', query: { redirect: to.fullPath } })
        return
      }
    }
    const need = to.matched.map(r => r.meta?.permission).filter(Boolean)
    const denied = need.find(code => !hasPermission(userStore, code))
    if (denied) {
      ElMessage.warning(`无权访问 (缺少权限: ${denied})`)
      if (from.name) {
        next(false)
      } else {
        next({ path: '/dashboard' })
      }
      return
    }
  }
  if (to.meta.title) {
    document.title = `${to.meta.title} | ${import.meta.env.VITE_APP_TITLE || 'GCSJ'}`
  }
  next()
})

export default router
