<template>
  <!--
    CommandConsoleLayout — Primary HUD shell
    Boot sequence:
      0ms:     topbar slides in from top (topbar-slide-in)
      300ms:   nav sidebar lamps light up staggered (nav-lamp-on × N)
      700ms:   main content area scanline sweeps across
      1200ms+: live tickers, LED blinks, status indicators activate
  -->
  <div class="console-root console-bg">

    <!-- ============================================================
         TOP BAR — HUD header strip
         Enters: topbar-slide-in 400ms at t=0
    ============================================================ -->
    <header class="topbar" :class="{ booted: isBooted }">
      <!-- System identity -->
      <div class="topbar-identity">
        <div class="sys-sigil">
          <div class="sigil-ring sigil-outer" />
          <div class="sigil-ring sigil-inner" />
          <div class="sigil-core" />
        </div>
        <div class="sys-label">
          <span class="sys-name h-display-lg">GCSJ</span>
          <span class="sys-subtitle">气象地质灾害监测预警系统</span>
        </div>
        <div class="sys-version">
          <span class="version-tag data-value">v2.0.0-STORM</span>
        </div>
      </div>

      <!-- Center: live system metrics -->
      <div class="topbar-metrics">
        <div class="metric-strip" v-for="m in headerMetrics" :key="m.key">
          <span class="metric-strip-label">{{ m.label }}</span>
          <span class="metric-strip-value data-value" :style="{ color: m.color }">{{ m.value }}</span>
          <span class="metric-strip-unit">{{ m.unit }}</span>
          <span class="led" :class="m.ledClass" />
        </div>
      </div>

      <!-- Right: status cluster + user -->
      <div class="topbar-right">
        <!-- UTC Clock -->
        <div class="utc-clock">
          <span class="clock-label">UTC+8</span>
          <span class="clock-time data-value">{{ utcTime }}</span>
          <span class="clock-date data-value">{{ utcDate }}</span>
        </div>

        <!-- Status LEDs array -->
        <div class="status-leds">
          <div class="status-led-item" v-for="s in systemStatus" :key="s.key">
            <span class="led" :class="s.ledClass" />
            <span class="status-led-label">{{ s.label }}</span>
          </div>
        </div>

        <!-- Alert badge -->
        <el-badge :value="alertStore.unreadCount" :max="99" :hidden="alertStore.unreadCount === 0" class="alert-badge">
          <button class="hud-btn" @click="goAlerts">
            <el-icon><BellFilled /></el-icon>
          </button>
        </el-badge>

        <!-- User dropdown -->
        <el-dropdown @command="handleCommand" trigger="click">
          <div class="user-capsule">
            <div class="user-avatar">{{ shortName }}</div>
            <div class="user-meta">
              <span class="user-name">{{ userName }}</span>
              <span class="user-role data-value">{{ userRole }}</span>
            </div>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
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

    <!-- ============================================================
         BODY — Sidebar + Main content
    ============================================================ -->
    <div class="console-body">

      <!-- =====================================================
           SIDEBAR NAV — Industrial navigation column
           Each item lights up with nav-lamp-on stagger
      ===================================================== -->
      <aside class="sidenav" :class="{ collapsed: appStore.sidebarCollapsed }">

        <!-- Collapse toggle -->
        <button class="collapse-toggle" @click="appStore.toggleSidebar">
          <el-icon><Fold v-if="!appStore.sidebarCollapsed" /><Expand v-else /></el-icon>
        </button>

        <!-- Navigation items -->
        <nav class="nav-items">
          <template v-for="(item, idx) in menuItems" :key="item.path">
            <!-- Group with children -->
            <div v-if="item.children?.length" class="nav-group">
              <div class="nav-group-label" v-show="!appStore.sidebarCollapsed">
                {{ item.title }}
              </div>
              <router-link
                v-for="(child, cidx) in item.children"
                :key="child.path"
                :to="child.path"
                class="nav-item reveal reveal-left"
                :class="{ active: route.path === child.path || route.path.startsWith(child.path + '/') }"
                :style="{ animationDelay: (300 + (idx * 3 + cidx) * 55) + 'ms' }"
              >
                <div class="nav-item-indicator" />
                <el-icon class="nav-icon"><component :is="child.icon" /></el-icon>
                <span class="nav-label" v-show="!appStore.sidebarCollapsed">{{ child.title }}</span>
                <div v-if="child.path === '/alerts' && alertStore.unreadCount > 0" class="nav-badge">
                  {{ alertStore.unreadCount > 9 ? '9+' : alertStore.unreadCount }}
                </div>
              </router-link>
            </div>

            <!-- Flat item -->
            <router-link
              v-else
              :to="item.path"
              class="nav-item reveal reveal-left"
              :class="{ active: route.path === item.path || route.path.startsWith(item.path + '/') }"
              :style="{ animationDelay: (300 + idx * 55) + 'ms' }"
            >
              <div class="nav-item-indicator" />
              <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
              <span class="nav-label" v-show="!appStore.sidebarCollapsed">{{ item.title }}</span>
            </router-link>
          </template>
        </nav>

        <!-- Sidebar footer: system health -->
        <div class="nav-footer" v-show="!appStore.sidebarCollapsed">
          <div class="sys-health-row">
            <span class="led led-online" />
            <span class="health-label">实时连接</span>
          </div>
          <div class="sys-time data-value">{{ localTime }}</div>
          <div class="sys-mode data-value">{{ envLabel }} MODE</div>
        </div>
      </aside>

      <!-- =====================================================
           MAIN CONTENT AREA
           scanline sweep activates at t=700ms
      ===================================================== -->
      <main class="content-area live-scanline" :class="{ booted: isBooted }">
        <!-- Breadcrumb strip -->
        <div class="breadcrumb-strip reveal reveal-delay-3">
          <div class="bc-path">
            <span class="bc-root data-value">ROOT</span>
            <span class="bc-sep">/</span>
            <span class="bc-current">{{ route.meta.title || '—' }}</span>
          </div>
          <div class="bc-right data-value">{{ coordLabel }}</div>
        </div>

        <!-- Page content slot -->
        <div class="page-container">
          <router-view v-slot="{ Component }">
            <keep-alive include="DashboardView">
              <component :is="Component" />
            </keep-alive>
          </router-view>
        </div>
      </main>
    </div>

    <!-- Global boot overlay — fades out once booted -->
    <transition name="boot-fade">
      <div v-if="!isBooted" class="boot-overlay">
        <div class="boot-sequence">
          <div class="boot-sigil">
            <div class="boot-ring boot-ring-1" />
            <div class="boot-ring boot-ring-2" />
            <div class="boot-ring boot-ring-3" />
            <div class="boot-core-text data-value">GCSJ</div>
          </div>
          <div class="boot-lines">
            <div v-for="(line, i) in bootLines" :key="i" class="boot-line data-value"
                 :style="{ animationDelay: (i * 120) + 'ms' }">
              {{ line }}
            </div>
          </div>
        </div>
      </div>
    </transition>
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

