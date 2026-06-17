<template>
  <div class="dict-page">
    <!-- 左侧: 字典类型 -->
    <aside class="gcsj-card type-pane">
      <div class="pane-head">
        <span class="title">字典类型</span>
        <el-button size="small" :icon="Plus" link type="primary" @click="onAdd(true)">新类型</el-button>
      </div>
      <ul class="type-list">
        <li :class="{ active: !currentType }" @click="currentType = ''">
          <el-icon><Menu /></el-icon><span>全部 ({{ allTotal }})</span>
        </li>
        <li
          v-for="t in types"
          :key="t"
          :class="{ active: currentType === t }"
          @click="currentType = t"
        >
          <el-icon><Collection /></el-icon><span>{{ t }}</span>
        </li>
      </ul>
    </aside>

    <!-- 右侧: 字典项列表 -->
    <section class="item-pane">
      <div class="gcsj-card toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="keyword"
            placeholder="搜索 item_code / 显示值"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
          <span v-if="currentType" class="cur-type">类型: <b>{{ currentType }}</b></span>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadAll" />
          <el-button type="primary" :icon="Plus" @click="onAdd(false)">新增字典项</el-button>
        </div>
      </div>

      <div class="gcsj-card">
        <el-table :data="filteredRows" v-loading="loading" stripe height="calc(100vh - 220px)">
          <el-table-column prop="id" label="ID" width="70" align="center" />
          <el-table-column prop="typeCode" label="字典类型" width="160">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.typeCode }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="itemCode" label="项编码" width="180" />
          <el-table-column prop="itemValue" label="显示值" min-width="180" />
          <el-table-column prop="sort" label="排序" width="80" align="center" />
          <el-table-column prop="description" label="备注" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="onEdit(row)">编辑</el-button>
              <el-button size="small" link type="danger" @click="onDel(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该类型下暂无字典项" />
          </template>
        </el-table>
      </div>
    </section>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dlg" :title="editing ? '编辑字典项' : '新增字典项'" width="520px" align-center>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="字典类型" prop="typeCode">
          <el-input v-model="form.typeCode" placeholder="如 disaster_type" :disabled="editing" />
          <div class="form-hint">英文/数字/下划线, 同类型下 item_code 唯一</div>
        </el-form-item>
        <el-form-item label="项编码" prop="itemCode">
          <el-input v-model="form.itemCode" placeholder="如 landslide" />
        </el-form-item>
        <el-form-item label="显示值" prop="itemValue">
          <el-input v-model="form.itemValue" placeholder="如 滑坡" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="256" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Menu, Collection } from '@element-plus/icons-vue'
import {
  apiDictionaryListAll,
  apiDictionaryAdd,
  apiDictionaryUpdate,
  apiDictionaryDelete
} from '@/api/dictionary'

const rows = ref([])
const loading = ref(false)
const currentType = ref('')
const keyword = ref('')

const types = computed(() => [...new Set(rows.value.map(r => r.typeCode))].sort())
const allTotal = computed(() => rows.value.length)

const filteredRows = computed(() => {
  let list = rows.value
  if (currentType.value) {
    list = list.filter(r => r.typeCode === currentType.value)
  }
  if (keyword.value) {
    const k = keyword.value.toLowerCase()
    list = list.filter(r =>
      r.itemCode?.toLowerCase().includes(k) ||
      r.itemValue?.toLowerCase().includes(k)
    )
  }
  return list
})

async function loadAll() {
  loading.value = true
  try {
    rows.value = await apiDictionaryListAll() || []
  } finally {
    loading.value = false
  }
}

const dlg = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref()
const form = reactive({
  id: null,
  typeCode: '',
  itemCode: '',
  itemValue: '',
  sort: 0,
  description: ''
})
const rules = {
  typeCode: [
    { required: true, message: '请输入字典类型', trigger: 'blur' },
    { pattern: /^[A-Za-z][A-Za-z0-9_]*$/, message: '字母开头, 只允许字母数字下划线', trigger: 'blur' }
  ],
  itemCode: [
    { required: true, message: '请输入项编码', trigger: 'blur' },
    { max: 64, message: '不超过 64 字符', trigger: 'blur' }
  ],
  itemValue: [
    { required: true, message: '请输入显示值', trigger: 'blur' },
    { max: 128, message: '不超过 128 字符', trigger: 'blur' }
  ]
}

function onAdd(isNewType) {
  editing.value = false
  Object.assign(form, {
    id: null,
    typeCode: isNewType ? '' : (currentType.value || ''),
    itemCode: '',
    itemValue: '',
    sort: 0,
    description: ''
  })
  dlg.value = true
}

function onEdit(row) {
  editing.value = true
  Object.assign(form, {
    id: row.id,
    typeCode: row.typeCode,
    itemCode: row.itemCode,
    itemValue: row.itemValue,
    sort: row.sort ?? 0,
    description: row.description || ''
  })
  dlg.value = true
}

async function onSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      typeCode: form.typeCode.trim(),
      itemCode: form.itemCode.trim(),
      itemValue: form.itemValue.trim(),
      sort: form.sort ?? 0,
      description: form.description?.trim() || null
    }
    if (editing.value) {
      await apiDictionaryUpdate(form.id, payload)
      ElMessage.success('更新成功')
    } else {
      await apiDictionaryAdd(payload)
      ElMessage.success('新增成功')
    }
    dlg.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function onDel(row) {
  await ElMessageBox.confirm(
    `确定删除字典项 [${row.typeCode} / ${row.itemCode}] 吗?`,
    '删除字典项',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  ).catch(() => null)
  await apiDictionaryDelete(row.id)
  ElMessage.success('已删除')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.dict-page {
  display: flex;
  gap: 12px;
  padding: 12px;
  height: calc(100vh - 56px);
  box-sizing: border-box;
}

.type-pane {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 12px;
  .pane-head {
    display: flex; align-items: center; justify-content: space-between;
    padding-bottom: 10px;
    border-bottom: 1px solid var(--border-soft);
    margin-bottom: 8px;
    .title { font-size: 14px; font-weight: 600; color: var(--text-1); }
  }
  .type-list {
    list-style: none; padding: 0; margin: 0;
    overflow-y: auto;
    flex: 1;
    li {
      display: flex; align-items: center; gap: 8px;
      padding: 8px 12px;
      margin: 2px 0;
      border-radius: 6px;
      cursor: pointer;
      font-size: 13px;
      color: var(--text-2);
      transition: background 0.15s;
      .el-icon { font-size: 14px; }
      span { flex: 1; word-break: break-all; }
      &:hover { background: var(--bg-soft); }
      &.active {
        background: var(--primary-soft);
        color: var(--primary-deep);
        font-weight: 600;
      }
    }
  }
}

.item-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  .toolbar-left { display: flex; align-items: center; gap: 12px;
    .cur-type { font-size: 12px; color: var(--text-3);
      b { color: var(--primary-deep); margin-left: 2px; }
    }
  }
  .toolbar-right { display: flex; gap: 8px; }
}

.form-hint {
  font-size: 11px; color: var(--text-3); line-height: 1.4; margin-top: 2px;
}

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .dict-page {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 56px);
    padding: 8px;
    gap: 8px;
  }
  .type-pane {
    width: 100%;
    max-height: 220px;
    .type-list { max-height: 160px; }
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    padding: 10px;
    .toolbar-left, .toolbar-right { width: 100%; }
    .toolbar-right { justify-content: flex-end; }
  }
}
</style>
