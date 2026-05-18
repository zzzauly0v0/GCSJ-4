<template>
  <div class="layout">
    <!-- 侧边栏 -->
    <aside class="aside" :class="{ collapsed: appStore.sidebarCollapsed }">
      <div class="logo">
        <div class="logo-mark">G</div>
        <div v-show="!appStore.sidebarCollapsed" class="logo-text">
          <div class="t1">GCSJ 灾害预警</div>
          <div class="t2">DISASTER MONITORING</div>
        </div>
      </div>

      <el-menu
        :default-active="route.path"
        :default-openeds="['/system']"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="transparent"
        text-color="#475569"
        active-text-color="#0EA5E9"
        class="menu"
      >
        <template v-for="item in menuItems" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon><component :is="child.icon" /></el-icon>
              <template #title>{{ child.title }}</template>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>

      <div class="aside-footer" v-show="!appStore.sidebarCollapsed">
        <div class="info-line"><span class="led" />实时连接</div>
        <div class="info-time">{{ now }}</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb class="crumb" separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title || '' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <div class="env-tag">
            <span class="led" />
            <span>{{ envLabel }}</span>
          </div>
          <el-tooltip content="预警通知" placement="bottom">
            <el-badge :value="alertStore.unreadCount" :max="99" :hidden="alertStore.unreadCount === 0" class="badge">
              <el-button text @click="goAlerts" class="icon-btn">
                <el-icon><BellFilled /></el-icon>
              </el-button>
            </el-badge>
          </el-tooltip>
          <el-dropdown @command="handleCommand">
            <span class="user">
              <el-avatar :size="30" class="avatar">{{ shortName }}</el-avatar>
              <div class="user-info">
                <div class="name">{{ userStore.profile?.realName || userStore.profile?.username }}</div>
                <div class="role">{{ userStore.roleCodes[0] || '游客' }}</div>
              </div>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="User">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <keep-alive include="DashboardView">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, User, SwitchButton, BellFilled, Fold, Expand } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useAppStore } from '@/store/app'
import { useUserStore } from '@/store/user'
import { useAlertStore } from '@/store/alert'
import { useAlertSocket } from '@/hooks/useAlertSocket'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const alertStore = useAlertStore()

useAlertSocket()

const menuItems = [
  { path: '/dashboard',     title: '监测大屏',  icon: 'DataLine' },
  { path: '/monitor',       title: '实时监测',  icon: 'TrendCharts' },
  { path: '/alerts',        title: '预警管理',  icon: 'BellFilled' },
  { path: '/disasters',     title: '灾害事件',  icon: 'Warning' },
  { path: '/sensors',       title: '传感器',    icon: 'Cpu' },
  {
    path: '/system',
    title: '系统管理',
    icon: 'Setting',
    children: [
      { path: '/system/users', title: '用户管理', icon: 'User' },
      { path: '/system/roles', title: '角色权限', icon: 'UserFilled' }
    ]
  }
]

const shortName = computed(() => {
  const n = userStore.profile?.realName || userStore.profile?.username || 'U'
  return n.slice(0, 1).toUpperCase()
})

const envLabel = import.meta.env.MODE === 'production' ? 'PROD' : 'DEV'

const now = ref(dayjs().format('MM-DD HH:mm:ss'))
let timer = null
onMounted(() => {
  timer = setInterval(() => { now.value = dayjs().format('MM-DD HH:mm:ss') }, 1000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

function goAlerts() {
  alertStore.markAllRead()
  router.push('/alerts')
}
async function handleCommand(cmd) {
  if (cmd === 'logout') {
    await userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/system/users')
  }
}
</script>

<style scoped lang="scss">
.layout {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}

/* ========== 侧边栏 ========== */
.aside {
  width: 220px;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
  border-right: 1px solid var(--border);
  &.collapsed { width: 64px; }
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-soft);
  .logo-mark {
    width: 32px; height: 32px;
    background: var(--primary);
    border-radius: 8px;
    display: flex; align-items: center; justify-content: center;
    color: #fff; font-weight: 700; font-size: 16px;
  }
  .logo-text {
    .t1 { color: var(--text-1); font-weight: 600; font-size: 14px; line-height: 1.2; }
    .t2 { color: var(--text-3); font-size: 9px; letter-spacing: 1px; margin-top: 2px; }
  }
}
.menu {
  flex: 1;
  border-right: none !important;
  padding: 8px 0;
  overflow-y: auto;
}
.menu::-webkit-scrollbar { width: 6px; }
.menu::-webkit-scrollbar-thumb { background: #CBD5E1; border-radius: 3px; }

:deep(.el-menu) {
  border-right: none !important;
}
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  margin: 2px 8px;
  border-radius: 8px;
  font-size: 13px;
}
:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--bg-soft) !important;
  color: var(--text-1) !important;
}
:deep(.el-menu-item.is-active) {
  background: var(--primary-soft) !important;
  color: var(--primary-deep) !important;
  font-weight: 600;
}
:deep(.el-sub-menu .el-menu-item) {
  padding-left: 44px !important;
  background: transparent !important;
}

.aside-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-soft);
  font-size: 11px;
  color: var(--text-3);
  .info-line {
    display: flex; align-items: center; gap: 6px;
    color: #059669;
    margin-bottom: 4px;
    .led {
      width: 6px; height: 6px; border-radius: 50%;
      background: #10B981;
    }
  }
  .info-time { font-family: 'SF Mono', monospace; color: var(--text-2); }
}

/* ========== 主区域 ========== */
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg);
}
.header {
  height: 56px;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 20px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
  .header-left { display: flex; align-items: center; gap: 16px;
    .collapse-btn { font-size: 18px; cursor: pointer; color: var(--text-2);
      &:hover { color: var(--primary); }
    }
    .crumb :deep(.el-breadcrumb__inner) { font-size: 13px; }
  }
  .header-right { display: flex; align-items: center; gap: 16px;
    .env-tag {
      display: flex; align-items: center; gap: 6px;
      padding: 4px 10px;
      background: var(--accent-soft);
      border-radius: 999px;
      font-size: 11px;
      color: #047857;
      font-weight: 600;
      letter-spacing: 1px;
      .led {
        width: 6px; height: 6px; border-radius: 50%;
        background: #10B981;
      }
    }
    .icon-btn { font-size: 18px; color: var(--text-2);
      &:hover { color: var(--primary); }
    }
    .user { display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 8px; border-radius: 8px;
      transition: background 0.2s;
      &:hover { background: var(--bg); }
      .avatar { background: var(--primary); color: #fff; }
      .user-info {
        line-height: 1.2;
        .name { font-size: 13px; color: var(--text-1); font-weight: 600; }
        .role { font-size: 11px; color: var(--text-3); }
      }
      .caret { font-size: 12px; color: var(--text-3); }
    }
  }
}
.content {
  flex: 1;
  overflow: auto;
  background: var(--bg);
}
</style>
