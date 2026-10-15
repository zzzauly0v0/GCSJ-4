<template>
  <div class="au-card" :class="{ 'au-card-grad': gradientBorder }">
    <div v-if="$slots.header || title" class="au-card-head">
      <div class="au-card-title-wrap">
        <span v-if="dot" class="au-card-dot" :style="{ background: dotColor }" />
        <slot name="header">
          <h3 class="au-card-title">{{ title }}</h3>
          <span v-if="subtitle" class="au-card-sub">{{ subtitle }}</span>
        </slot>
      </div>
      <div class="au-card-extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="au-card-body" :class="{ 'au-card-body-flat': flat }">
      <slot />
    </div>
    <div v-if="$slots.footer" class="au-card-foot">
      <slot name="footer" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: String,
  subtitle: String,
  gradientBorder: { type: Boolean, default: false },
  flat: { type: Boolean, default: false },
  dot: { type: Boolean, default: false },
  dotColor: { type: String, default: 'var(--au-grad-primary)' },
})
</script>

<style scoped>
.au-card {
  position: relative;
  background: var(--au-bg-surface);
  border: 1px solid var(--au-border-subtle);
  border-radius: var(--au-radius-lg);
  box-shadow: var(--au-shadow-sm);
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  transition: box-shadow var(--au-dur-base) var(--au-ease),
              transform var(--au-dur-base) var(--au-ease);
}
.au-card:hover {
  box-shadow: var(--au-shadow-md);
}

.au-card-grad {
  border: none;
  background:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)) padding-box,
    var(--au-grad-border) border-box;
  border: 1.5px solid transparent;
}
.au-card-grad:hover {
  background:
    linear-gradient(var(--au-bg-surface), var(--au-bg-surface)) padding-box,
    var(--au-grad-border-strong) border-box;
  box-shadow: var(--au-shadow-blue);
}

.au-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--au-border-subtle);
  flex-shrink: 0;
}
.au-card-title-wrap {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}
.au-card-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  flex-shrink: 0;
  align-self: center;
  box-shadow: 0 0 0 3px rgba(99,102,241,0.10);
}
.au-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--au-text-strong);
  margin: 0;
  letter-spacing: -0.01em;
}
.au-card-sub {
  font-size: 12px;
  color: var(--au-text-tertiary);
  font-weight: 400;
}
.au-card-extra {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.au-card-body {
  padding: 16px 18px;
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.au-card-body-flat {
  padding: 0;
  overflow: hidden;
}

.au-card-foot {
  padding: 10px 18px;
  border-top: 1px solid var(--au-border-subtle);
  font-size: 12px;
  color: var(--au-text-tertiary);
  flex-shrink: 0;
}
</style>
