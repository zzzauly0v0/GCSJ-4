<template>
  <!--
    DataPanel — Universal industrial instrumentation panel
    Features:
      - Clip-path cut-corner borders with corner bracket glyphs
      - Title strip with optional status badge
      - Scanline effect on hover / when live
      - Slot for Echarts or numeric content
      - Staggered reveal via :class prop
  -->
  <div
    class="data-panel"
    :class="[
      `status-${status}`,
      { 'is-live': live, 'is-alert': status === 'alert', 'is-warning': status === 'warning' }
    ]"
  >
    <!-- Corner brackets — drawn with CSS -->
    <div class="corner corner-tl" />
    <div class="corner corner-tr" />
    <div class="corner corner-bl" />
    <div class="corner corner-br" />

    <!-- Panel header strip -->
    <div class="panel-header">
      <!-- Left: status dot + title -->
      <div class="panel-header-left">
        <span class="panel-status-dot" :class="statusDotClass" />
        <span class="panel-title-text">{{ title }}</span>
        <span v-if="subtitle" class="panel-subtitle">{{ subtitle }}</span>
      </div>

      <!-- Right: badge + extra slot -->
      <div class="panel-header-right">
        <slot name="header-extra" />
        <span v-if="badge !== undefined" class="panel-badge" :class="`badge-${status}`">
          {{ badge }}
        </span>
        <span class="panel-mode data-value">{{ modeLabel }}</span>
      </div>
    </div>

    <!-- Separator line with glow -->
    <div class="panel-divider" />

    <!-- Main content slot -->
    <div class="panel-body" :style="bodyStyle">
      <slot />
    </div>

    <!-- Scanline overlay — activates when live=true -->
    <div v-if="live" class="panel-scanline" />

    <!-- Bottom status bar -->
    <div v-if="showFooter" class="panel-footer">
      <slot name="footer">
        <span class="footer-text data-value">{{ footerText }}</span>
      </slot>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import dayjs from 'dayjs'

const props = defineProps({
  /** Panel title — displayed in industrial caps */
  title: {
    type: String,
    required: true,
  },
  /** Small subtitle after title */
  subtitle: {
    type: String,
    default: '',
  },
  /**
   * Status variant:
   *   'normal'  — crystal blue (default)
   *   'warning' — lava orange
   *   'alert'   — alert red
   *   'offline' — dim/inactive
   */
  status: {
    type: String,
    default: 'normal',
    validator: v => ['normal', 'warning', 'alert', 'offline'].includes(v),
  },
  /** Show active scanline sweep effect */
  live: {
    type: Boolean,
    default: true,
  },
  /** Badge value shown top-right of header */
  badge: {
    type: [String, Number],
    default: undefined,
  },
  /** Show footer bar */
  showFooter: {
    type: Boolean,
    default: false,
  },
  /** Footer text (if not using footer slot) */
  footerText: {
    type: String,
    default: () => `UPDATED ${dayjs().format('HH:mm:ss')}`,
  },
  /** Optional min-height for panel body */
  bodyMinHeight: {
    type: String,
    default: undefined,
  },
})

const statusDotClass = computed(() => {
  const map = {
    normal:  'led-online',
    warning: 'led-warning',
    alert:   'led-alert',
    offline: 'led-offline',
  }
  return `led ${map[props.status] || 'led-online'}`
})

const modeLabel = computed(() => {
  const map = { normal: 'LIVE', warning: 'WARN', alert: 'CRIT', offline: 'IDLE' }
  return map[props.status] || 'LIVE'
})

const bodyStyle = computed(() => {
  const s = {}
  if (props.bodyMinHeight) s['min-height'] = props.bodyMinHeight
  return s
})
</script>

<style scoped>
/* ================================================================
   PANEL SHELL
================================================================ */
.data-panel {
  position: relative;
  background: var(--bg-panel);
  border: 1px solid var(--border-panel);
  box-shadow: var(--glow-panel);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: box-shadow 300ms ease, border-color 300ms ease;
  /* Industrial cut-corner via clip-path */
  clip-path: polygon(
    0 0,
    calc(100% - 10px) 0,
    100% 10px,
    100% 100%,
    10px 100%,
    0 calc(100% - 10px)
  );
}

