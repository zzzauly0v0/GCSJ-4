<template>
  <!--
    AuSelect — 完全用 div 自绘的下拉选择器，不依赖浏览器原生 <select>。
    渐变边框 + 平滑展开 + 学术风滚动。
  -->
  <div class="au-select-wrap" :class="{ 'is-open': open, 'is-disabled': disabled }" ref="rootEl">
    <button type="button" class="au-select-trigger" @click="toggle" :disabled="disabled">
      <span class="au-select-label" :class="{ placeholder: !current }">
        {{ current ? current.label : placeholder }}
      </span>
      <span class="au-select-caret" :class="{ open }">
        <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
          <path d="M2.5 4.5L6 8l3.5-3.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </span>
    </button>

    <Teleport to="body">
      <transition name="au-pop">
        <div v-if="open" class="au-select-panel au-select-panel--floating" :style="panelStyle" ref="panelEl">
          <div v-if="searchable" class="au-select-search">
            <input
              v-model="query"
              type="text"
              class="au-select-search-input"
              :placeholder="searchPlaceholder"
              @click.stop
            />
          </div>
          <div class="au-select-options">
            <div
              v-for="opt in filtered"
              :key="opt.value"
              class="au-select-option"
              :class="{ active: opt.value === modelValue }"
              @click="pick(opt)"
            >
              <span class="au-select-option-label">{{ opt.label }}</span>
              <svg v-if="opt.value === modelValue" viewBox="0 0 12 12" width="12" height="12" class="au-select-check">
                <path d="M2 6.5L5 9.5L10 3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </div>
            <div v-if="filtered.length === 0" class="au-select-empty">无匹配项</div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, null], default: null },
  options: { type: Array, default: () => [] },          // [{ value, label }]
  placeholder: { type: String, default: '请选择' },
  searchable: { type: Boolean, default: false },
  searchPlaceholder: { type: String, default: '搜索…' },
  disabled: { type: Boolean, default: false },
  width: { type: [String, Number], default: '100%' },
})
const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const query = ref('')
const rootEl = ref(null)
const panelEl = ref(null)
const panelPos = ref({ top: 0, left: 0, width: 0 })

const current = computed(() =>
  props.options.find(o => o.value === props.modelValue)
)

const filtered = computed(() => {
  if (!props.searchable || !query.value) return props.options
  const q = query.value.toLowerCase()
  return props.options.filter(o => String(o.label).toLowerCase().includes(q))
})

const panelStyle = computed(() => ({
  position: 'fixed',
  top: `${panelPos.value.top}px`,
  left: `${panelPos.value.left}px`,
  width: typeof props.width === 'number'
    ? `${props.width}px`
    : (props.width === '100%' ? `${panelPos.value.width}px` : props.width),
  zIndex: 9999,
}))

function updatePosition() {
  if (!rootEl.value) return
  const r = rootEl.value.getBoundingClientRect()
  panelPos.value = { top: r.bottom + 6, left: r.left, width: r.width }
}

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) {
    query.value = ''
    nextTick(updatePosition)
  }
}

function pick(opt) {
  emit('update:modelValue', opt.value)
  emit('change', opt)
  open.value = false
}

function onOutside(e) {
  if (!open.value) return
  if (rootEl.value?.contains(e.target)) return
  if (panelEl.value?.contains(e.target)) return
  open.value = false
}

function onScrollOrResize() {
  if (open.value) updatePosition()
}

onMounted(() => {
  document.addEventListener('mousedown', onOutside)
  window.addEventListener('scroll', onScrollOrResize, true)
  window.addEventListener('resize', onScrollOrResize)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutside)
  window.removeEventListener('scroll', onScrollOrResize, true)
  window.removeEventListener('resize', onScrollOrResize)
})

watch(() => props.disabled, (v) => { if (v) open.value = false })
</script>

<style scoped>
.au-select-wrap {
  position: relative;
  display: inline-block;
  width: 100%;
  font-family: var(--au-font-sans);
}

.au-select-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  font-size: 13px;
  color: var(--au-text-primary);
  background: var(--au-bg-surface);
  border: 1.5px solid transparent;
  border-radius: 16px;
  cursor: pointer;
  background-image:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)),
    var(--au-grad-border);
  background-origin: border-box;
  background-clip: padding-box, border-box;
  transition: box-shadow var(--au-dur-base) var(--au-ease),
              transform var(--au-dur-fast) var(--au-ease);
}
.au-select-trigger:hover {
  background-image:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)),
    var(--au-grad-border-strong);
  box-shadow: var(--au-shadow-sm);
}
.au-select-wrap.is-open .au-select-trigger {
  background-image:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)),
    var(--au-grad-border-strong);
  box-shadow: var(--au-shadow-glow);
}
.au-select-wrap.is-disabled .au-select-trigger {
  cursor: not-allowed;
  opacity: 0.55;
}

.au-select-label {
  flex: 1;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-feature-settings: var(--au-font-feat);
}
.au-select-label.placeholder {
  color: var(--au-text-tertiary);
}

.au-select-caret {
  display: inline-flex;
  color: var(--au-text-secondary);
  transition: transform var(--au-dur-base) var(--au-ease);
}
.au-select-caret.open {
  transform: rotate(180deg);
}

</style>

<!-- Non-scoped: panel is teleported to <body>, scoped styles wouldn't apply -->
<style>
.au-select-panel {
  background:
    linear-gradient(var(--au-bg-surface, #fff), var(--au-bg-surface, #fff)) padding-box,
    var(--au-grad-border, linear-gradient(180deg, #DADCE0, #DADCE0)) border-box;
  border: 1.5px solid transparent;
  border-radius: 16px;
  box-shadow: var(--au-shadow-lg, 0 12px 32px rgba(60, 64, 67, 0.10));
  overflow: hidden;
  font-family: 'Noto Sans SC', 'Open Sans', sans-serif;
}

.au-select-search {
  padding: 8px;
  border-bottom: 1px solid var(--au-border-subtle, #E5E7EB);
  background: var(--au-bg-subtle, #F8FAFC);
}
.au-select-search-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--au-text-primary, #0F172A);
  background: var(--au-bg-surface, #fff);
  border: 1px solid var(--au-border-base, #DADCE0);
  border-radius: 16px;
  outline: none;
  transition: border-color 150ms ease, box-shadow 150ms ease;
}
.au-select-search-input:focus {
  border-color: var(--au-border-focus, #5F6368);
  box-shadow: var(--au-shadow-glow, 0 0 0 3px rgba(60, 64, 67, 0.10));
}

.au-select-options {
  max-height: 240px;
  overflow-y: auto;
  padding: 4px;
}

.au-select-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  font-size: 13px;
  color: var(--au-text-primary, #3C4043);
  border-radius: 16px;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}
.au-select-option:hover {
  background: var(--au-bg-hover, #E8EAED);
  color: var(--au-text-strong, #202124);
}
.au-select-option.active {
  background: var(--au-info-soft, #F1F3F4);
  color: var(--au-text-strong, #202124);
  font-weight: 600;
}
.au-select-check {
  color: var(--au-text-strong, #202124);
  flex-shrink: 0;
}

.au-select-empty {
  padding: 14px;
  text-align: center;
  color: var(--au-text-tertiary, #94A3B8);
  font-size: 12px;
}

/* 弹出动画 */
.au-pop-enter-active, .au-pop-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}
.au-pop-enter-from, .au-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>