// ---- Boot sequence ----
const isBooted = ref(false)
const bootLines = [
  'SYS: INITIALIZING GCSJ STORMWATCH v2.0.0',
  'GIS: LOADING SPATIAL ENGINE ... OK',
  'WS:  ESTABLISHING REALTIME FEED ... OK',
  'DB:  CONNECTING POSTGRESQL+POSTGIS ... OK',
  'MAP: TILE CACHE WARM ... 1247 TILES',
  'ALERT: LOADING RULE ENGINE ... 14 RULES ACTIVE',
  'SYS: ALL SUBSYSTEMS NOMINAL — READY',
]
onMounted(() => {
  // Boot overlay shows for ~1600ms then fades
  const bootTimer = setTimeout(() => { isBooted.value = true }, 1600)
  return () => clearTimeout(bootTimer)
})

// ---- Clocks ----
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

// ---- Header metrics ----
const headerMetrics = computed(() => {
  const total = alertStore.latest.length
  const red = alertStore.latest.filter(a => a.level === 4).length
  return [
    { key: 'alerts', label: '活跃预警', value: String(total).padStart(3, '0'), unit: '条', color: 'var(--signal-crystal)', ledClass: total > 0 ? 'led-online' : 'led-offline' },
    { key: 'red',    label: '红色预警', value: String(red).padStart(2, '0'),    unit: '条', color: red > 0 ? 'var(--signal-alert)' : 'var(--text-tertiary)', ledClass: red > 0 ? 'led-alert' : 'led-offline' },
    { key: 'stream', label: '数据流',   value: '24.7',  unit: 'KB/s', color: 'var(--signal-crystal)', ledClass: 'led-online' },
  ]
})

