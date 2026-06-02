<template>
  <!--
    AuroraLayout — 学术云蓝全站布局
    白底 / 渐变顶栏 / 大字导航 / 柔和阴影 / 无扫描线、无暗色背景。
  -->
  <div class="au-shell">

    <!-- ================= 顶栏 ================= -->
    <header class="au-topbar">
      <div class="au-topbar-left">
        <div class="au-brand">
          <div class="au-brand-mark">
            <svg viewBox="0 0 36 36" width="32" height="32" aria-hidden="true">
              <defs>
                <linearGradient id="auBrandGrad" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0%"  stop-color="#6366F1" />
                  <stop offset="55%" stop-color="#2563EB" />
                  <stop offset="100%" stop-color="#06B6D4" />
                </linearGradient>
              </defs>
              <path
                d="M6 27 L13 14 L20 22 L26 9 L32 27 Z"
                fill="none"
                stroke="url(#auBrandGrad)"
                stroke-width="2.4"
                stroke-linejoin="round"
                stroke-linecap="round"
              />
              <circle cx="13" cy="14" r="2.2" fill="url(#auBrandGrad)" />
              <circle cx="20" cy="22" r="2.2" fill="url(#auBrandGrad)" />
              <circle cx="26" cy="9"  r="2.2" fill="url(#auBrandGrad)" />
            </svg>
          </div>
          <div class="au-brand-text">
            <span class="au-brand-name au-grad-text">GCSJ</span>
            <span class="au-brand-sub">气象地质灾害监测预警平台</span>
          </div>
        </div>
      </div>

      <div class="au-topbar-center">
        <div class="au-metric" v-for="m in headerMetrics" :key="m.key">
          <span class="au-metric-label">{{ m.label }}</span>
          <span class="au-metric-value" :style="{ color: m.color }">{{ m.value }}</span>
          <span class="au-metric-unit">{{ m.unit }}</span>
        </div>
      </div>

      <div class="au-topbar-right">
        <div class="au-clock">
          <span class="au-clock-time">{{ utcTime }}</span>
          <span class="au-clock-date">{{ utcDate }} · UTC+8</span>
        </div>

        <button class="au-icon-btn" @click="goAlerts" :data-badge="alertStore.unreadCount > 0 ? (alertStore.unreadCount > 9 ? '9+' : alertStore.unreadCount) : ''">
          <el-icon :size="18"><BellFilled /></el-icon>
        </button>

        <el-dropdown @command="handleCommand" trigger="click" popper-class="au-dropdown-popper">
          <div class="au-user">
            <div class="au-user-avatar">{{ shortName }}</div>
            <div class="au-user-info">
              <span class="au-user-name">{{ userName }}</span>
              <span class="au-user-role">{{ userRole }}</span>
            </div>
            <el-icon class="au-user-caret"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile" :icon="User">个人资料</el-dropdown-item>
              <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- ================= 主体 ================= -->
    <div class="au-body">

      <!-- 侧边导航 -->
      <aside class="au-sidenav" :class="{ collapsed: appStore.sidebarCollapsed }">
        <button class="au-collapse" @click="appStore.toggleSidebar">
          <el-icon :size="16"><Fold v-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
        </button>

        <nav class="au-nav">
          <template v-for="(item) in menuItems" :key="item.path">
            <div v-if="item.children?.length" class="au-nav-group">
              <div class="au-nav-group-label" v-show="!appStore.sidebarCollapsed">{{ item.title }}</div>
              <router-link
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                class="au-nav-item"
                :class="{ active: route.path === child.path || route.path.startsWith(child.path + '/') }"
              >
                <span class="au-nav-bar" />
                <el-icon class="au-nav-icon"><component :is="child.icon" /></el-icon>
                <span class="au-nav-label" v-show="!appStore.sidebarCollapsed">{{ child.title }}</span>
                <span
                  v-if="child.path === '/alerts' && alertStore.unreadCount > 0"
                  class="au-nav-badge"
                >{{ alertStore.unreadCount > 9 ? '9+' : alertStore.unreadCount }}</span>
              </router-link>
            </div>
            <router-link
              v-else
              :to="item.path"
              class="au-nav-item"
              :class="{ active: route.path === item.path || route.path.startsWith(item.path + '/') }"
            >
              <span class="au-nav-bar" />
              <el-icon class="au-nav-icon"><component :is="item.icon" /></el-icon>
              <span class="au-nav-label" v-show="!appStore.sidebarCollapsed">{{ item.title }}</span>
            </router-link>
          </template>
        </nav>

        <div class="au-nav-foot" v-show="!appStore.sidebarCollapsed">
          <div class="au-foot-row">
            <span class="au-foot-dot" />
            <span>实时连接</span>
          </div>
          <div class="au-foot-time">{{ localTime }}</div>
          <div class="au-foot-mode">{{ envLabel }} · v2.0</div>
        </div>
      </aside>

      <!-- 主内容 -->
      <main class="au-main">
        <div class="au-breadcrumb">
          <div class="au-bc-trail">
            <span class="au-bc-home">主页</span>
            <span class="au-bc-sep">/</span>
            <span class="au-bc-current">{{ route.meta.title || '页面' }}</span>
          </div>
          <div class="au-bc-coord">{{ coordLabel }}</div>
        </div>

        <div class="au-page">
          <router-view v-slot="{ Component }">
            <component :is="Component" />
          </router-view>
        </div>
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

