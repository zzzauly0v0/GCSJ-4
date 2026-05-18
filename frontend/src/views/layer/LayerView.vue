<template>
  <div class="gcsj-page">
    <el-card class="gcsj-card">
      <div class="header">
        <span class="title">空间图层</span>
        <el-button type="primary" @click="onAdd">新增图层</el-button>
      </div>
      <el-table :data="rows" border size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="code" label="编码" width="160" />
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="workspace" label="GeoServer 工作区" width="160" />
        <el-table-column prop="layerName" label="GeoServer 图层名" width="200" />
        <el-table-column label="可见" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.visible" @change="onToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="onEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑图层' : '新增图层'" width="520px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option v-for="t in ['vector','raster','wms','wmts','xyz']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源 URL"><el-input v-model="form.sourceUrl" /></el-form-item>
        <el-form-item label="GeoServer 工作区"><el-input v-model="form.workspace" /></el-form-item>
        <el-form-item label="GeoServer 图层名"><el-input v-model="form.layerName" /></el-form-item>
        <el-form-item label="样式"><el-input v-model="form.style" /></el-form-item>
        <el-form-item label="层级"><el-input-number v-model="form.zIndex" /></el-form-item>
        <el-form-item label="可见"><el-switch v-model="form.visible" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiLayerList, apiLayerCreate, apiLayerUpdate, apiLayerDelete } from '@/api/layer'

const rows = ref([])
const dlg = ref(false)
const form = reactive({ id: null, name: '', code: '', type: 'vector', sourceUrl: '', workspace: '', layerName: '', style: '', visible: true, zIndex: 0 })

async function load() { rows.value = await apiLayerList() }
function reset() { Object.assign(form, { id: null, name: '', code: '', type: 'vector', sourceUrl: '', workspace: '', layerName: '', style: '', visible: true, zIndex: 0 }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  if (form.id) await apiLayerUpdate(form.id, form)
  else await apiLayerCreate(form)
  ElMessage.success('已保存'); dlg.value = false; load()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除图层 [${row.name}]?`, '确认')
  await apiLayerDelete(row.id); ElMessage.success('已删除'); load()
}
async function onToggle(row) {
  await apiLayerUpdate(row.id, row); ElMessage.success('已更新')
}

onMounted(load)
</script>

<style scoped lang="scss">
.header { display: flex; justify-content: space-between; margin-bottom: 12px;
  .title { font-size: 16px; font-weight: 600; }
}
</style>
