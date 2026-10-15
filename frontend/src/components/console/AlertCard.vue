<template>
  <!--
    AlertCard — Multi-level warning card
    Level variants: 1=blue, 2=hazard-yellow, 3=lava-orange, 4=alert-red
    Red cards emit a breathing pulse animation + scanline.
    Code/timestamp use JetBrains Mono enforced.
  -->
  <div
    class="alert-card"
    :class="[`lvl-${level}`, { 'is-critical': level >= 4, 'is-high': level === 3 }]"
    @click="emit('click', alert)"
  >
    <!-- Left accent stripe -->
    <div class="card-stripe" />

    <!-- Pulse indicator (levels 3+) -->
    <div v-if="level >= 3" class="pulse-ring" />

    <!-- Level badge with status dot -->
    <div class="card-level-badge">
      <span class="level-dot" />
      <span class="level-text">{{ meta.label }}</span>
    </div>

    <!-- Card body -->
    <div class="card-body">
      <!-- Row 1: title + status chip -->
      <div class="card-row1">
        <span class="card-title">{{ alert.title || '无标题预警' }}</span>
        <span class="card-status-chip" :class="`chip-${statusKey}`">
          {{ statusLabel }}
        </span>
      </div>

      <!-- Row 2: code + disaster type + time -->
      <div class="card-row2">
        <span class="card-code data-value">{{ alert.code || 'ALT-000000' }}</span>
        <span class="card-type">{{ alert.disasterType || '—' }}</span>
        <span class="card-time data-value">{{ formattedTime }}</span>
      </div>

      <!-- Row 3 (optional): coordinates -->
      <div v-if="alert.longitude != null" class="card-row3">
        <span class="card-coord data-value">
          {{ fmtCoord(alert.longitude) }}°E
          {{ fmtCoord(alert.latitude) }}°N
        </span>
        <span class="card-fly-hint">TAP TO FLY</span>
      </div>
    </div>

    <!-- Scanline for critical cards -->
    <div v-if="level >= 4" class="card-scanline" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'
import { levelMeta } from '@/utils/format'

const props = defineProps({
  /** The raw alert object from the API */
  alert: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['click'])

const level = computed(() => props.alert.level || 1)
const meta = computed(() => levelMeta(level.value))

const formattedTime = computed(() => {
  if (!props.alert.triggeredAt) return '—'
  return dayjs(props.alert.triggeredAt).format('MM-DD HH:mm')
})

const statusKey = computed(() => {
  const map = { 1: 'pending', 2: 'sent', 3: 'confirmed', 4: 'closed' }
  return map[props.alert.status] || 'pending'
})
const statusLabel = computed(() => {
  const map = { pending: '待发送', sent: '已发送', confirmed: '已确认', closed: '已关闭' }
  return map[statusKey.value] || '未知'
})

function fmtCoord(v) {
  if (v == null) return '—'
  return Number(v).toFixed(4)
}
</script>

<style scoped>
/* ================================================================
   CARD BASE
================================================================ */
.alert-card {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 0;
  background: var(--bg-panel);
  border: 1px solid var(--border-separator);
  border-left: none;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
  overflow: hidden;
  min-height: 58px;
  clip-path: polygon(
    0 0,
    calc(100% - 6px) 0,
    100% 6px,
    100% 100%,
    6px 100%,
    0 calc(100% - 6px)
  );
}
.alert-card:hover {
  background: var(--bg-panel-elevated);
}

/* ================================================================
   LEVEL STRIPE (left edge accent)
================================================================ */
.card-stripe {
  width: 3px;
  flex-shrink: 0;
  background: var(--card-color);
}
.lvl-1 { --card-color: var(--level-1-color); border-color: rgba(59, 139, 240, 0.18); }
.lvl-2 { --card-color: var(--level-2-color); border-color: rgba(255, 214, 10, 0.18); }
.lvl-3 { --card-color: var(--level-3-color); border-color: rgba(255, 106, 26, 0.22); }
.lvl-4 {
  --card-color: var(--level-4-color);
  border-color: rgba(255, 45, 45, 0.30);
  background: rgba(255, 45, 45, 0.04);
  animation: notif-flash 1800ms ease-in-out infinite;
  animation-delay: 1400ms;
}

/* ================================================================
   PULSE RING (levels 3+)
================================================================ */
.pulse-ring {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--card-color);
  animation: alert-pulse-ring var(--dur-pulse) ease-out infinite;
  animation-delay: 1400ms;
  opacity: 0;
  pointer-events: none;
}
.lvl-3 .pulse-ring { animation-duration: 2000ms; }
.lvl-4 .pulse-ring { animation-duration: 1200ms; }

/* ================================================================
   LEVEL BADGE (left pill)
================================================================ */
.card-level-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 36px;
  flex-shrink: 0;
  padding: 8px 0;
  background: linear-gradient(180deg, transparent, rgba(255,255,255,0.02));
}
.level-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--card-color);
  flex-shrink: 0;
  box-shadow: 0 0 6px var(--card-color);
}
.lvl-4 .level-dot { animation: led-blink 700ms steps(1, end) infinite; }
.lvl-3 .level-dot { animation: led-pulse 1600ms ease-in-out infinite; }
.level-text {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  color: var(--card-color);
  letter-spacing: 0.06em;
  writing-mode: vertical-lr;
  text-orientation: mixed;
  transform: rotate(180deg);
  text-transform: uppercase;
}

/* ================================================================
   BODY
================================================================ */
.card-body {
  flex: 1;
  padding: 7px 10px 7px 6px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.card-row1 {
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title {
  font-family: var(--font-ui);
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-status-chip {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 600;
  padding: 1px 6px;
  border-radius: 1px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  flex-shrink: 0;
}
.chip-pending   { color: var(--signal-hazard); background: var(--signal-hazard-dim); border: 1px solid rgba(255,214,10,0.20); }
.chip-sent      { color: var(--signal-crystal); background: var(--signal-crystal-dim); border: 1px solid rgba(0,229,255,0.20); }
.chip-confirmed { color: #3fb950; background: rgba(63,185,80,0.12); border: 1px solid rgba(63,185,80,0.25); }
.chip-closed    { color: var(--text-tertiary); background: transparent; border: 1px solid var(--border-separator); }

.card-row2 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-code {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-secondary);
  letter-spacing: 0.06em;
  flex-shrink: 0;
}
.card-type {
  font-family: var(--font-ui);
  font-size: 10px;
  color: var(--text-tertiary);
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.card-time {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
  flex-shrink: 0;
}

.card-row3 {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-coord {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--signal-crystal);
  letter-spacing: 0.06em;
  opacity: 0.70;
}
.card-fly-hint {
  font-family: var(--font-mono);
  font-size: 7px;
  color: var(--text-tertiary);
  letter-spacing: 0.15em;
  text-transform: uppercase;
  opacity: 0;
  transition: opacity 150ms ease;
}
.alert-card:hover .card-fly-hint {
  opacity: 1;
}

/* ================================================================
   SCANLINE (level 4 only)
================================================================ */
.card-scanline {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(255, 45, 45, 0.20) 25%,
    rgba(255, 45, 45, 0.45) 50%,
    rgba(255, 45, 45, 0.20) 75%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 10;
  top: -1px;
  animation: scanline-sweep 2400ms linear infinite;
  animation-delay: 1400ms;
  will-change: top;
}
</style>