// 时钟
const utcTime = ref(dayjs().format('HH:mm:ss'))
const utcDate = ref(dayjs().format('YYYY-MM-DD'))
const localTime = ref(dayjs().format('MM-DD HH:mm:ss'))
let clockTimer = null
onMounted(() => {
  clockTimer = setInterval(() => {
    utcTime.value = dayjs().format('HH:mm:ss')
    utcDate.value = dayjs().format('YYYY-MM-DD')
    localTime.value = dayjs().format('MM-DD HH:mm:ss')
  }, 1000)
})
onBeforeUnmount(() => { if (clockTimer) clearInterval(clockTimer) })

// 顶栏指标
const headerMetrics = computed(() => {
  const total = alertStore.latest.length
  const red = alertStore.latest.filter(a => a.level === 4).length
  return [
    { key: 'alerts', label: '活跃预警', value: String(total).padStart(2, '0'), unit: '条', color: '#2563EB' },
    { key: 'red',    label: '红色预警', value: String(red).padStart(2, '0'),    unit: '条', color: red > 0 ? '#DC2626' : '#94A3B8' },
    { key: 'flow',   label: '数据流',   value: '24.7', unit: 'KB/s', color: '#06B6D4' },
  ]
})

// 用户
const userName = computed(() => userStore.profile?.realName || userStore.profile?.username || '游客')
const userRole = computed(() => userStore.roleCodes?.[0] || 'GUEST')
const shortName = computed(() => (userStore.profile?.realName || userStore.profile?.username || 'U').slice(0, 1).toUpperCase())
const envLabel = import.meta.env.MODE === 'production' ? 'PROD' : 'DEV'

const coordLabel = computed(() => '104.0000°E · 35.0000°N · EPSG:3857')

// 菜单
function canAccess(meta) {
  const code = meta?.permission
  if (!code) return true
  if (userStore.roleCodes?.includes('ROLE_ADMIN')) return true
  return userStore.permissions?.includes(code) ?? false
}
function joinPath(parent, child) {
  if (child.startsWith('/')) return child
  return `${parent.replace(/\/$/, '')}/${child}`
}
function buildMenu(routes, parentPath = '') {
  const items = []
  for (const r of routes || []) {
    if (!r.meta || r.meta.hideInMenu) continue
    if (!canAccess(r.meta)) continue
    const fullPath = joinPath(parentPath, r.path)
    const children = buildMenu(r.children, fullPath)
    if (r.children?.length && children.length === 0) continue
    items.push({ path: fullPath, title: r.meta.title, icon: r.meta.icon, children: children.length ? children : undefined })
  }
  return items
}
const menuItems = computed(() => {
  const root = router.options.routes.find(r => r.path === '/')
  return buildMenu(root?.children, '/')
})

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

<style scoped>
/* ============ ROOT ============ */
.au-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--au-bg-page);
  color: var(--au-text-primary);
  font-family: var(--au-font-sans);
}

/* ============ TOPBAR ============ */
.au-topbar {
  height: 68px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 0 24px;
  background:
    linear-gradient(135deg, rgba(238,242,255,0.95) 0%, rgba(219,234,254,0.92) 50%, rgba(224,242,254,0.95) 100%),
    var(--au-bg-surface);
  border-bottom: 1px solid #E0E7FF;
  position: relative;
  z-index: 50;
  backdrop-filter: saturate(140%);
}
.au-topbar::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: -1px;
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, #818CF8 30%, #38BDF8 70%, transparent 100%);
  opacity: 0.5;
}

