import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

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
        meta: { title: '监测大屏', icon: 'DataLine' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/MonitorView.vue'),
        meta: { title: '实时监测', icon: 'TrendCharts' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('@/views/alert/AlertView.vue'),
        meta: { title: '预警管理', icon: 'BellFilled' }
      },
      {
        path: 'disasters',
        name: 'Disasters',
        component: () => import('@/views/disaster/DisasterView.vue'),
        meta: { title: '灾害事件', icon: 'Warning' }
      },
      {
        path: 'sensors',
        name: 'Sensors',
        component: () => import('@/views/sensor/SensorView.vue'),
        meta: { title: '传感器', icon: 'Cpu' }
      },
      {
        path: 'system/users',
        name: 'SysUsers',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'system/roles',
        name: 'SysRoles',
        component: () => import('@/views/system/RoleView.vue'),
        meta: { title: '角色权限', icon: 'UserFilled' }
      },
      // 以下页面保留, 但不在主菜单中显示 (留给未来扩展)
      {
        path: 'alert-rules',
        name: 'AlertRules',
        component: () => import('@/views/alert/AlertRuleView.vue'),
        meta: { title: '预警规则', icon: 'Setting', hideInMenu: true }
      },
      {
        path: 'layers',
        name: 'Layers',
        component: () => import('@/views/layer/LayerView.vue'),
        meta: { title: '空间图层', icon: 'MapLocation', hideInMenu: true }
      },
      {
        path: 'plans',
        name: 'Plans',
        component: () => import('@/views/plan/PlanView.vue'),
        meta: { title: '应急预案', icon: 'Document', hideInMenu: true }
      },
      {
        path: 'system/permissions',
        name: 'SysPermissions',
        component: () => import('@/views/system/PermissionView.vue'),
        meta: { title: '权限树', icon: 'Lock', hideInMenu: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', component: () => import('@/views/error/NotFoundView.vue'), meta: { requiresAuth: false } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

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
  }
  if (to.meta.title) {
    document.title = `${to.meta.title} | ${import.meta.env.VITE_APP_TITLE || 'GCSJ'}`
  }
  next()
})

export default router