// ---- Status LEDs ----
const systemStatus = [
  { key: 'db',    label: 'DB',   ledClass: 'led-online' },
  { key: 'ws',    label: 'WS',   ledClass: 'led-online' },
  { key: 'gis',   label: 'GIS',  ledClass: 'led-online' },
]

// ---- User info ----
const userName = computed(() => userStore.profile?.realName || userStore.profile?.username || 'USER')
const userRole = computed(() => userStore.roleCodes?.[0] || 'GUEST')
const shortName = computed(() => (userStore.profile?.realName || userStore.profile?.username || 'U').slice(0, 1).toUpperCase())

const envLabel = import.meta.env.MODE === 'production' ? 'PROD' : 'DEV'

// Coordinate display for breadcrumb
const coordLabel = computed(() => '104.0000°E / 35.0000°N · EPSG:3857')

// ---- Permission & menu building ----
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

// ---- Actions ----
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
/* ================================================================
   LAYOUT SHELL
================================================================ */
.console-root {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  position: relative;
}

/* ================================================================
   TOPBAR
   Boot: topbar-slide-in 400ms cubic at t=0
================================================================ */
.topbar {
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 12px;
  background: var(--bg-panel-deep);
  border-bottom: 1px solid var(--border-panel);
  flex-shrink: 0;
  position: relative;
  z-index: 100;
  animation: topbar-slide-in 380ms var(--ease-power-on) both;
  /* Glow underline */
  box-shadow:
    0 1px 0 var(--border-panel),
    0 2px 12px rgba(0, 229, 255, 0.08);
  will-change: transform;
}

/* System identity */
.topbar-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.sys-sigil {
  width: 34px;
  height: 34px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.sigil-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid var(--signal-crystal);
}
.sigil-outer {
  inset: 0;
  opacity: 0.35;
  animation: radar-rotate var(--dur-radar) linear infinite;
  animation-delay: 1600ms;
  border-style: dashed;
}
.sigil-inner {
  inset: 6px;
  opacity: 0.65;
  animation: radar-rotate 4000ms linear reverse infinite;
  animation-delay: 1600ms;
}
.sigil-core {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--signal-crystal);
  box-shadow: var(--glow-crystal);
  animation: led-pulse 2400ms ease-in-out infinite;
  animation-delay: 1600ms;
}