.au-topbar-left, .au-topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}
.au-topbar-center {
  display: flex;
  align-items: center;
  gap: 18px;
  flex: 1;
  justify-content: center;
}

.au-brand { display: flex; align-items: center; gap: 12px; }
.au-brand-mark {
  width: 44px; height: 44px;
  background: var(--au-bg-surface);
  border-radius: var(--au-radius-md);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 14px rgba(99,102,241,0.18), 0 0 0 1px #E0E7FF;
}
.au-brand-text {
  display: flex; flex-direction: column; gap: 2px;
}
.au-brand-name {
  font-family: var(--au-font-display);
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 0.04em;
  line-height: 1;
}
.au-brand-sub {
  font-size: 12px;
  color: var(--au-text-secondary);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.au-metric {
  display: flex; align-items: baseline; gap: 6px;
  padding: 8px 14px;
  background: rgba(255,255,255,0.6);
  border: 1px solid rgba(199,210,254,0.6);
  border-radius: 999px;
  backdrop-filter: blur(6px);
}
.au-metric-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--au-text-secondary);
}
.au-metric-value {
  font-family: var(--au-font-num);
  font-size: 18px;
  font-weight: 700;
  font-feature-settings: var(--au-font-feat);
  letter-spacing: -0.01em;
}
.au-metric-unit {
  font-size: 11px;
  color: var(--au-text-tertiary);
}

.au-clock {
  display: flex; flex-direction: column; align-items: flex-end;
  line-height: 1.1;
  margin-right: 4px;
}
.au-clock-time {
  font-family: var(--au-font-num);
  font-size: 18px;
  font-weight: 700;
  background: var(--au-grad-primary);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-feature-settings: var(--au-font-feat);
  letter-spacing: 0.02em;
}
.au-clock-date {
  font-size: 11px;
  color: var(--au-text-tertiary);
  font-weight: 500;
}

.au-icon-btn {
  position: relative;
  width: 40px; height: 40px;
  border: none;
  border-radius: var(--au-radius-md);
  background: var(--au-bg-surface);
  color: var(--au-text-secondary);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  box-shadow: var(--au-shadow-xs), 0 0 0 1px var(--au-border-subtle);
  transition: all var(--au-dur-base) var(--au-ease);
}
.au-icon-btn:hover {
  color: var(--au-info);
  box-shadow: var(--au-shadow-sm), 0 0 0 1px #C7D2FE;
  transform: translateY(-1px);
}
.au-icon-btn[data-badge]:not([data-badge=""])::after {
  content: attr(data-badge);
  position: absolute;
  top: -4px; right: -4px;
  min-width: 18px; height: 18px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: 700;
  color: #FFFFFF;
  background: linear-gradient(135deg, #F87171, #DC2626);
  border-radius: 999px;
  border: 2px solid var(--au-bg-surface);
  display: flex; align-items: center; justify-content: center;
  font-family: var(--au-font-num);
}

.au-user {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 14px 6px 6px;
  background: var(--au-bg-surface);
  border-radius: 999px;
  cursor: pointer;
  box-shadow: var(--au-shadow-xs), 0 0 0 1px var(--au-border-subtle);
  transition: all var(--au-dur-base) var(--au-ease);
}
.au-user:hover {
  box-shadow: var(--au-shadow-sm), 0 0 0 1px #C7D2FE;
  transform: translateY(-1px);
}
.au-user-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  background: var(--au-grad-primary);
  color: #FFFFFF;
  font-family: var(--au-font-display);
  font-size: 14px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 10px rgba(37,99,235,0.30);
}
.au-user-info { display: flex; flex-direction: column; line-height: 1.2; }
.au-user-name { font-size: 13px; font-weight: 600; color: var(--au-text-strong); }
.au-user-role {
  font-size: 10px;
  color: var(--au-text-tertiary);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-weight: 600;
}
.au-user-caret { color: var(--au-text-tertiary); }

