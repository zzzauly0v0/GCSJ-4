<template>
  <!--
    StormMap — OpenLayers map with STORMWATCH dark console aesthetic
    Features:
      - Dark tile styling via CSS filter: invert + hue-rotate
      - Pulsing radar sweep ring (CSS conic-gradient animation)
      - Disaster point markers with glow
      - Layer toggle HUD overlay
      - Coordinate readout in JetBrains Mono
      - Map enters with basemap-reveal animation
  -->
  <div class="storm-map-wrap reveal reveal-basemap">

    <!-- Radar sweep overlay (CSS-only, rotates continuously) -->
    <div class="radar-sweep" />

    <!-- Map canvas -->
    <div ref="mapEl" class="map-canvas" />

    <!-- Layer control HUD — top right -->
    <div class="layer-hud reveal reveal-delay-6">
      <div class="hud-label">LAYERS</div>
      <div
        v-for="l in layers"
        :key="l.key"
        class="layer-row"
        :class="{ active: l.visible }"
        @click="toggleLayer(l)"
      >
        <span class="layer-color-dot" :style="{ background: l.color, boxShadow: `0 0 5px ${l.color}` }" />
        <span class="layer-name">{{ l.name }}</span>
        <span class="layer-count data-value">{{ l.count }}</span>
      </div>
    </div>

    <!-- Legend HUD — bottom right -->
    <div class="legend-hud reveal reveal-delay-7">
      <div class="hud-label">ALERT LEVELS</div>
      <div v-for="lv in [4,3,2,1]" :key="lv" class="legend-row">
        <span class="legend-dot" :style="{ background: levelColors[lv], boxShadow: `0 0 4px ${levelColors[lv]}` }" />
        <span class="legend-name">{{ levelLabels[lv] }}</span>
      </div>
    </div>

    <!-- Coordinate readout — bottom left -->
    <div class="coord-hud reveal reveal-delay-5">
      <div class="coord-row">
        <span class="coord-key">PROJ</span>
        <span class="coord-val data-value">EPSG:3857</span>
      </div>
      <div class="coord-row">
        <span class="coord-key">LON</span>
        <span class="coord-val data-value">{{ centerLon }}°E</span>
      </div>
      <div class="coord-row">
        <span class="coord-key">LAT</span>
        <span class="coord-val data-value">{{ centerLat }}°N</span>
      </div>
    </div>

    <!-- Scanline overlay on map -->
    <div class="map-scanline" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useMapStore } from '@/store/map'

const props = defineProps({
  /** Layer visibility array */
  layers: {
    type: Array,
    default: () => [],
  },
  /** Function from useMap to toggle a layer */
  onToggleLayer: {
    type: Function,
    default: undefined,
  },
})

const emit = defineEmits(['ready'])

const mapStore = useMapStore()
const mapEl = ref(null)

const levelColors = {
  1: '#3B8BF0',
  2: '#FFD60A',
  3: '#FF6A1A',
  4: '#FF2D2D',
}
const levelLabels = {
  1: '蓝色预警',
  2: '黄色预警',
  3: '橙色预警',
  4: '红色预警',
}

const centerLon = computed(() => (mapStore.center[0] || 104).toFixed(4))
const centerLat = computed(() => (mapStore.center[1] || 35).toFixed(4))

function toggleLayer(l) {
  l.visible = !l.visible
  props.onToggleLayer?.(l)
}

onMounted(() => {
  emit('ready', mapEl.value)
})
</script>

<style scoped>
/* ================================================================
   MAP WRAPPER
================================================================ */
.storm-map-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--bg-console);
}

/* ================================================================
   MAP CANVAS — dark filter applied to OL map tiles
================================================================ */
.map-canvas {
  width: 100%;
  height: 100%;
  /* Invert light tiles to dark; hue-rotate to shift blue map water */
  filter: invert(0.92) hue-rotate(190deg) saturate(0.6) brightness(0.85);
  transition: filter 400ms ease;
}

/* ================================================================
   RADAR SWEEP — pure CSS conic-gradient rotation
   A single div creates the rotating sector glow
================================================================ */
.radar-sweep {
  position: absolute;
  top: 50%;
  left: 50%;
  width: min(60vw, 400px);
  height: min(60vw, 400px);
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    rgba(0, 229, 255, 0.06) 30deg,
    rgba(0, 229, 255, 0.14) 60deg,
    transparent 61deg
  );
  animation: radar-rotate var(--dur-radar, 6000ms) linear infinite;
  animation-delay: 1600ms;
  pointer-events: none;
  z-index: 5;
  will-change: transform;
  mix-blend-mode: screen;
}

/* ================================================================
   LAYER HUD OVERLAY
================================================================ */
.layer-hud,
.legend-hud,
.coord-hud {
  position: absolute;
  background: rgba(10, 14, 26, 0.88);
  border: 1px solid var(--border-panel);
  backdrop-filter: blur(10px);
  z-index: 20;
  padding: 8px 10px;
}

.layer-hud {
  top: 12px;
  right: 12px;
  min-width: 140px;
}
.legend-hud {
  bottom: 48px;
  right: 12px;
  min-width: 120px;
}
.coord-hud {
  bottom: 12px;
  left: 40px;  /* offset from OL zoom controls */
  font-family: var(--font-mono);
}

.hud-label {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.20em;
  text-transform: uppercase;
  color: var(--text-tertiary);
  margin-bottom: 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border-separator);
}

/* Layer rows */
.layer-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 0;
  cursor: pointer;
  transition: opacity 120ms ease;
  opacity: 0.55;
}
.layer-row.active { opacity: 1; }
.layer-row:hover  { opacity: 0.85; }

.layer-color-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: box-shadow 150ms ease;
}
.layer-name {
  font-family: var(--font-ui);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-secondary);
  flex: 1;
}
.layer-count {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-tertiary);
  min-width: 24px;
  text-align: right;
}

/* Legend rows */
.legend-row {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 2px 0;
}
.legend-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-name {
  font-family: var(--font-ui);
  font-size: 10px;
  color: var(--text-secondary);
}

/* Coord rows */
.coord-row {
  display: flex;
  gap: 8px;
  align-items: center;
  line-height: 1.6;
}
.coord-key {
  font-family: var(--font-mono);
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.15em;
  color: var(--text-tertiary);
  min-width: 32px;
}
.coord-val {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--signal-crystal);
  letter-spacing: 0.04em;
}

/* ================================================================
   MAP SCANLINE
================================================================ */
.map-scanline {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(0, 229, 255, 0.04) 25%,
    rgba(0, 229, 255, 0.12) 50%,
    rgba(0, 229, 255, 0.04) 75%,
    transparent 100%
  );
  pointer-events: none;
  z-index: 25;
  top: -2px;
  animation: scanline-sweep 5000ms linear infinite;
  animation-delay: 1800ms;
  will-change: top;
}
</style>
