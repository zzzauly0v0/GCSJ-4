<template>
  <div class="gcsj-page">
    <el-card class="gcsj-card">
      <div class="header">
        <span class="title">预警规则</span>
        <el-button type="primary" @click="onAdd">新增规则</el-button>
      </div>
      <el-table :data="rows" border size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="indicator" label="指标" />
        <el-table-column prop="operator" label="运算符" width="100" />
        <el-table-column prop="threshold" label="阈值" width="100" />
        <el-table-column label="等级" width="100">
          <template #default="{ row }"><AlertLevelTag :level="row.level" /></template>
        </el-table-column>
        <el-table-column prop="disasterType" label="灾种" width="120" />
        <el-table-column label="启用" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.enabled" type="success" size="small">启用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" @click="onEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="onDel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dlg" :title="form.id ? '编辑规则' : '新增规则'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="指标">
          <el-select v-model="form.indicator">
            <el-option label="降雨量(mm/h)" value="RAINFALL_HOURLY" />
            <el-option label="位移(mm/24h)" value="DISPLACEMENT_24H" />
            <el-option label="土壤含水率(%)" value="SOIL_MOISTURE" />
          </el-select>
        </el-form-item>
        <el-form-item label="运算符">
          <el-select v-model="form.operator">
            <el-option v-for="op in ['>', '>=', '<', '<=', '==']" :key="op" :label="op" :value="op" />
          </el-select>
        </el-form-item>
        <el-form-item label="阈值"><el-input-number v-model="form.threshold" :precision="2" /></el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="form.level">
            <el-radio :value="1">蓝</el-radio>
            <el-radio :value="2">黄</el-radio>
            <el-radio :value="3">橙</el-radio>
            <el-radio :value="4">红</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="灾种"><el-input v-model="form.disasterType" /></el-form-item>
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
import { apiAlertRuleList, apiAlertRuleCreate, apiAlertRuleUpdate, apiAlertRuleDelete } from '@/api/alert'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'

const rows = ref([])
const dlg = ref(false)
const form = reactive({
  id: null, name: '', indicator: 'RAINFALL_HOURLY',
  operator: '>=', threshold: 50, level: 2, disasterType: '',
  enabled: true, description: ''
})

async function load() {
  rows.value = await apiAlertRuleList()
}

function reset() {
  form.id = null; form.name = ''
  form.indicator = 'RAINFALL_HOURLY'; form.operator = '>='
  form.threshold = 50; form.level = 2; form.disasterType = ''
  form.enabled = true; form.description = ''
}

function onAdd() { reset(); dlg.value = true }
function onEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  if (form.id) await apiAlertRuleUpdate(form.id, form)
  else await apiAlertRuleCreate(form)
  ElMessage.success('已保存')
  dlg.value = false
  load()
}
async function onDel(row) {
  await ElMessageBox.confirm(`确定删除 [${row.name}]?`, '确认')
  await apiAlertRuleDelete(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.header { display: flex; justify-content: space-between; margin-bottom: 12px;
  .title { font-size: 16px; font-weight: 600; }
}
</style>
