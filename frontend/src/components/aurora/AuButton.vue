<template>
  <button
    type="button"
    class="au-btn"
    :class="[`au-btn-${variant}`, `au-btn-${size}`, { 'is-disabled': disabled }]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: { type: String, default: 'primary' },   // primary | ghost | soft
  size: { type: String, default: 'md' },           // sm | md | lg
  disabled: { type: Boolean, default: false },
})
defineEmits(['click'])
</script>

<style scoped>
.au-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-family: var(--au-font-sans);
  font-weight: 600;
  letter-spacing: 0.01em;
  border: none;
  cursor: pointer;
  user-select: none;
  border-radius: var(--au-radius-md);
  transition: transform var(--au-dur-fast) var(--au-ease),
              box-shadow var(--au-dur-base) var(--au-ease),
              filter var(--au-dur-fast) var(--au-ease);
  white-space: nowrap;
}
.au-btn:active { transform: translateY(1px); }
.au-btn.is-disabled { opacity: 0.5; cursor: not-allowed; }

.au-btn-sm { padding: 6px 12px; font-size: 12px; }
.au-btn-md { padding: 8px 16px; font-size: 13px; }
.au-btn-lg { padding: 10px 20px; font-size: 14px; }

.au-btn-primary {
  color: #FFFFFF;
  background: var(--au-grad-primary);
  box-shadow: var(--au-shadow-blue);
}
.au-btn-primary:hover { filter: brightness(1.05); box-shadow: 0 10px 24px rgba(37,99,235,0.22); }

.au-btn-soft {
  color: var(--au-info);
  background: var(--au-info-soft);
}
.au-btn-soft:hover { background: #BFDBFE; }

.au-btn-ghost {
  color: var(--au-info);
  background: var(--au-bg-surface);
  border: 1.5px solid transparent;
  background-image:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)),
    var(--au-grad-border);
  background-origin: border-box;
  background-clip: padding-box, border-box;
}
.au-btn-ghost:hover {
  background-image:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)),
    var(--au-grad-border-strong);
}
</style>