/* Status variants */
.data-panel.status-normal {
  --panel-accent: var(--signal-crystal);
  --panel-accent-dim: var(--signal-crystal-dim);
}
.data-panel.status-warning {
  --panel-accent: var(--signal-lava);
  --panel-accent-dim: var(--signal-lava-dim);
  border-color: var(--border-warning);
  box-shadow:
    0 0 0 1px var(--border-warning),
    0 4px 24px rgba(255, 106, 26, 0.12);
}
.data-panel.status-alert {
  --panel-accent: var(--signal-alert);
  --panel-accent-dim: var(--signal-alert-dim);
  border-color: var(--border-critical);
  box-shadow:
    0 0 0 1px var(--border-critical),
    0 4px 24px rgba(255, 45, 45, 0.18);
  animation: glow-breathe 1800ms ease-in-out infinite;
  animation-delay: 1400ms;
}
.data-panel.status-offline {
  --panel-accent: var(--text-tertiary);
  --panel-accent-dim: rgba(61, 90, 115, 0.10);
  opacity: 0.65;
  border-color: var(--border-separator);
}

/* ================================================================
   CORNER BRACKETS
   Drawn as 2px L-shaped pseudo-elements at each corner
================================================================ */
.corner {
  position: absolute;
  width: 12px;
  height: 12px;
  z-index: 10;
  pointer-events: none;
}
.corner-tl {
  top: -1px;
  left: -1px;
  border-top: 2px solid var(--panel-accent, var(--signal-crystal));
  border-left: 2px solid var(--panel-accent, var(--signal-crystal));
}
.corner-tr {
  top: -1px;
  right: -1px;
  border-top: 2px solid var(--panel-accent, var(--signal-crystal));
  border-right: 2px solid var(--panel-accent, var(--signal-crystal));
}
.corner-bl {
  bottom: -1px;
  left: -1px;
  border-bottom: 2px solid var(--panel-accent, var(--signal-crystal));
  border-left: 2px solid var(--panel-accent, var(--signal-crystal));
}
.corner-br {
  bottom: -1px;
  right: -1px;
  border-bottom: 2px solid var(--panel-accent, var(--signal-crystal));
  border-right: 2px solid var(--panel-accent, var(--signal-crystal));
}

/* ================================================================
   HEADER
================================================================ */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  padding: 0 12px 0 10px;
  flex-shrink: 0;
  background: linear-gradient(90deg,
    rgba(0, 229, 255, 0.05) 0%,
    transparent 60%
  );
}

.panel-header-left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.panel-title-text {
  font-family: var(--font-display);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--panel-accent, var(--signal-crystal));
}

.panel-subtitle {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.06em;
}

.panel-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.panel-badge {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 1px;
}
.badge-normal  { color: var(--signal-crystal); background: var(--signal-crystal-dim); border: 1px solid rgba(0,229,255,0.20); }
.badge-warning { color: var(--signal-lava); background: var(--signal-lava-dim); border: 1px solid rgba(255,106,26,0.25); }
.badge-alert   { color: var(--signal-alert); background: var(--signal-alert-dim); border: 1px solid rgba(255,45,45,0.28); animation: led-blink 1000ms steps(1) infinite; }
.badge-offline { color: var(--text-tertiary); background: transparent; border: 1px solid var(--border-separator); }

.panel-mode {
  font-family: var(--font-mono);
  font-size: 8px;
  color: var(--text-tertiary);
  letter-spacing: 0.15em;
}

/* ================================================================
   DIVIDER
================================================================ */
.panel-divider {
  height: 1px;
  background: linear-gradient(90deg,
    var(--panel-accent, var(--signal-crystal)) 0%,
    rgba(0, 229, 255, 0.25) 30%,
    transparent 80%
  );
  flex-shrink: 0;
  opacity: 0.4;
}
.status-warning .panel-divider {
  background: linear-gradient(90deg, var(--signal-lava) 0%, rgba(255,106,26,0.25) 30%, transparent 80%);
}
.status-alert .panel-divider {
  background: linear-gradient(90deg, var(--signal-alert) 0%, rgba(255,45,45,0.25) 30%, transparent 80%);
}

/* ================================================================
   BODY
================================================================ */
.panel-body {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  position: relative;
}

/* ================================================================
   SCANLINE EFFECT
================================================================ */
.panel-scanline {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    var(--signal-crystal-dim) 25%,
    rgba(0, 229, 255, 0.30) 50%,
    var(--signal-crystal-dim) 75%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 20;
  animation: scanline-sweep var(--dur-scan) linear infinite;
  animation-delay: 1600ms;
  top: -2px;
  will-change: top;
}
.status-alert .panel-scanline {
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(255,45,45,0.20) 30%,
    rgba(255,45,45,0.40) 50%,
    rgba(255,45,45,0.20) 70%,
    transparent 100%
  );
}

/* ================================================================
   FOOTER
================================================================ */
.panel-footer {
  height: 22px;
  display: flex;
  align-items: center;
  padding: 0 10px;
  border-top: 1px solid var(--border-separator);
  flex-shrink: 0;
  background: var(--bg-panel-deep);
}
.footer-text {
  font-family: var(--font-mono);
  font-size: 9px;
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}
</style>
