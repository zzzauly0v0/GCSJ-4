<template>
  <div class="login-page">
    <!-- 背景动效 -->
    <div class="bg-grid" />
    <div class="bg-glow bg-glow-1" />
    <div class="bg-glow bg-glow-2" />
    <canvas ref="particleEl" class="bg-particles" />

    <!-- 顶部品牌 -->
    <div class="brand">
      <div class="brand-mark">GCSJ</div>
      <div class="brand-line" />
      <div class="brand-text">
        <div class="brand-title">气象地质灾害监测预警管理系统</div>
        <div class="brand-sub">METEOROLOGICAL & GEOLOGICAL DISASTER MONITORING SYSTEM</div>
      </div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card dash-panel">
      <span class="corner-bl" /><span class="corner-br" />

      <div class="card-head">
        <div class="head-title">
          <span class="dot" />
          欢迎登录
        </div>
        <div class="head-time">{{ now }}</div>
      </div>

      <div class="card-body">
        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="密码" :prefix-icon="Lock" />
          </el-form-item>
          <el-button type="primary" :loading="loading" class="submit" @click="onSubmit">
            <span v-if="!loading">登 录 系 统</span>
            <span v-else>验证中...</span>
          </el-button>
        </el-form>

        <div class="quick-tip">
          <span class="tip-label">默认账号</span>
          <span class="tip-item" @click="quickFill('admin')">admin</span>
          <span class="tip-item" @click="quickFill('operator')">operator</span>
          <span class="tip-item" @click="quickFill('viewer')">viewer</span>
          <span class="tip-pwd">/ 123456</span>
        </div>
      </div>
    </div>

    <!-- 底部状态栏 -->
    <div class="footer">
      <div class="status">
        <span class="led" />
        <span>SYSTEM ONLINE</span>
      </div>
      <div class="copy">© {{ year }} GCSJ-4 · WebGIS Disaster Early-Warning Platform</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import dayjs from 'dayjs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const year = new Date().getFullYear()
const now = ref(dayjs().format('YYYY-MM-DD HH:mm:ss'))
let timer = null

function quickFill(u) {
  form.username = u
  form.password = '123456'
}

async function onSubmit() {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success({ message: '登录成功，正在进入系统', duration: 1500 })
    setTimeout(() => router.replace(route.query.redirect || '/dashboard'), 300)
  } finally {
    loading.value = false
  }
}

/* 粒子背景 */
const particleEl = ref()
let raf = null
let onResize = null
function startParticles() {
  const canvas = particleEl.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  let w = (canvas.width = window.innerWidth)
  let h = (canvas.height = window.innerHeight)
  const N = 60
  const pts = Array.from({ length: N }, () => ({
    x: Math.random() * w,
    y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.3,
    vy: (Math.random() - 0.5) * 0.3,
    r: Math.random() * 1.5 + 0.5
  }))
  function step() {
    ctx.clearRect(0, 0, w, h)
    for (const p of pts) {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > w) p.vx *= -1
      if (p.y < 0 || p.y > h) p.vy *= -1
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = 'rgba(56, 189, 248, 0.6)'
      ctx.fill()
    }
    for (let i = 0; i < N; i++) {
      for (let j = i + 1; j < N; j++) {
        const dx = pts[i].x - pts[j].x, dy = pts[i].y - pts[j].y
        const d2 = dx * dx + dy * dy
        if (d2 < 18000) {
          ctx.strokeStyle = `rgba(56,189,248,${0.18 * (1 - d2 / 18000)})`
          ctx.lineWidth = 1
          ctx.beginPath()
          ctx.moveTo(pts[i].x, pts[i].y)
          ctx.lineTo(pts[j].x, pts[j].y)
          ctx.stroke()
        }
      }
    }
    raf = requestAnimationFrame(step)
  }
  step()
  onResize = () => { w = canvas.width = window.innerWidth; h = canvas.height = window.innerHeight }
  window.addEventListener('resize', onResize)
}

