<template>
  <div class="au-tl-mini">
    <span class="dot" :class="{ playing: replay.playing }"></span>
    <span class="time">{{ formattedNow }}</span>
    <button class="mini-btn" @click="replay.toggle()">{{ replay.playing ? '⏸' : '▶' }}</button>
    <button class="mini-btn" @click="replay.jumpToPeak()" title="跳到暴雨峰值时刻">⚡ 暴雨峰值</button>
    <div class="mini-bar" @click="onSeek" ref="barRef">
      <div class="mini-fill" :style="{ width: `${replay.progress * 100}%` }"></div>
      <div class="mini-anchor" :style="{ left: `${peakProgress * 100}%` }" title="暴雨峰值"></div>
    </div>
    <div class="mini-speed">
      <button
        v-for="s in speedOptions"
        :key="s.value"
        class="speed-btn"
        :class="{ active: replay.speed === s.value }"
        @click="replay.setSpeed(s.value)"
      >{{ s.label }}</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useReplayStore } from '@/store/replay'

const replay = useReplayStore()
const barRef = ref(null)

const speedOptions = [
  { value: 600,   label: '10×' },
  { value: 1800,  label: '30×' },
  { value: 3600,  label: '60×' },
]

const formattedNow = computed(() => {
  if (!replay.virtualNow) return '--'
  const d = replay.virtualNow
  const pad = (n) => String(n).padStart(2, '0')
  const local = new Date(d.getTime() + 8 * 60 * 60 * 1000)
  return `${local.getUTCFullYear()}-${pad(local.getUTCMonth()+1)}-${pad(local.getUTCDate())} ${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())}`
})

const peakProgress = computed(() => {
  if (!replay.windowStart || !replay.windowEnd) return 0
  const a = new Date(replay.windowStart).getTime()
  const b = new Date(replay.windowEnd).getTime()
  const q = new Date('2022-09-06T04:00:00Z').getTime()
  return Math.max(0, Math.min(1, (q - a) / (b - a)))
})

function onSeek(e) {
  if (!barRef.value) return
  const rect = barRef.value.getBoundingClientRect()
  replay.seek((e.clientX - rect.left) / rect.width)
}

onMounted(async () => {
  await replay.init()
})
</script>

<style scoped>
.au-tl-mini {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 14px;
  background: linear-gradient(135deg, rgba(99,102,241,0.05), rgba(6,182,212,0.05));
  border: 1px solid #E0E7FF;
  border-radius: 10px;
  font-family: var(--au-font-sans, system-ui);
}
.dot { width: 7px; height: 7px; border-radius: 50%; background: #94A3B8; transition: all 0.3s; }
.dot.playing { background: #10B981; box-shadow: 0 0 0 4px rgba(16,185,129,0.15); animation: pulseMini 1.5s infinite; }
@keyframes pulseMini {
  0%   { box-shadow: 0 0 0 0 rgba(16,185,129,0.4); }
  70%  { box-shadow: 0 0 0 6px rgba(16,185,129,0); }
  100% { box-shadow: 0 0 0 0 rgba(16,185,129,0); }
}
.time {
  font-family: 'DIN Alternate', monospace;
  font-size: 13px; font-weight: 700; color: #1E293B;
  white-space: nowrap;
}
.mini-btn {
  padding: 4px 10px; font-size: 12px;
  border: 1px solid #E5E7EB; border-radius: 6px;
  background: #fff; color: #2563EB; cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
}
.mini-btn:hover { background: #EFF6FF; }
.mini-bar {
  position: relative; flex: 1; min-width: 120px;
  height: 6px; background: #E0E7FF; border-radius: 999px; cursor: pointer;
}
.mini-fill {
  position: absolute; left: 0; top: 0; height: 100%;
  background: linear-gradient(90deg, #6366F1, #2563EB);
  border-radius: 999px;
  transition: width 0.3s linear;
}
.mini-anchor {
  position: absolute; top: -3px; width: 2px; height: 12px;
  background: #EF4444; transform: translateX(-1px); pointer-events: none;
}
.mini-speed { display: flex; gap: 2px; }
.speed-btn {
  padding: 3px 7px; font-size: 10px;
  border: 1px solid #E5E7EB; background: #fff; color: #64748B;
  border-radius: 5px; cursor: pointer;
  font-family: 'DIN Alternate', monospace;
}
.speed-btn.active { background: #2563EB; color: #fff; border-color: #2563EB; }
</style>
