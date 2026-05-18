<template>
  <div class="alert-page">
    <!-- ====== 顶部统计 ====== -->
    <div class="stats">
      <div v-for="s in statsData" :key="s.key" class="stat" :style="{'--c': s.color}">
        <div class="stat-icon"><el-icon><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value num-mono">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
        <div class="stat-trend">
          <el-icon><CaretTop /></el-icon>{{ s.trend }}%
        </div>
      </div>
    </div>

    <!-- ====== Tabs + 筛选 ====== -->
    <div class="gcsj-card">
      <div class="tab-bar">
        <div
          v-for="t in tabs"
          :key="t.value"
          class="tab"
          :class="{ active: filter.level === t.value }"
          @click="onTab(t.value)"
        >
          <span v-if="t.color" class="tab-dot" :style="{ background: t.color }" />
          <span>{{ t.label }}</span>
          <span class="tab-count">{{ t.count }}</span>
        </div>

        <div class="tab-actions">
          <el-select v-model="filter.status" placeholder="状态" clearable style="width: 130px" size="default" @change="loadPage">
            <el-option label="全部状态" :value="null" />
            <el-option label="待发送" :value="1" />
            <el-option label="已发送" :value="2" />
            <el-option label="已确认" :value="3" />
            <el-option label="已关闭" :value="4" />
          </el-select>
          <el-button :icon="Refresh" @click="loadPage" circle />
        </div>
      </div>

      <!-- ====== 列表 ====== -->
      <div class="alert-list" v-loading="loading">
        <div v-if="!rows.length && !loading" class="empty">
          <el-empty description="暂无预警记录" />
        </div>
        <div v-for="row in rows" :key="row.id" class="alert-item" :class="`lvl-${row.level}`">
          <div class="lvl-stripe" />
          <div class="alert-icon">
            <el-icon><BellFilled /></el-icon>
            <span class="lvl-label">{{ levelMeta(row.level).label }}</span>
          </div>
          <div class="alert-main">
            <div class="title-row">
              <span class="title">{{ row.title }}</span>
              <span class="code">{{ row.code }}</span>
            </div>
            <div class="content">{{ row.content || '—' }}</div>
            <div class="meta">
              <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatDateTime(row.triggeredAt) }}</span>
              <span v-if="row.longitude != null" class="meta-item">
                <el-icon><Location /></el-icon>{{ row.longitude.toFixed(3) }}, {{ row.latitude.toFixed(3) }}
              </span>
              <span class="meta-item"><el-icon><Promotion /></el-icon>{{ row.channels || 'in_site' }}</span>
            </div>
          </div>
          <div class="alert-status">
            <span class="status-tag" :class="`s-${row.status}`">{{ statusText(row.status) }}</span>
            <div class="actions">
              <el-button size="small" type="primary" :disabled="row.status >= 3" @click="onConfirm(row)">确认</el-button>
              <el-button size="small" :disabled="row.status === 4" @click="onClose(row)">关闭</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pager">
        <el-pagination
          v-model:current-page="filter.page"
          v-model:page-size="filter.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="loadPage"
          @size-change="loadPage"
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  BellFilled, Clock, Location, Promotion, Refresh, CaretTop
} from '@element-plus/icons-vue'
import { apiAlertPage, apiAlertConfirm, apiAlertClose } from '@/api/alert'
import { formatDateTime, levelMeta } from '@/utils/format'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const filter = reactive({ level: null, status: null, page: 1, size: 10 })

const stats = ref({ total: 0, l1: 0, l2: 0, l3: 0, l4: 0 })

const tabs = computed(() => [
  { value: null, label: '全部',     count: stats.value.total, color: null },
  { value: 4,    label: '红色预警', count: stats.value.l4,    color: '#EF4444' },
  { value: 3,    label: '橙色预警', count: stats.value.l3,    color: '#F97316' },
  { value: 2,    label: '黄色预警', count: stats.value.l2,    color: '#FBBF24' },
  { value: 1,    label: '蓝色预警', count: stats.value.l1,    color: '#3B82F6' }
])

const statsData = computed(() => [
  { key: 'total', label: '预警总数',   value: stats.value.total, color: '#3B82F6', trend: 12, icon: 'BellFilled' },
  { key: 'red',   label: '红色预警',   value: stats.value.l4,    color: '#EF4444', trend: 5,  icon: 'Warning' },
  { key: 'orange',label: '橙色预警',   value: stats.value.l3,    color: '#F97316', trend: 8,  icon: 'Warning' },
  { key: 'done',  label: '已处置',     value: 32,                color: '#22C55E', trend: 18, icon: 'Check' }
])

function statusText(s) {
  return ({ 1: '待发送', 2: '已发送', 3: '已确认', 4: '已关闭' })[s] || '-'
}

function onTab(level) {
  filter.level = level
  filter.page = 1
  loadPage()
}