.sys-label {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.sys-name {
  font-family: var(--font-display);
  font-size: 16px;
  font-weight: 800;
  letter-spacing: 0.15em;
  color: var(--signal-crystal);
  text-shadow: var(--glow-crystal);
  line-height: 1;
}
.sys-subtitle {
  font-family: var(--font-ui);
  font-size: 9px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: var(--text-tertiary);
  text-transform: uppercase;
}
.version-tag {
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
  padding: 2px 6px;
  border: 1px solid var(--border-separator);
  border-radius: 1px;
}

/* Center metrics strip */
.topbar-metrics {
  display: flex;
  align-items: center;
  gap: 2px;
  animation: panel-enter 400ms var(--ease-power-on) both;
  animation-delay: 200ms;
}
.metric-strip {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  background: var(--bg-panel);
  border: 1px solid var(--border-separator);
  border-radius: 1px;
  margin: 0 2px;
}
.metric-strip-label {
  font-family: var(--font-ui);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.10em;
  text-transform: uppercase;
  color: var(--text-tertiary);
}
.metric-strip-value {
  font-family: var(--font-mono);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.04em;
  font-feature-settings: "tnum" 1;
  transition: color 300ms ease;
}
.metric-strip-unit {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
}

/* Right cluster */
.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  animation: panel-enter-right 400ms var(--ease-power-on) both;
  animation-delay: 150ms;
}

.utc-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0;
}
.clock-label {
  font-family: var(--font-ui);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  line-height: 1;
}
.clock-time {
  font-family: var(--font-mono);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-data-hot);
  letter-spacing: 0.06em;
  line-height: 1.1;
  font-feature-settings: "tnum" 1;
}
.clock-date {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
  line-height: 1;
}

.status-leds {
  display: flex;
  gap: 8px;
  padding: 4px 10px;
  background: var(--bg-panel);
  border: 1px solid var(--border-separator);
  border-radius: 1px;
}
.status-led-item {
  display: flex;
  align-items: center;
  gap: 4px;
}
.status-led-label {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.10em;
}

.hud-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-panel);
  border: 1px solid var(--border-panel);
  border-radius: 1px;
  color: var(--text-secondary);
  font-size: 16px;
  transition: all 150ms ease;
  cursor: pointer;
}
.hud-btn:hover {
  background: var(--signal-crystal-dim);
  color: var(--signal-crystal);
  border-color: var(--border-active);
  box-shadow: 0 0 8px rgba(0, 229, 255, 0.25);
}

.user-capsule {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px 4px 5px;
  background: var(--bg-panel);
  border: 1px solid var(--border-panel);
  border-radius: 1px;
  cursor: pointer;
  transition: all 150ms ease;
}
.user-capsule:hover {
  border-color: var(--border-active);
  background: var(--bg-panel-elevated);
}
.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 1px;
  background: var(--signal-crystal-dim);
  border: 1px solid var(--border-active);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 700;
  color: var(--signal-crystal);
}
.user-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.user-name {
  font-family: var(--font-ui);
  font-size: 11px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: 0.04em;
}
.user-role {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--signal-crystal);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.user-caret {
  font-size: 10px;
  color: var(--text-tertiary);
}

/* ================================================================
   BODY — Sidebar + Content
================================================================ */
.console-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  min-height: 0;
}

/* ================================================================
   SIDEBAR
================================================================ */
.sidenav {
  width: 200px;
  background: var(--bg-panel-deep);
  border-right: 1px solid var(--border-panel);
  display: flex;
  flex-direction: column;
  transition: width 180ms var(--ease-power-on);
  flex-shrink: 0;
  position: relative;
  z-index: 50;
}
.sidenav.collapsed {
  width: 48px;
}

.collapse-toggle {
  height: 36px;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-tertiary);
  font-size: 14px;
  border-bottom: 1px solid var(--border-separator);
  background: transparent;
  cursor: pointer;
  transition: all 150ms ease;
  border-radius: 0;
}
.collapse-toggle:hover {
  color: var(--signal-crystal);
  background: var(--signal-crystal-dim);
}

.nav-items {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0;
  scrollbar-width: thin;
}

