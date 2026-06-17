<template>
  <div class="page">
    <div class="gcsj-card toolbar">
      <div class="toolbar-left">
        <el-input v-model="filter.keyword" placeholder="搜索用户名/姓名/手机号" clearable :prefix-icon="Search" style="width: 280px" @change="loadPage" />
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="loadPage" />
        <el-button type="primary" :icon="Plus" @click="onAdd">新增用户</el-button>
      </div>
    </div>

    <div class="gcsj-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="用户" min-width="220">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" class="avatar">{{ (row.realName || row.username).slice(0, 1).toUpperCase() }}</el-avatar>
              <div>
                <div class="u-name">{{ row.realName || row.username }}</div>
                <div class="u-account">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="email" label="邮箱" min-width="200" />
        <el-table-column label="角色" width="220">
          <template #default="{ row }">
            <el-tag v-for="r in row.roleCodes" :key="r" size="small" effect="plain" style="margin-right: 4px">{{ r }}</el-tag>
            <span v-if="!row.roleCodes?.length" class="text-3 fs-12">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-pill" :class="row.status === 1 ? 'on' : 'off'">
              <span class="led" />{{ row.status === 1 ? '启用' : '停用' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="onEdit(row)">编辑</el-button>
            <el-button size="small" link type="danger" @click="onDel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadPage"
          @size-change="loadPage"
        />
      </div>
    </div>

    <el-dialog v-model="dlg" :title="form.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="用户名"><el-input v-model="form.username" :disabled="!!form.id" /></el-form-item>
        <el-form-item v-if="!form.id" label="密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item v-if="form.id" label="状态">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
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
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { apiUserPage, apiUserCreate, apiUserUpdate, apiUserDelete } from '@/api/user'
import { apiRoleList } from '@/api/role'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const roles = ref([])
const filter = reactive({ keyword: '', page: 1, size: 10 })
const dlg = ref(false)
const form = reactive({ id: null, username: '', password: '', realName: '', phone: '', email: '', status: 1, roleIds: [] })

async function loadPage() {
  loading.value = true
  try {
    const data = await apiUserPage(filter)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}
function reset() { Object.assign(form, { id: null, username: '', password: '', realName: '', phone: '', email: '', status: 1, roleIds: [] }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) {
  reset()
  Object.assign(form, row)
  if (!form.roleIds) form.roleIds = []
  dlg.value = true
}
async function onSave() {
  if (form.id) await apiUserUpdate(form.id, form)
  else await apiUserCreate(form)
  ElMessage.success('已保存')
  dlg.value = false
  loadPage()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除 [${row.username}]?`, '确认操作', { type: 'warning' })
  await apiUserDelete(row.id)
  ElMessage.success('已删除')
  loadPage()
}

onMounted(async () => { roles.value = await apiRoleList(); loadPage() })
</script>

<style scoped lang="scss">
.page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; justify-content: space-between; padding: 14px 18px; }
.toolbar-left, .toolbar-right { display: flex; gap: 8px; }

.user-cell { display: flex; align-items: center; gap: 10px;
  .avatar { background: linear-gradient(135deg, #38BDF8, #6366F1); color: #fff; font-weight: 600; }
  .u-name { font-size: 14px; color: var(--text-1); font-weight: 600; line-height: 1.2; }
  .u-account { font-size: 11px; color: var(--text-3); margin-top: 2px; }
}
.status-pill {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 2px 10px; border-radius: 10px;
  .led { width: 6px; height: 6px; border-radius: 50%; }
  &.on  { background: rgba(34,197,94,0.1); color: #16A34A; .led { background: #22C55E; box-shadow: 0 0 6px #22C55E; } }
  &.off { background: rgba(239,68,68,0.1); color: #DC2626; .led { background: #EF4444; } }
}
.pager { padding: 14px; display: flex; justify-content: flex-end; }

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .page { padding: 10px; gap: 10px; }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    padding: 12px;
  }
  .toolbar-left, .toolbar-right { width: 100%; flex-wrap: wrap; }
  .toolbar-right { justify-content: flex-end; }
  .pager { justify-content: center; padding: 10px; }
}
</style>