/* ============ BODY ============ */
.au-body {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* ============ SIDENAV ============ */
.au-sidenav {
  width: 232px;
  flex-shrink: 0;
  background: var(--au-bg-surface);
  border-right: 1px solid var(--au-border-subtle);
  display: flex; flex-direction: column;
  position: relative;
  transition: width var(--au-dur-base) var(--au-ease);
  overflow: hidden;
}
.au-sidenav.collapsed { width: 70px; }

.au-collapse {
  position: absolute;
  top: 12px; right: 12px;
  width: 28px; height: 28px;
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-sm);
  background: var(--au-bg-surface);
  color: var(--au-text-secondary);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  z-index: 2;
  transition: all var(--au-dur-fast) var(--au-ease);
}
.au-collapse:hover { color: var(--au-info); border-color: #C7D2FE; }

.au-nav {
  margin-top: 50px;
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px 16px;
  display: flex; flex-direction: column; gap: 4px;
}
.au-nav-group {
  display: flex; flex-direction: column; gap: 2px;
  margin-top: 14px;
}
.au-nav-group:first-child { margin-top: 0; }
.au-nav-group-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--au-text-tertiary);
  letter-spacing: 0.10em;
  text-transform: uppercase;
  padding: 4px 12px 6px;
}

.au-nav-item {
  position: relative;
  display: flex; align-items: center; gap: 12px;
  padding: 10px 12px;
  border-radius: var(--au-radius-md);
  color: var(--au-text-primary);
  font-size: 14px;
  font-weight: 500;
  text-decoration: none;
  transition: all var(--au-dur-fast) var(--au-ease);
  cursor: pointer;
}
.au-nav-bar {
  position: absolute;
  left: 0;
  top: 8px; bottom: 8px;
  width: 3px;
  border-radius: 0 4px 4px 0;
  background: transparent;
  transition: all var(--au-dur-base) var(--au-ease);
}
.au-nav-item:hover {
  background: var(--au-bg-hover);
  color: var(--au-info);
}
.au-nav-item.active {
  background:
    linear-gradient(90deg, rgba(99,102,241,0.10), rgba(6,182,212,0.04));
  color: var(--au-info);
  font-weight: 600;
}
.au-nav-item.active .au-nav-bar {
  background: var(--au-grad-primary);
}
.au-nav-icon {
  flex-shrink: 0;
  font-size: 18px;
  width: 24px;
  display: flex; align-items: center; justify-content: center;
}
.au-nav-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.au-nav-badge {
  min-width: 18px; height: 18px;
  padding: 0 5px;
  font-size: 10px;
  font-weight: 700;
  color: #FFFFFF;
  background: linear-gradient(135deg, #F87171, #DC2626);
  border-radius: 999px;
  display: flex; align-items: center; justify-content: center;
  font-family: var(--au-font-num);
}

.au-nav-foot {
  margin: 0 12px 14px;
  padding: 12px;
  background: var(--au-bg-subtle);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-md);
  font-size: 12px;
  color: var(--au-text-secondary);
  display: flex; flex-direction: column; gap: 4px;
}
.au-foot-row { display: flex; align-items: center; gap: 6px; }
.au-foot-dot {
  width: 8px; height: 8px; border-radius: 999px;
  background: #10B981;
  box-shadow: 0 0 0 3px rgba(16,185,129,0.20);
}
.au-foot-time {
  font-family: var(--au-font-num);
  font-size: 13px;
  color: var(--au-text-strong);
  font-weight: 600;
  font-feature-settings: var(--au-font-feat);
}
.au-foot-mode {
  font-size: 11px;
  color: var(--au-text-tertiary);
  letter-spacing: 0.06em;
  font-weight: 600;
}

/* ============ MAIN ============ */
.au-main {
  flex: 1;
  display: flex; flex-direction: column;
  min-width: 0;
  background: var(--au-bg-page);
  overflow: hidden;
}
.au-breadcrumb {
  flex-shrink: 0;
  height: 44px;
  padding: 0 24px;
  display: flex; align-items: center; justify-content: space-between;
  background: var(--au-bg-surface);
  border-bottom: 1px solid var(--au-border-subtle);
  font-size: 13px;
}
.au-bc-trail { display: flex; align-items: center; gap: 8px; color: var(--au-text-secondary); }
.au-bc-home { color: var(--au-text-tertiary); }
.au-bc-sep { color: var(--au-text-tertiary); }
.au-bc-current { color: var(--au-text-strong); font-weight: 600; }
.au-bc-coord {
  font-family: var(--au-font-num);
  font-size: 12px;
  color: var(--au-text-tertiary);
  font-feature-settings: var(--au-font-feat);
}

.au-page {
  flex: 1;
  overflow: auto;
  min-height: 0;
}
</style>
