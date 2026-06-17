<template>
  <div class="au-tl">
    <div class="au-tl-meta">
      <div class="au-tl-title">
        <span class="dot" :class="{ playing: replay.playing }"></span>
        <span class="label">回放时刻</span>
        <span class="time">{{ formattedNow }}</span>
      </div>
      <div class="au-tl-window">{{ replay.description || '加载中...' }}</div>
    </div>

    <div class="au-tl-bar" @click="onSeek" ref="barRef">
      <div class="au-tl-bar-bg"></div>
      <div class="au-tl-bar-fill" :style="{ width: `${replay.progress * 100}%` }"></div>
      <!-- 关键时刻锚点 -->
      <div class="au-tl-anchor quake" :style="{ left: `${quakeProgress * 100}%` }" title="泸定 6.8 主震">
        <span>主震</span>
      </div>
      <div class="au-tl-thumb" :style="{ left: `${replay.progress * 100}%` }"></div>
    </div>

    <div class="au-tl-controls">
      <div class="au-tl-btns">
        <button class="ctrl-btn" @click="replay.reset()" title="回到起点">⏮</button>
        <button class="ctrl-btn primary" @click="replay.toggle()">
          {{ replay.playing ? '⏸ 暂停' : '▶ 播放' }}
        </button>
        <button class="ctrl-btn" @click="replay.jumpToQuake()" title="跳到主震时刻">⚡ 主震</button>
      </div>
      <div class="au-tl-speed">
        <span class="speed-label">倍速</span>
        <button
          v-for="s in speedOptions"
          :key="s.value"
          class="speed-btn"
          :class="{ active: replay.speed === s.value }"
          @click="replay.setSpeed(s.value)"
        >{{ s.label }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useReplayStore } from '@/store/replay'

const replay = useReplayStore()
const barRef = ref(null)

const speedOptions = [
  { value: 60,    label: '1×' },     // 1s = 1min
  { value: 600,   label: '10×' },    // 1s = 10min
  { value: 1800,  label: '30×' },    // 1s = 30min
  { value: 3600,  label: '60×' },    // 1s = 1h
]

const formattedNow = computed(() => {
  if (!replay.virtualNow) return '--'
  const d = replay.virtualNow
  const pad = (n) => String(n).padStart(2, '0')
  // 显示北京时间
  const offset = 8 * 60 * 60 * 1000
  const local = new Date(d.getTime() + offset)
  return `${local.getUTCFullYear()}-${pad(local.getUTCMonth()+1)}-${pad(local.getUTCDate())} ${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())}`
})

const quakeProgress = computed(() => {
  if (!replay.windowStart || !replay.windowEnd) return 0
  const a = new Date(replay.windowStart).getTime()
  const b = new Date(replay.windowEnd).getTime()
  const q = new Date('2022-09-05T04:52:00Z').getTime()
  return Math.max(0, Math.min(1, (q - a) / (b - a)))
})

function onSeek(e) {
  if (!barRef.value) return
  const rect = barRef.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width
  replay.seek(x)
}

onMounted(async () => {
  await replay.init()
})
</script>

<style scoped>
.au-tl {
  background: var(--au-card-bg, #FFFFFF);
  border: 1px solid var(--au-border, #E5E7EB);
  border-radius: var(--au-radius-md, 12px);
  padding: 14px 18px;
  box-shadow: var(--au-shadow-sm, 0 2px 6px rgba(15,23,42,0.04));
  font-family: var(--au-font-sans, system-ui);
}
.au-tl-meta {
  display: flex; justify-content: space-between; align-items: baseline;
  margin-bottom: 12px;
}
.au-tl-title {
  display: flex; align-items: center; gap: 10px;
}
.au-tl-title .dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #94A3B8; transition: all 0.3s;
}
.au-tl-title .dot.playing {
  background: #10B981;
  box-shadow: 0 0 0 4px rgba(16,185,129,0.15);
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%   { box-shadow: 0 0 0 0 rgba(16,185,129,0.4); }
  70%  { box-shadow: 0 0 0 8px rgba(16,185,129,0); }
  100% { box-shadow: 0 0 0 0 rgba(16,185,129,0); }
}
.au-tl-title .label {
  font-size: 12px; color: #64748B; letter-spacing: 0.05em;
}
.au-tl-title .time {
  font-family: var(--au-font-mono, ui-monospace);
  font-size: 16px; font-weight: 700;
  color: #1E293B;
  letter-spacing: 0.02em;
}
.au-tl-window {
  font-size: 12px; color: #64748B;
}

.au-tl-bar {
  position: relative;
  height: 10px;
  border-radius: 999px;
  cursor: pointer;
  margin: 6px 8px 14px;
}
.au-tl-bar-bg {
  position: absolute; inset: 0;
  background: linear-gradient(90deg, #E0E7FF, #DBEAFE);
  border-radius: 999px;
}
.au-tl-bar-fill {
  position: absolute; left: 0; top: 0; height: 100%;
  background: linear-gradient(90deg, #6366F1, #2563EB, #06B6D4);
  border-radius: 999px;
  transition: width 0.3s linear;
}
.au-tl-anchor {
  position: absolute; top: -4px;
  width: 2px; height: 18px;
  background: #EF4444;
  transform: translateX(-1px);
  pointer-events: none;
}
.au-tl-anchor span {
  position: absolute; top: -16px; left: 50%;
  transform: translateX(-50%);
  font-size: 10px; color: #EF4444;
  white-space: nowrap; font-weight: 600;
}
.au-tl-thumb {
  position: absolute; top: 50%;
  width: 16px; height: 16px;
  background: #FFFFFF;
  border: 3px solid #2563EB;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 2px 6px rgba(37,99,235,0.3);
  pointer-events: none;
  transition: left 0.3s linear;
}

.au-tl-controls {
  display: flex; justify-content: space-between; align-items: center;
}
.au-tl-btns { display: flex; gap: 6px; }
.ctrl-btn {
  padding: 6px 12px;
  font-size: 12px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  background: #F8FAFC;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}
.ctrl-btn:hover {
  background: #EFF6FF;
  border-color: #93C5FD;
  color: #1D4ED8;
}
.ctrl-btn.primary {
  background: linear-gradient(135deg, #2563EB, #06B6D4);
  color: white;
  border: none;
  font-weight: 600;
  padding: 6px 18px;
}
.ctrl-btn.primary:hover { filter: brightness(1.08); }

.au-tl-speed {
  display: flex; align-items: center; gap: 6px;
}
.speed-label { font-size: 11px; color: #94A3B8; margin-right: 4px; }
.speed-btn {
  padding: 4px 10px;
  font-size: 11px;
  border: 1px solid #E5E7EB;
  background: #FFFFFF;
  color: #64748B;
  border-radius: 6px;
  cursor: pointer;
  font-family: var(--au-font-mono, monospace);
  transition: all 0.15s;
}
.speed-btn:hover { background: #F1F5F9; }
.speed-btn.active {
  background: #2563EB;
  color: white;
  border-color: #2563EB;
}
</style>