async function loadPage() {
  loading.value = true
  try {
    const data = await apiAlertPage(filter)
    rows.value = data.records || []
    total.value = data.total || 0
    // 更新 stats (无 level 时整体抓取)
    if (filter.level == null && filter.status == null) {
      const all = await apiAlertPage({ page: 1, size: 200 })
      stats.value = {
        total: all.total || 0,
        l1: (all.records || []).filter(r => r.level === 1).length,
        l2: (all.records || []).filter(r => r.level === 2).length,
        l3: (all.records || []).filter(r => r.level === 3).length,
        l4: (all.records || []).filter(r => r.level === 4).length
      }
    }
  } finally { loading.value = false }
}

async function onConfirm(row) {
  await ElMessageBox.confirm(`确认预警「${row.title}」?`, '确认操作', { type: 'warning' })
  await apiAlertConfirm(row.id)
  ElMessage.success('已确认')
  loadPage()
}
async function onClose(row) {
  await ElMessageBox.confirm(`关闭预警「${row.title}」?`, '关闭确认', { type: 'warning' })
  await apiAlertClose(row.id)
  ElMessage.success('已关闭')
  loadPage()
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.alert-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }

/* 顶部统计 */
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat {
  --c: #3B82F6;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  position: relative; overflow: hidden;
  &::before {
    content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--c);
  }
  .stat-icon {
    width: 44px; height: 44px; border-radius: 8px;
    background: color-mix(in srgb, var(--c) 14%, white);
    color: var(--c);
    display: flex; align-items: center; justify-content: center;
    font-size: 22px;
  }
  .stat-body { flex: 1; }
  .stat-value { font-size: 26px; font-weight: 700; color: var(--text-1); line-height: 1; }
  .stat-label { font-size: 12px; color: var(--text-3); margin-top: 4px; }
  .stat-trend {
    font-size: 11px; color: #16A34A; display: flex; align-items: center; gap: 2px;
    padding: 2px 8px; background: rgba(34,197,94,0.1); border-radius: 10px;
  }
}

/* Tab 栏 */
.tab-bar {
  display: flex; align-items: center; gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border);
}
.tab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s;
  &:hover { background: var(--bg); }
  &.active {
    background: rgba(37, 99, 235, 0.08);
    color: var(--primary);
    font-weight: 600;
  }
  .tab-dot { width: 8px; height: 8px; border-radius: 50%; }
  .tab-count {
    font-family: 'DIN Alternate', monospace;
    background: var(--bg);
    color: var(--text-2);
    padding: 0 6px;
    border-radius: 8px;
    font-size: 11px;
    min-width: 20px;
    text-align: center;
  }
  &.active .tab-count { background: rgba(37, 99, 235, 0.15); color: var(--primary); }
}
.tab-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }

/* 列表 */
.alert-list { padding: 12px; min-height: 200px; }
.empty { padding: 40px 0; }

.alert-item {
  display: flex; align-items: stretch; gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-bottom: 10px;
  background: #fff;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
  &:hover {
    border-color: rgba(37, 99, 235, 0.3);
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  }
  .lvl-stripe { width: 3px; border-radius: 2px; flex-shrink: 0; }
  &.lvl-1 .lvl-stripe { background: var(--alert-blue); }
  &.lvl-2 .lvl-stripe { background: var(--alert-yellow); }
  &.lvl-3 .lvl-stripe { background: var(--alert-orange); }
  &.lvl-4 .lvl-stripe { background: var(--alert-red); }
}
.alert-icon {
  width: 60px; flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  .el-icon { font-size: 22px; }
  .lvl-label { font-size: 11px; font-weight: 600; }
}
.lvl-1 .alert-icon { color: var(--alert-blue); }
.lvl-2 .alert-icon { color: var(--alert-yellow); }
.lvl-3 .alert-icon { color: var(--alert-orange); }
.lvl-4 .alert-icon { color: var(--alert-red); }

.alert-main { flex: 1; min-width: 0;
  .title-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px;
    .title { font-size: 14px; font-weight: 600; color: var(--text-1); }
    .code { font-family: 'DIN Alternate', monospace; font-size: 11px; color: var(--text-3);
      padding: 1px 8px; border: 1px dashed var(--border); border-radius: 8px;
    }
  }
  .content { font-size: 12px; color: var(--text-2); margin-bottom: 8px; line-height: 1.5; }
  .meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 11px; color: var(--text-3);
    .meta-item { display: flex; align-items: center; gap: 4px; }
  }
}

.alert-status {
  display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; gap: 8px;
  flex-shrink: 0;
  .status-tag {
    font-size: 11px; padding: 2px 10px; border-radius: 10px; font-weight: 600;
    &.s-1 { background: rgba(239,68,68,0.1);  color: #DC2626; }
    &.s-2 { background: rgba(56,189,248,0.1); color: #0284C7; }
    &.s-3 { background: rgba(34,197,94,0.1);  color: #16A34A; }
    &.s-4 { background: rgba(100,116,139,0.1);color: #475569; }
  }
  .actions { display: flex; gap: 6px; }
}

.pager { padding: 12px 16px; display: flex; justify-content: flex-end; border-top: 1px solid var(--border); }
</style>