onMounted(() => {
  startParticles()
  timer = setInterval(() => { now.value = dayjs().format('YYYY-MM-DD HH:mm:ss') }, 1000)
})
onBeforeUnmount(() => {
  if (raf) cancelAnimationFrame(raf)
  if (onResize) window.removeEventListener('resize', onResize)
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.login-page {
  position: fixed; inset: 0;
  background:
    radial-gradient(ellipse at 20% 0%,  rgba(56, 189, 248, 0.18) 0%, transparent 50%),
    radial-gradient(ellipse at 100% 100%, rgba(99, 102, 241, 0.20) 0%, transparent 55%),
    linear-gradient(160deg, #050B1F 0%, #0A1432 60%, #11204A 100%);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  color: #E2E8F0;
}

.bg-grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(to right, rgba(56,189,248,0.08) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(56,189,248,0.08) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse at center, black 30%, transparent 75%);
}
.bg-glow {
  position: absolute; width: 600px; height: 600px; border-radius: 50%;
  filter: blur(120px); opacity: 0.4;
  &.bg-glow-1 { top: -200px; left: -200px; background: #38BDF8; }
  &.bg-glow-2 { bottom: -200px; right: -200px; background: #6366F1; }
}
.bg-particles { position: absolute; inset: 0; }

.brand {
  position: absolute; top: 32px; left: 40px;
  display: flex; align-items: center; gap: 16px;
  z-index: 3;
  .brand-mark {
    font-size: 28px; font-weight: 800; letter-spacing: 4px;
    color: #38BDF8;
    text-shadow: 0 0 20px rgba(56,189,248,0.6);
  }
  .brand-line { width: 1px; height: 36px; background: rgba(56,189,248,0.4); }
  .brand-title { font-size: 18px; font-weight: 600; }
  .brand-sub { font-size: 11px; color: #64748B; letter-spacing: 1px; margin-top: 2px; }
}

.login-card {
  width: 420px;
  padding-bottom: 28px;
  z-index: 2;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  height: 44px; padding: 0 20px;
  border-bottom: 1px solid rgba(91,169,255,0.25);
  background: linear-gradient(90deg, rgba(56,189,248,0.15) 0%, transparent 100%);
  .head-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; letter-spacing: 1px; }
  .dot {
    width: 6px; height: 6px; border-radius: 50%; background: #38BDF8;
    box-shadow: 0 0 10px #38BDF8;
    animation: blink 1.6s ease-in-out infinite;
  }
  .head-time { font-family: 'DIN Alternate', monospace; font-size: 12px; color: #94A3B8; }
}
.card-body { padding: 32px 28px 0; }

:deep(.el-input__wrapper) {
  background: rgba(11, 22, 50, 0.6) !important;
  border: 1px solid rgba(91,169,255,0.25) !important;
  box-shadow: none !important;
  border-radius: 4px;
  padding: 4px 12px;
  &:hover { border-color: rgba(56,189,248,0.5) !important; }
  &.is-focus { border-color: #38BDF8 !important; box-shadow: 0 0 0 2px rgba(56,189,248,0.2) !important; }
}
:deep(.el-input__inner) { color: #E2E8F0 !important; height: 38px; }
:deep(.el-input__prefix) { color: #38BDF8; }

.submit {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  background: linear-gradient(90deg, #2563EB 0%, #38BDF8 100%);
  border: none;
  font-size: 14px; font-weight: 600; letter-spacing: 4px;
  position: relative; overflow: hidden;
  &::before {
    content: ''; position: absolute; top: 0; left: -100%; width: 100%; height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255,255,255,0.3), transparent);
    transition: left 0.5s;
  }
  &:hover::before { left: 100%; }
}

.quick-tip {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 20px; padding-top: 16px;
  border-top: 1px dashed rgba(91,169,255,0.2);
  font-size: 12px; color: #94A3B8;
  .tip-label { color: #64748B; }
  .tip-item {
    color: #38BDF8; cursor: pointer; padding: 1px 8px;
    border: 1px solid rgba(56,189,248,0.3); border-radius: 10px;
    transition: all 0.2s;
    &:hover { background: rgba(56,189,248,0.15); }
  }
  .tip-pwd { color: #64748B; }
}

.footer {
  position: absolute; bottom: 24px; left: 0; right: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 40px;
  font-size: 12px; color: #64748B;
  z-index: 3;
  .status { display: flex; align-items: center; gap: 8px; color: #22C55E;
    .led {
      width: 8px; height: 8px; border-radius: 50%; background: #22C55E;
      box-shadow: 0 0 10px #22C55E;
      animation: blink 1.6s ease-in-out infinite;
    }
  }
  .copy { letter-spacing: 1px; }
}
</style>
