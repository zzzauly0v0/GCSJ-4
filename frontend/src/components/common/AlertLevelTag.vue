<template>
  <el-tag
    :style="{ backgroundColor: meta.color, color: '#fff', border: 'none' }"
    size="small"
  >
    {{ meta.label }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { levelMeta } from '@/utils/format'

const props = defineProps({
  level: { type: Number, required: true }
})
const meta = computed(() => levelMeta(props.level))
</script>
