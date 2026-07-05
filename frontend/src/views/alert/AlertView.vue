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
          <el-button type="primary" :icon="MagicStick" @click="genDlg = true">从风险研判生成</el-button>
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
              <span v-if="row.source === 'eval'" class="src-tag">研判</span>
            </div>
            <div class="content">{{ row.content || '—' }}</div>
            <div class="meta">
              <span class="meta-item"><el-icon><Clock /></el-icon>{{ formatDateTime(row.triggeredAt) }}</span>
              <span v-if="row.longitude != null" class="meta-item">
                <el-icon><Location /></el-icon>{{ row.longitude.toFixed(3) }}, {{ row.latitude.toFixed(3) }}
              </span>
              <span class="meta-item"><el-icon><Promotion /></el-icon>{{ (row.channels || []).join(' / ') || 'in_site' }}</span>
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

    <!-- 从风险研判批量生成预警 -->
    <el-dialog v-model="genDlg" title="从风险研判生成预警" width="460px">
      <el-form :model="genForm" label-width="90px" size="default">
        <el-form-item label="年份">
          <el-select v-model="genForm.year" placeholder="年份" clearable style="width: 100%">
            <el-option label="全部年份" :value="null" />
            <el-option v-for="y in YEARS" :key="y" :label="String(y)" :value="y" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低等级">
          <el-select v-model="genForm.minLevel" style="width: 100%">
            <el-option label="蓝色及以上" :value="1" />
            <el-option label="黄色及以上" :value="2" />
            <el-option label="橙色及以上" :value="3" />
            <el-option label="仅红色" :value="4" />
          </el-select>
        </el-form-item>
        <div class="gen-hint">
          扫描历史风险研判结果, 按「站点 + 日期」去重生成预警。重复生成不会产生重复记录。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="genDlg = false">取消</el-button>
        <el-button type="primary" :loading="genLoading" @click="onGenerate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  BellFilled, Clock, Location, Promotion, Refresh, MagicStick
} from '@element-plus/icons-vue'
import { apiAlertConfirm, apiAlertClose, apiAlertPage, apiAlertGenerateFromEval, apiAlertStats } from '@/api/alert'
import { formatDateTime, levelMeta } from '@/utils/format'
import AuTimelineMini from '@/components/aurora/AuTimelineMini.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const filter = reactive({ level: null, status: null, page: 1, size: 10 })

// 从风险研判批量生成
const YEARS = [2020, 2021, 2022, 2023]
const genDlg = ref(false)
const genLoading = ref(false)
const genForm = reactive({ year: null, minLevel: 3 })

const stats = ref({ total: 0, level1: 0, level2: 0, level3: 0, level4: 0, done: 0 })

const tabs = computed(() => [
  { value: null, label: '全部',     count: stats.value.total,  color: null },
  { value: 4,    label: '红色预警', count: stats.value.level4, color: '#D93025' },
  { value: 3,    label: '橙色预警', count: stats.value.level3, color: '#E8710A' },
  { value: 2,    label: '黄色预警', count: stats.value.level2, color: '#F29900' },
  { value: 1,    label: '蓝色预警', count: stats.value.level1, color: '#5F6368' }
])

const statsData = computed(() => [
  { key: 'total',  label: '预警总数', value: stats.value.total,  color: '#3C4043', icon: 'BellFilled' },
  { key: 'red',    label: '红色预警', value: stats.value.level4, color: '#D93025', icon: 'Warning' },
  { key: 'orange', label: '橙色预警', value: stats.value.level3, color: '#E8710A', icon: 'Warning' },
  { key: 'yellow', label: '黄色预警', value: stats.value.level2, color: '#F29900', icon: 'Warning' },
  { key: 'blue',   label: '蓝色预警', value: stats.value.level1, color: '#5F6368', icon: 'Warning' },
  { key: 'done',   label: '已处置',   value: stats.value.done,   color: '#1E8E3E', icon: 'Check' }
])

function statusText(s) {
  return ({ 1: '待发送', 2: '已发送', 3: '已确认', 4: '已关闭' })[s] || '-'
}

function onTab(level) {
  filter.level = level
  filter.page = 1
  loadPage()
}

/**
 * 数据来源: /alerts 真实分页接口
 * 列表按当前筛选分页; 统计卡另取全量按等级计数
 */
