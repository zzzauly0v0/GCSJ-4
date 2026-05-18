<template>
  <div class="gcsj-page">
    <el-card class="gcsj-card">
      <div class="header">
        <span class="title">应急预案</span>
        <el-button type="primary" @click="onAdd">新增预案</el-button>
      </div>
      <el-table :data="rows" border size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="code" label="编码" width="180" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="disasterType" label="灾种" width="120" />
        <el-table-column label="等级" width="100">
          <template #default="{ row }">
            <AlertLevelTag v-if="row.level" :level="row.level" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
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

    <el-dialog v-model="dlg" :title="form.id ? '编辑预案' : '新增预案'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.code" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="灾种"><el-input v-model="form.disasterType" /></el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">蓝</el-radio>
            <el-radio :value="2">黄</el-radio>
            <el-radio :value="3">橙</el-radio>
            <el-radio :value="4">红</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="6" /></el-form-item>
        <el-form-item label="附件URL"><el-input v-model="form.fileUrl" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
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
import { apiPlanList, apiPlanCreate, apiPlanUpdate, apiPlanDelete } from '@/api/plan'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'

const rows = ref([])
const dlg = ref(false)
const form = reactive({ id: null, code: '', name: '', disasterType: '', level: 2, content: '', fileUrl: '', enabled: true })

async function load() { rows.value = await apiPlanList() }
function reset() { Object.assign(form, { id: null, code: '', name: '', disasterType: '', level: 2, content: '', fileUrl: '', enabled: true }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  if (form.id) await apiPlanUpdate(form.id, form)
  else await apiPlanCreate(form)
  ElMessage.success('已保存'); dlg.value = false; load()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除预案 [${row.name}]?`, '确认')
  await apiPlanDelete(row.id); ElMessage.success('已删除'); load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.header { display: flex; justify-content: space-between; margin-bottom: 12px;
  .title { font-size: 16px; font-weight: 600; }
}
</style>
