<template>
  <div class="login-page">
    <!-- 背景动效：WeatherNext 视频 -->
    <video
      class="bg-video"
      src="/weathernext.webm#t=0.13"
      autoplay
      loop
      muted
      playsinline
      preload="auto"
    />
    <div class="bg-overlay" />

    <!-- 顶部品牌 -->
    <div class="brand">
      <div class="brand-mark"></div>
      <div class="brand-line" />
      <div class="brand-text">
        <div class="brand-title">METEOROLOGICAL & GEOLOGICAL DISASTER MONITORING SYSTEM</div>
        <div class="brand-sub">Engineering practice assignments</div>
      </div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <div class="card-head">
        <div class="head-title">
          Welcome
        </div>
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
            <span v-if="!loading">Enter System</span>
            <span v-else>验证中...</span>
          </el-button>
        </el-form>

        <div class="quick-tip">
          <span class="tip-label">Default account</span>
          <span class="tip-item" @click="quickFill('admin')">admin</span>
          <span class="tip-item" @click="quickFill('viewer')">viewer</span>
        </div>
      </div>
    </div>

    <!-- 底部状态栏 -->
    <div class="footer">
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

onMounted(() => {
  timer = setInterval(() => { now.value = dayjs().format('YYYY-MM-DD HH:mm:ss') }, 1000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.login-page {
  position: fixed; inset: 0;
  background: rgb(248, 249, 252);
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
  color: var(--au-text-primary);
}

/* 视频背景：充满整屏 */
.bg-video {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  object-fit: cover;
  z-index: 0;
  pointer-events: none;
}
/* 极淡蒙版：保留视频可见度，仅给文字一点对比 */
.bg-overlay {
  position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.10) 100%);
  z-index: 1;
  pointer-events: none;
}

.brand {
  position: absolute; top: 32px; left: 40px;
  display: flex; align-items: center; gap: 16px;
  z-index: 3;
  .brand-mark {
    font-size: 28px; font-weight: 800; letter-spacing: 4px;
    color: var(--au-text-strong);
  }
  .brand-line { width: 1px; height: 36px; background: var(--au-border-base); }
  .brand-title { font-size: 18px; font-weight: 600; color: var(--au-text-strong); }
  .brand-sub { font-size: 11px; color: var(--au-text-secondary); letter-spacing: 1px; margin-top: 2px; }
}

.login-card {
  width: 420px;
  padding-bottom: 30px;
  z-index: 2;
  position: relative;
  /* 毛玻璃：仅靠 backdrop-filter 模糊背景视频，自身几乎全透明 */
  background: transparent;
  border: 1px solid rgba(160, 160, 160, 0.45); // 边框透明度
  border-radius: var(--au-radius-lg);  // 继承16px的圆角度数
  box-shadow: 0 8px 32px rgba(60, 64, 67, 0.18);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  overflow: hidden;
  color: #FFFFFF;
}
.card-head {
  display: flex; align-items: center; justify-content: space-between;
  height: 44px; padding: 0 20px;
  background: transparent;
  border-radius: var(--au-radius-lg) var(--au-radius-lg) 0 0;
  .head-title { display: flex; align-items: center; gap: 8px; font-size: 20px; font-weight: 600; color: rgba(255, 255, 255, 0.65) }
  .dot {
    width: 6px; height: 6px; border-radius: 50%; background: #34D399;
    animation: blink 1.6s ease-in-out infinite;
  }
  .head-time { font-family: var(--au-font-num); font-size: 12px; color: rgba(255,255,255,0.85); }
}
.card-body { padding: 32px 28px 0; }

:deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.10) !important;
  border: 1px solid rgba(160, 160, 160, 0.45) !important;
  box-shadow: none !important;
  border-radius: var(--au-radius-md) !important;
  padding: 4px 12px !important;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  
  &:hover {     
    background: rgba(255, 255, 255, 0.18)  !important;
    border-color: rgba(120, 120, 120, 0.40) !important;
  }
  &.is-focus {
    border-color: rgba(99, 99, 99, 0.85) !important;
    background: rgba(255, 255, 255, 0.18) !important;
    box-shadow: 0 0 0 2px rgba(99, 99, 99, 0.15) !important;
  }
}
:deep(.el-input__inner) {
  color: #FFFFFF !important;
  height: 38px;
  &::placeholder { color: rgba(255,255,255,0.65) !important; }
}
:deep(.el-input__prefix), :deep(.el-input__suffix) { color: rgba(255,255,255,0.80); }

.submit {
  width: 100%;
  height: 44px;
  margin-top: 8px;
  background: rgba(255, 255, 255, 0.15) !important;
  border: 1px solid rgba(160, 160, 160, 0.50) !important;
  color: rgba(255, 255, 255, 0.65) !important;
  border-radius: var(--au-radius-md) !important;
  font-size: 28px; font-weight: 600; letter-spacing: 4px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: all 0.2s;
  &:hover {
    background: rgba(255, 255, 255, 0.18)  !important;
    border-color: rgba(120, 120, 120, 0.40) !important;
    transform: translateY(-1px);
  }
}

.quick-tip {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-top: 20px; padding-top: 16px;
  font-size: 16px; color: rgba(255, 255, 255, 0.80);
  .tip-label { color: rgba(255, 255, 255, 0.65); }
  .tip-item {
    color: rgba(255, 255, 255, 0.65); 
    cursor: pointer; padding: 1px 10px;
    border: 1px solid rgba(160, 160, 160, 0.45);
    background: rgba(255, 255, 255, 0.10);
    border-radius: var(--au-radius-sm);
    transition: all 0.2s;
    &:hover {
      background: rgba(255, 255, 255, 0.18);
      border-color: rgba(120, 120, 120, 0.40);
    }
  }
  .tip-pwd { color: rgba(255, 255, 255, 0.65); }
}

.footer {
  position: absolute; bottom: 24px; left: 0; right: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 40px;
  font-size: 12px; color: var(--au-text-secondary);
  z-index: 3;
  .status { display: flex; align-items: center; gap: 8px; color: #1E8E3E;
    .led {
      width: 8px; height: 8px; border-radius: 50%; background: #1E8E3E;
      box-shadow: 0 0 0 3px rgba(30,142,62,0.18);
      animation: blink 1.6s ease-in-out infinite;
    }
  }
  .copy { letter-spacing: 1px; }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.4; }
}

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .brand {
    top: 16px;
    left: 16px;
    right: 16px;
    gap: 10px;
    .brand-mark { font-size: 20px; letter-spacing: 2px; }
    .brand-line { height: 24px; }
    .brand-title {
      font-size: 13px;
      line-height: 1.3;
      max-width: 60vw;
    }
    .brand-sub { display: none; }
  }

  .login-card {
    width: 88vw;
    max-width: 380px;
    padding-bottom: 22px;
  }
  .card-head { height: 38px; padding: 0 16px; }
  .card-body { padding: 22px 18px 0; }
  .submit { letter-spacing: 2px; height: 42px; }
  .quick-tip { font-size: 11px; gap: 6px; }

  .footer {
    bottom: 12px;
    padding: 0 16px;
    font-size: 10px;
    .copy { letter-spacing: 0.5px; text-align: center; flex: 1; }
  }
}

@media (max-width: 480px) {
  .login-card { width: 92vw; }
  .brand .brand-title { font-size: 12px; }
}
</style>
