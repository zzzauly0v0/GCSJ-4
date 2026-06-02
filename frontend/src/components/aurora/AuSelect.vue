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

    <transition name="au-pop">
      <div v-if="open" class="au-select-panel" :style="panelStyle">
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

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

const current = computed(() =>
  props.options.find(o => o.value === props.modelValue)
)

const filtered = computed(() => {
  if (!props.searchable || !query.value) return props.options
  const q = query.value.toLowerCase()
  return props.options.filter(o => String(o.label).toLowerCase().includes(q))
})

const panelStyle = computed(() => ({
  width: typeof props.width === 'number' ? `${props.width}px` : props.width,
}))

function toggle() {
  if (props.disabled) return
  open.value = !open.value
  if (open.value) query.value = ''
}

function pick(opt) {
  emit('update:modelValue', opt.value)
  emit('change', opt)
  open.value = false
}

function onOutside(e) {
  if (!rootEl.value) return
  if (!rootEl.value.contains(e.target)) open.value = false
}

onMounted(() => document.addEventListener('mousedown', onOutside))
onBeforeUnmount(() => document.removeEventListener('mousedown', onOutside))

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
  border-radius: var(--au-radius-md);
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
  color: var(--au-info);
  transition: transform var(--au-dur-base) var(--au-ease);
}
.au-select-caret.open {
  transform: rotate(180deg);
}

.au-select-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 50;
  background:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)) padding-box,
    var(--au-grad-border) border-box;
  border: 1.5px solid transparent;
  border-radius: var(--au-radius-md);
  box-shadow: var(--au-shadow-lg);
  overflow: hidden;
  min-width: 100%;
}

.au-select-search {
  padding: 8px;
  border-bottom: 1px solid var(--au-border-subtle);
  background: var(--au-bg-subtle);
}
.au-select-search-input {
  width: 100%;
  padding: 6px 10px;
  font-size: 12px;
  color: var(--au-text-primary);
  background: var(--au-bg-surface);
  border: 1px solid var(--au-border-base);
  border-radius: var(--au-radius-sm);
  outline: none;
  transition: border-color var(--au-dur-fast) var(--au-ease),
              box-shadow var(--au-dur-fast) var(--au-ease);
}
.au-select-search-input:focus {
  border-color: var(--au-border-focus);
  box-shadow: var(--au-shadow-glow);
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
  color: var(--au-text-primary);
  border-radius: var(--au-radius-sm);
  cursor: pointer;
  transition: background var(--au-dur-fast) var(--au-ease),
              color var(--au-dur-fast) var(--au-ease);
}
.au-select-option:hover {
  background: var(--au-bg-hover);
  color: var(--au-info);
}
.au-select-option.active {
  background: var(--au-info-soft);
  color: var(--au-info);
  font-weight: 600;
}
.au-select-check {
  color: var(--au-info);
  flex-shrink: 0;
}

.au-select-empty {
  padding: 14px;
  text-align: center;
  color: var(--au-text-tertiary);
  font-size: 12px;
}

/* 弹出动画 */
.au-pop-enter-active, .au-pop-leave-active {
  transition: opacity var(--au-dur-base) var(--au-ease),
              transform var(--au-dur-base) var(--au-ease);
}
.au-pop-enter-from, .au-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}
</style>
