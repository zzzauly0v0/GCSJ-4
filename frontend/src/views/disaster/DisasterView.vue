<template>
  <div class="page">
    <!-- 工具栏 -->
    <div class="gcsj-card toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="filter.keyword"
          placeholder="搜索灾害事件"
          :prefix-icon="Search"
          clearable
          style="width: 240px"
          @change="loadPage"
        />
        <el-select v-model="filter.type" placeholder="灾种" clearable style="width: 140px" @change="loadPage">
          <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="filter.level" placeholder="等级" clearable style="width: 120px" @change="loadPage">
          <el-option label="蓝色" :value="1" />
          <el-option label="黄色" :value="2" />
          <el-option label="橙色" :value="3" />
          <el-option label="红色" :value="4" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="loadPage" />
        <el-button type="primary" :icon="Plus" @click="onAdd">新建灾害事件</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="gcsj-card">
      <el-table :data="rows" v-loading="loading" stripe size="default">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="灾害事件" min-width="280">
          <template #default="{ row }">
            <div class="row-title">
              <div class="title-name">{{ row.title }}</div>
              <div class="title-desc">{{ row.description || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="灾种" width="120">
          <template #default="{ row }">
            <span class="type-tag">{{ typeLabel(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="100" align="center">
          <template #default="{ row }">
            <AlertLevelTag :level="row.level" />
          </template>
        </el-table-column>
        <el-table-column label="位置" width="180">
          <template #default="{ row }">
            <span class="num-mono fs-12 text-2">
              {{ row.longitude?.toFixed(3) }}, {{ row.latitude?.toFixed(3) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 1" type="warning" size="small">进行中</el-tag>
            <el-tag v-else type="info" size="small">已结束</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadPage"
          @size-change="loadPage"
        />
      </div>
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dlg" title="新建灾害事件" width="540px">
      <el-form :model="form" label-width="100px" size="default">
        <el-form-item label="标题"><el-input v-model="form.title" placeholder="如:某市暴雨引发滑坡" /></el-form-item>
        <el-form-item label="灾种">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="form.level">
            <el-radio-button :value="1">蓝色</el-radio-button>
            <el-radio-button :value="2">黄色</el-radio-button>
            <el-radio-button :value="3">橙色</el-radio-button>
            <el-radio-button :value="4">红色</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="经度"><el-input-number v-model="form.longitude" :precision="6" style="width: 100%" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="form.latitude" :precision="6" style="width: 100%" /></el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="form.occurredAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="详细描述..." />
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
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { apiDisasterPage, apiDisasterCreate } from '@/api/disaster'
import { formatDateTime } from '@/utils/format'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const filter = reactive({ keyword: '', type: null, level: null, page: 1, size: 10 })

const dlg = ref(false)
const form = reactive({
  title: '', type: 'landslide', level: 2,
  longitude: 104.0, latitude: 35.0,
  occurredAt: new Date().toISOString().slice(0, 19) + 'Z',
  description: ''
})
const types = [
  { label: '滑坡', value: 'landslide' },
  { label: '泥石流', value: 'debris_flow' },
  { label: '崩塌', value: 'collapse' },
  { label: '暴雨', value: 'rainstorm' }
]
function typeLabel(v) { return types.find(t => t.value === v)?.label || v }

async function loadPage() {
  loading.value = true
  try {
    const data = await apiDisasterPage(filter)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}

function onAdd() { dlg.value = true }
async function onSave() {
  await apiDisasterCreate(form)
  ElMessage.success('已新增')
  dlg.value = false
  loadPage()
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
}
.toolbar-left { display: flex; gap: 10px; align-items: center; }
.toolbar-right { display: flex; gap: 8px; }

.row-title {
  .title-name { font-size: 14px; color: var(--text-1); font-weight: 600; }
  .title-desc { font-size: 12px; color: var(--text-3); margin-top: 2px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px;
  }
}
.type-tag {
  font-size: 12px; padding: 2px 8px; border-radius: 10px;
  background: rgba(37, 99, 235, 0.08); color: var(--primary);
}
.pager { padding: 14px; display: flex; justify-content: flex-end; }
</style>