.nav-group {
  margin-bottom: 2px;
}
.nav-group-label {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  padding: 8px 14px 3px;
  border-top: 1px solid var(--border-separator);
  margin-top: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 12px;
  height: 38px;
  color: var(--text-secondary);
  text-decoration: none;
  position: relative;
  transition: all 150ms ease;
  border-left: 2px solid transparent;
  overflow: hidden;
  cursor: pointer;
}
.nav-item:hover {
  background: var(--signal-crystal-dim);
  color: var(--signal-crystal);
  border-left-color: rgba(0, 229, 255, 0.35);
}
.nav-item.active {
  background: rgba(0, 229, 255, 0.10);
  color: var(--signal-crystal);
  border-left-color: var(--signal-crystal);
}
.nav-item.active .nav-item-indicator {
  opacity: 1;
  box-shadow: var(--glow-crystal);
}

.nav-item-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 18px;
  background: var(--signal-crystal);
  opacity: 0;
  transition: opacity 150ms ease;
  border-radius: 0 1px 1px 0;
}

.nav-icon {
  font-size: 15px;
  flex-shrink: 0;
  transition: color 150ms ease;
}

.nav-label {
  font-family: var(--font-ui);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: opacity 180ms ease;
}
.sidenav.collapsed .nav-label {
  opacity: 0;
}

.nav-badge {
  margin-left: auto;
  min-width: 18px;
  height: 16px;
  padding: 0 4px;
  background: var(--signal-alert);
  border-radius: 1px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: led-blink 800ms steps(1, end) infinite;
  flex-shrink: 0;
}

.nav-footer {
  padding: 10px 14px;
  border-top: 1px solid var(--border-separator);
  flex-shrink: 0;
}
.sys-health-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;
}
.health-label {
  font-family: var(--font-ui);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.10em;
  text-transform: uppercase;
  color: var(--signal-crystal);
}
.sys-time {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-secondary);
  letter-spacing: 0.04em;
  margin-bottom: 3px;
}
.sys-mode {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.12em;
}

/* ================================================================
   MAIN CONTENT AREA
================================================================ */
.content-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: transparent;
  position: relative;
}

.breadcrumb-strip {
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: var(--bg-panel-deep);
  border-bottom: 1px solid var(--border-separator);
  flex-shrink: 0;
}
.bc-path {
  display: flex;
  align-items: center;
  gap: 6px;
}
.bc-root {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-tertiary);
  letter-spacing: 0.10em;
}
.bc-sep {
  color: var(--border-panel);
  font-size: 12px;
}
.bc-current {
  font-family: var(--font-ui);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-secondary);
}
.bc-right {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
}

.page-container {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

/* ================================================================
   BOOT OVERLAY
================================================================ */
.boot-overlay {
  position: fixed;
  inset: 0;
  background: var(--bg-console);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.boot-sequence {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 32px;
}

.boot-sigil {
  position: relative;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.boot-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid var(--signal-crystal);
}
.boot-ring-1 {
  inset: 0;
  opacity: 0.3;
  animation: radar-rotate 3000ms linear infinite;
  border-style: dashed;
}
.boot-ring-2 {
  inset: 14px;
  opacity: 0.6;
  animation: radar-rotate 2000ms linear reverse infinite;
}
.boot-ring-3 {
  inset: 28px;
  opacity: 0.9;
  animation: radar-rotate 1200ms linear infinite;
}
.boot-core-text {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 800;
  color: var(--signal-crystal);
  letter-spacing: 0.20em;
  text-shadow: var(--glow-crystal);
  position: relative;
  z-index: 1;
}

.boot-lines {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 380px;
}
.boot-line {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--signal-crystal);
  letter-spacing: 0.06em;
  opacity: 0;
  animation: panel-enter 200ms var(--ease-power-on) forwards;
}

/* Boot fade-out transition */
.boot-fade-leave-active {
  transition: opacity 400ms ease 200ms;
}
.boot-fade-leave-to {
  opacity: 0;
}

/* Alert badge override */
.alert-badge :deep(.el-badge__content) {
  font-family: var(--font-mono);
  font-size: 9px;
  background: var(--signal-alert);
  animation: led-blink 800ms steps(1, end) infinite;
}
</style>
