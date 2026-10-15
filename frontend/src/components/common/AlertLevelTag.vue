<template>
  <!--
    AlertLevelTag — Inline warning level badge
    Uses STORMWATCH fluorescent palette + JetBrains Mono
    Levels: 1=crystal blue / 2=hazard yellow / 3=lava orange / 4=alert red
  -->
  <span class="level-tag" :class="`lvl-${level}`">
    <span class="tag-dot" />
    {{ meta.label }}
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { levelMeta } from '@/utils/format'

const props = defineProps({
  level: { type: Number, required: true },
})
const meta = computed(() => levelMeta(props.level))
</script>

<style scoped>
.level-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 1px;
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  white-space: nowrap;
}
.tag-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

/* Level 1 — 蓝色 */
.lvl-1 {
  color: var(--level-1-color);
  background: var(--level-1-dim);
  border: 1px solid rgba(59, 139, 240, 0.28);
}
/* Level 2 — 黄色 */
.lvl-2 {
  color: var(--level-2-color);
  background: var(--level-2-dim);
  border: 1px solid rgba(255, 214, 10, 0.28);
}
/* Level 3 — 橙色 */
.lvl-3 {
  color: var(--level-3-color);
  background: var(--level-3-dim);
  border: 1px solid rgba(255, 106, 26, 0.30);
}
/* Level 4 — 红色 (breathing flash) */
.lvl-4 {
  color: var(--level-4-color);
  background: var(--level-4-dim);
  border: 1px solid rgba(255, 45, 45, 0.35);
  animation: notif-flash 1200ms ease-in-out infinite;
  animation-delay: 1400ms;
}
.lvl-4 .tag-dot {
  animation: led-blink 700ms steps(1, end) infinite;
}
</style>
