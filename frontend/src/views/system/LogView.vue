<template>
  <div class="page">
    <!-- 顶部筛选 -->
    <div class="gcsj-card toolbar">
      <div class="toolbar-left">
        <el-input
          v-model="filter.username"
          placeholder="用户名"
          clearable
          :prefix-icon="User"
          style="width: 160px"
          @keyup.enter="loadPage"
        />
        <el-select v-model="filter.method" placeholder="方法" clearable style="width: 120px" @change="loadPage">
          <el-option label="GET" value="GET" />
          <el-option label="POST" value="POST" />
          <el-option label="PUT" value="PUT" />
          <el-option label="DELETE" value="DELETE" />
        </el-select>
        <el-input
          v-model="filter.uri"
          placeholder="URI 包含"
          clearable
          :prefix-icon="Link"
          style="width: 220px"
          @keyup.enter="loadPage"
        />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="起"
          end-placeholder="止"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          style="width: 360px"
          @change="loadPage"
        />
        <el-button type="primary" :icon="Search" @click="loadPage">查询</el-button>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="onReset">重置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="gcsj-card">
      <el-table :data="rows" v-loading="loading" stripe height="calc(100vh - 220px)">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="用户" width="130">
          <template #default="{ row }">
            <span v-if="row.username" class="usr">
              <el-avatar :size="22" class="avatar">{{ row.username.slice(0, 1).toUpperCase() }}</el-avatar>
              {{ row.username }}
            </span>
            <span v-else class="text-3 fs-12">匿名</span>
          </template>
        </el-table-column>
        <el-table-column label="方法" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="methodType(row.method)" effect="plain">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uri" label="URI" min-width="240" show-overflow-tooltip />
        <el-table-column label="模块·方法" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="mod">{{ row.module }}</span>
            <span class="dot">·</span>
            <span class="act">{{ row.action }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <span class="status-pill" :class="row.resultCode === 0 ? 'on' : 'off'">
              <span class="led" />{{ row.resultCode === 0 ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时" width="100" align="right">
          <template #default="{ row }">
            <span :class="costClass(row.costMs)">{{ row.costMs }} ms</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :page-sizes="[20, 50, 100]"
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          @current-change="loadPage"
          @size-change="loadPage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { Search, Refresh, User, Link } from '@element-plus/icons-vue'
import { apiLogPage } from '@/api/log'
import { formatDateTime } from '@/utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const dateRange = ref([])

const filter = reactive({
  username: '',
  method: '',
  uri: '',
  page: 1,
  size: 20
})

const fromTo = computed(() => ({
  from: dateRange.value?.[0] || undefined,
  to: dateRange.value?.[1] || undefined
}))

async function loadPage() {
  loading.value = true
  try {
    const params = {
      username: filter.username || undefined,
      method: filter.method || undefined,
      uri: filter.uri || undefined,
      from: fromTo.value.from,
      to: fromTo.value.to,
      page: filter.page,
      size: filter.size
    }
    const data = await apiLogPage(params)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function onReset() {
  filter.username = ''
  filter.method = ''
  filter.uri = ''
  dateRange.value = []
  filter.page = 1
  loadPage()
}

function methodType(m) {
  return ({ GET: 'info', POST: 'success', PUT: 'warning', DELETE: 'danger' })[m] || ''
}
function costClass(ms) {
  if (ms == null) return ''
  if (ms < 100) return 'cost-fast'
  if (ms < 500) return 'cost-mid'
  return 'cost-slow'
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.page {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  flex-wrap: wrap;
  gap: 8px;
  .toolbar-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  .toolbar-right { display: flex; gap: 8px; }
}

.usr {
  display: inline-flex; align-items: center; gap: 6px;
  font-size: 13px;
  .avatar { background: var(--primary); color: #fff; font-size: 11px; }
}
.mod { color: var(--text-2); font-size: 12px; }
.dot { color: var(--text-3); margin: 0 4px; }
.act { color: var(--primary-deep); font-weight: 600; font-size: 12px; }

.status-pill {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; padding: 2px 8px; border-radius: 999px;
  .led { width: 6px; height: 6px; border-radius: 50%; }
  &.on  { background: rgba(34,197,94,0.1); color: #16A34A; .led { background: #22C55E; box-shadow: 0 0 6px #22C55E; } }
  &.off { background: rgba(239,68,68,0.1); color: #DC2626; .led { background: #EF4444; box-shadow: 0 0 6px #EF4444; } }
}

.cost-fast { color: #16A34A; font-family: 'SF Mono', monospace; font-size: 12px; }
.cost-mid  { color: #D97706; font-family: 'SF Mono', monospace; font-size: 12px; }
.cost-slow { color: #DC2626; font-family: 'SF Mono', monospace; font-size: 12px; font-weight: 600; }

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px;
}

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .page { padding: 8px; gap: 8px; }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    .toolbar-left, .toolbar-right { width: 100%; }
    .toolbar-right { justify-content: flex-end; }
  }
  .pager { justify-content: center; padding: 10px; }
}
</style>
