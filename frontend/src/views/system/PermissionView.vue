<template>
  <div class="gcsj-page">
    <el-card class="gcsj-card">
      <div class="title">权限树 (只读)</div>
      <el-tree :data="data" :props="{ label: 'name', children: 'children' }" default-expand-all />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiPermissionTree } from '@/api/permission'

const data = ref([])
onMounted(async () => { data.value = await apiPermissionTree() })
</script>

<style scoped lang="scss">
.title { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
</style>
