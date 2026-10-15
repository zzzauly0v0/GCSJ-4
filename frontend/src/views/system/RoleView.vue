<template>
  <div class="page">
    <div class="layout-2col">
      <!-- 左:角色列表 -->
      <div class="gcsj-card role-list">
        <div class="card-head">
          <div class="head-title">
            <el-icon><UserFilled /></el-icon>
            <span>角色列表</span>
            <span class="count">{{ rows.length }}</span>
          </div>
          <el-button type="primary" :icon="Plus" size="small" @click="onAdd">新建</el-button>
        </div>
        <div class="role-items">
          <div
            v-for="r in rows"
            :key="r.id"
            class="role-item"
            :class="{ active: selectedId === r.id }"
            @click="onSelect(r)"
          >
            <div class="role-icon"><el-icon><Avatar /></el-icon></div>
            <div class="role-body">
              <div class="role-name">{{ r.name }}</div>
              <div class="role-code">{{ r.code }}</div>
            </div>
            <div class="role-actions">
              <el-button size="small" link @click.stop="onEdit(r)">编辑</el-button>
              <el-button size="small" link type="danger" @click.stop="onDel(r)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!rows.length" description="暂无角色" :image-size="60" />
        </div>
      </div>

      <!-- 右:权限配置 -->
      <div class="gcsj-card permission-panel">
        <div class="card-head">
          <div class="head-title">
            <el-icon><Lock /></el-icon>
            <span>权限配置</span>
            <span v-if="selectedRole" class="role-tag">{{ selectedRole.name }}</span>
          </div>
        </div>
        <div v-if="selectedRole" class="perm-body">
          <div class="perm-tip">勾选权限并点击底部 <b>保存</b> 按钮</div>
          <el-tree
            ref="treeRef"
            :data="permTree"
            :props="{ label: 'name', children: 'children' }"
            show-checkbox
            node-key="id"
            default-expand-all
            class="perm-tree"
          />
          <div class="perm-actions">
            <el-button type="primary" @click="onSavePerm">保存权限</el-button>
          </div>
        </div>
        <el-empty v-else description="请选择角色" :image-size="80" />
      </div>
    </div>

    <el-dialog v-model="dlg" :title="form.id ? '编辑角色' : '新增角色'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="onSaveRole">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, Avatar, Lock, Plus } from '@element-plus/icons-vue'
import { apiRoleList, apiRoleCreate, apiRoleUpdate, apiRoleDelete } from '@/api/role'
import { apiPermissionTree } from '@/api/permission'

const rows = ref([])
const dlg = ref(false)
const treeRef = ref()
const permTree = ref([])
const selectedId = ref(null)
const selectedRole = ref(null)
const form = reactive({ id: null, name: '', code: '', description: '', permissionIds: [] })

async function load() {
  rows.value = await apiRoleList()
  if (selectedId.value) {
    const found = rows.value.find(r => r.id === selectedId.value)
    if (found) onSelect(found)
  }
}
function reset() { Object.assign(form, { id: null, name: '', code: '', description: '', permissionIds: [] }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) { reset(); Object.assign(form, row); if (!form.permissionIds) form.permissionIds = []; dlg.value = true }
async function onSaveRole() {
  if (form.id) await apiRoleUpdate(form.id, form)
  else await apiRoleCreate(form)
  ElMessage.success('已保存')
  dlg.value = false
  load()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除角色 [${row.name}]?`, '确认操作', { type: 'warning' })
  await apiRoleDelete(row.id)
  ElMessage.success('已删除')
  if (selectedId.value === row.id) { selectedId.value = null; selectedRole.value = null }
  load()
}

function onSelect(r) {
  selectedId.value = r.id
  selectedRole.value = r
  // 用 nextTick 等树渲染好再设置勾选
  setTimeout(() => {
    if (treeRef.value && r.permissionIds) {
      treeRef.value.setCheckedKeys(r.permissionIds || [], false)
    } else if (treeRef.value) {
      treeRef.value.setCheckedKeys([], false)
    }
  }, 50)
}

async function onSavePerm() {
  if (!selectedRole.value) return
  const ids = treeRef.value.getCheckedKeys(false).concat(treeRef.value.getHalfCheckedKeys())
  await apiRoleUpdate(selectedRole.value.id, { ...selectedRole.value, permissionIds: ids })
  ElMessage.success('权限已保存')
  load()
}

onMounted(async () => {
  permTree.value = await apiPermissionTree()
  load()
})
</script>

<style scoped lang="scss">
.page { padding: 16px; }
.layout-2col { display: grid; grid-template-columns: 380px 1fr; gap: 16px; min-height: calc(100vh - 88px); }

.card-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--au-border-subtle);
  .head-title {
    display: flex; align-items: center; gap: 8px;
    font-size: 14px; font-weight: 600; color: var(--au-text-strong);
    .el-icon { color: var(--au-text-strong); }
    .count { background: var(--au-bg-subtle); padding: 0 8px; border-radius: 16px;
      font-size: 11px; color: var(--au-text-secondary); font-family: var(--au-font-num); }
    .role-tag {
      background: var(--au-bg-subtle); color: var(--au-text-strong);
      border: 1px solid var(--au-border-subtle);
      padding: 2px 10px; border-radius: 16px; font-size: 12px;
    }
  }
}

.role-list { display: flex; flex-direction: column; }
.role-items { padding: 8px; flex: 1; overflow-y: auto; }
.role-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: rgb(248, 249, 252); }
  &.active {
    background: var(--au-bg-subtle);
    .role-icon { background: #3C4043; color: #fff; }
  }
  .role-icon {
    width: 36px; height: 36px; border-radius: 16px;
    background: rgb(248, 249, 252);
    color: var(--au-text-strong);
    display: flex; align-items: center; justify-content: center;
    font-size: 18px;
    flex-shrink: 0;
    transition: all 0.15s;
  }
  .role-body { flex: 1; min-width: 0; }
  .role-name { font-size: 13px; font-weight: 600; color: var(--au-text-strong); }
  .role-code { font-size: 11px; color: var(--au-text-secondary); font-family: var(--au-font-num); margin-top: 2px; }
  .role-actions { opacity: 0; transition: opacity 0.15s; }
  &:hover .role-actions { opacity: 1; }
}

.permission-panel { display: flex; flex-direction: column; }
.perm-body { flex: 1; padding: 16px 20px; display: flex; flex-direction: column; }
.perm-tip { font-size: 12px; color: var(--au-text-secondary); margin-bottom: 12px;
  background: rgb(248, 249, 252); padding: 8px 12px; border-radius: 16px;
  border-left: 3px solid var(--au-border-base);
}
.perm-tree { flex: 1; overflow-y: auto; }
.perm-actions { padding-top: 12px; border-top: 1px dashed var(--au-border-subtle); display: flex; justify-content: flex-end; }

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .page { padding: 10px; }
  .layout-2col {
    display: flex;
    flex-direction: column;
    gap: 10px;
    min-height: 0;
  }
  .role-list { max-height: 240px; }
  .role-items { max-height: 200px; }
  .role-item .role-actions { opacity: 1; }
  .perm-body { padding: 12px; }
  .card-head { padding: 12px; }
}
</style>