async function loadPage() {
  loading.value = true
  try {
    const pageRes = await apiAlertPage({
      level: filter.level ?? undefined,
      status: filter.status ?? undefined,
      page: filter.page,
      size: filter.size,
    })
    rows.value = (pageRes?.records || []).map(a => ({
      id: a.id,
      code: a.code,
      title: a.title,
      content: a.content,
      level: a.level,
      status: a.status,
      source: a.source,
      triggeredAt: a.triggeredAt,
      longitude: a.longitude,
      latitude: a.latitude,
      channels: Array.isArray(a.channels) ? a.channels : (a.channels ? a.channels.split(',') : ['in_site']),
    }))
    total.value = pageRes?.total || 0

    const s = await apiAlertStats()
    stats.value = { total: s.total, level1: s.level1, level2: s.level2, level3: s.level3, level4: s.level4, done: s.done }
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

async function onGenerate() {
  genLoading.value = true
  try {
    const res = await apiAlertGenerateFromEval(genForm.year, genForm.minLevel)
    ElMessage.success(`扫描 ${res?.scanned ?? 0} 条, 新增 ${res?.created ?? 0} 条, 跳过 ${res?.skipped ?? 0} 条`)
    genDlg.value = false
    filter.page = 1
    loadPage()
  } finally { genLoading.value = false }
}

onMounted(loadPage)
</script>

<style scoped lang="scss">
.alert-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }

/* 顶部统计 */
.stats { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; }
.stat {
  --c: #3C4043;
  background: #fff;
  border: 1px solid var(--au-border-subtle);
  border-radius: 16px;
  padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  position: relative; overflow: hidden;
  box-shadow: var(--au-shadow-sm);
  &::before {
    content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 3px; background: var(--c);
  }
  .stat-icon {
    width: 44px; height: 44px; border-radius: 16px;
    background: color-mix(in srgb, var(--c) 10%, white);
    color: var(--c);
    display: flex; align-items: center; justify-content: center;
    font-size: 22px;
  }
  .stat-body { flex: 1; }
  .stat-value { font-size: 26px; font-weight: 700; color: var(--au-text-strong); line-height: 1; }
  .stat-label { font-size: 12px; color: var(--au-text-secondary); margin-top: 4px; }
}

/* Tab 栏 */
.tab-bar {
  display: flex; align-items: center; gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--au-border-subtle);
}
.tab {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  font-size: 13px;
  color: var(--au-text-secondary);
  cursor: pointer;
  border-radius: 16px;
  transition: all 0.15s;
  &:hover { background: rgb(248, 249, 252); color: var(--au-text-strong); }
  &.active {
    background: rgb(248, 249, 252);
    color: var(--au-text-strong);
    font-weight: 600;
  }
  .tab-dot { width: 8px; height: 8px; border-radius: 50%; }
  .tab-count {
    font-family: var(--au-font-num);
    background: var(--au-bg-subtle);
    color: var(--au-text-secondary);
    padding: 0 6px;
    border-radius: 16px;
    font-size: 11px;
    min-width: 20px;
    text-align: center;
  }
  &.active .tab-count { background: var(--au-bg-hover); color: var(--au-text-strong); }
}
.tab-actions { margin-left: auto; display: flex; align-items: center; gap: 8px; }

/* 列表 */
.alert-list { padding: 12px; min-height: 200px; }
.empty { padding: 40px 0; }

.alert-item {
  display: flex; align-items: stretch; gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--au-border-subtle);
  border-radius: 16px;
  margin-bottom: 10px;
  background: #fff;
  transition: all 0.2s;
  position: relative;
  overflow: hidden;
  &:hover {
    border-color: var(--au-border-base);
    box-shadow: 0 2px 8px rgba(60,64,67,0.06);
  }
  .lvl-stripe { width: 3px; border-radius: 16px; flex-shrink: 0; }
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
    .title { font-size: 14px; font-weight: 600; color: var(--au-text-strong); }
    .code { font-family: var(--au-font-num); font-size: 11px; color: var(--au-text-secondary);
      padding: 1px 8px; border: 1px dashed var(--au-border-base); border-radius: 16px;
    }
    .src-tag {
      font-size: 11px; padding: 1px 8px; border-radius: 16px;
      background: rgba(99, 102, 241, 0.10); color: #4F46E5;
      border: 1px solid rgba(99, 102, 241, 0.24);
    }
  }
  .content { font-size: 12px; color: var(--au-text-primary); margin-bottom: 8px; line-height: 1.5; }
  .meta { display: flex; flex-wrap: wrap; gap: 16px; font-size: 11px; color: var(--au-text-secondary);
    .meta-item { display: flex; align-items: center; gap: 4px; }
  }
}

.alert-status {
  display: flex; flex-direction: column; align-items: flex-end; justify-content: space-between; gap: 8px;
  flex-shrink: 0;
  .status-tag {
    font-size: 11px; padding: 2px 10px; border-radius: 16px; font-weight: 600;
    &.s-1 { background: #FCE8E6; color: #B3261E; }
    &.s-2 { background: #F1F3F4; color: #3C4043; }
    &.s-3 { background: #E6F4EA; color: #1E8E3E; }
    &.s-4 { background: #F1F3F4; color: #5F6368; }
  }
  .actions { display: flex; gap: 6px; }
}

.pager { padding: 12px 16px; display: flex; justify-content: flex-end; border-top: 1px solid var(--au-border-subtle); }

.gen-hint { font-size: 12px; color: var(--au-text-secondary); line-height: 1.6; padding: 0 4px; }

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .alert-page { padding: 10px; gap: 10px; }

  .stats { grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .stat { padding: 12px; gap: 10px; border-radius: 12px;
    .stat-icon { width: 36px; height: 36px; font-size: 18px; }
    .stat-value { font-size: 20px; }
    .stat-label { font-size: 11px; }
    .stat-trend { display: none; }
  }

  .tab-bar { flex-wrap: wrap; padding: 6px; gap: 2px; }
  .tab { padding: 6px 10px; font-size: 12px; }
  .tab-actions { margin-left: 0; width: 100%; justify-content: flex-end; padding: 4px 0 0; }

  .alert-list { padding: 8px; }
  .alert-item {
    flex-wrap: wrap;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 12px;
    .alert-icon { width: 36px; flex-direction: row; gap: 6px;
      .el-icon { font-size: 18px; }
    }
    .alert-main {
      width: calc(100% - 50px);
      .title-row .title { font-size: 13px; }
      .meta { gap: 8px; font-size: 10px; }
    }
  }
  .alert-status { flex-direction: row; align-items: center; width: 100%;
    justify-content: space-between; padding-top: 4px;
    border-top: 1px dashed var(--au-border-subtle);
  }

  .pager { justify-content: center; padding: 10px; }
}

@media (max-width: 480px) {
  .stats { grid-template-columns: repeat(2, 1fr); }
}
</style>
