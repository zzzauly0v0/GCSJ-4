<template>
  <div class="aurora-root">

    <!-- ============================================================
         HERO — 与监测大屏一致: 渐变描边 banner + 标题 + 视图切换
    ============================================================ -->
    <section class="au-hero hero">
      <div class="hero-inner">
        <div class="hero-text">
          <span class="au-pill-grad">气象 · 地质灾害分析</span>
          <h1 class="hero-title">
            <span class="au-grad-text">历史灾害分析</span>
          </h1>
          <p class="hero-sub">
            <template v-if="tab === 'overview'">历史灾害判别结果统计分析</template>
            <template v-else-if="tab === 'risk'">逐日风险研判明细, 支持全等级 / 灾种 / 年份筛选</template>
            <template v-else-if="tab === 'stations'">四川逐日气象观测站点档案</template>
          </p>
        </div>
        <div class="hero-tabs">
          <el-radio-group v-model="tab" size="default">
            <el-radio-button value="overview">统计概览</el-radio-button>
            <el-radio-button value="risk">风险研判明细</el-radio-button>
            <el-radio-button value="stations">气象站点</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </section>

    <!-- ======================== 统计概览 (历史分析) ======================== -->
    <template v-if="tab === 'overview'">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="overviewYear" placeholder="年份" clearable style="width: 120px" @change="loadSummary">
            <el-option label="全部年份" :value="null" />
            <el-option v-for="y in YEARS" :key="y" :label="String(y)" :value="y" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadSummary">刷新</el-button>
        </div>
      </div>

      <div v-loading="summaryLoading" class="overview-body">
        <!-- KPI 卡 -->
        <div class="kpi-grid">
          <AuKpiCard label="气象站点" :value="summary.stationCount || 0" unit="个" tone="info" />
          <AuKpiCard label="气象观测天数" :value="summary.weatherDays || 0" unit="天" tone="info" />
          <AuKpiCard label="风险研判日" :value="summary.riskDays || 0" unit="天" tone="warning" />
          <AuKpiCard label="覆盖年份" :value="overviewYear ? 1 : (summary.byYear?.length || YEARS.length)" unit="年" tone="success" />
        </div>

        <!-- 图表 -->
        <div class="chart-grid">
          <AuCard title="综合风险等级分布" subtitle="按预警等级统计风险日数" gradient-border dot class="chart-card">
            <StormChart title="" subtitle="" :option="levelChartOption" height="280px" />
          </AuCard>
          <AuCard title="四灾种风险日占比" subtitle="滑坡 / 暴雨 / 高温热浪 / 干旱 (level≥1)" gradient-border dot class="chart-card">
            <StormChart title="" subtitle="" :option="typeChartOption" height="280px" />
          </AuCard>
          <AuCard title="逐年风险日趋势" subtitle="历年综合风险日 (comp_level≥1) 变化" gradient-border dot class="chart-card chart-card-wide">
            <StormChart title="" subtitle="" :option="yearChartOption" height="280px" />
          </AuCard>
        </div>

        <!-- Top 风险日榜单 -->
        <AuCard title="Top 风险日榜单" subtitle="综合等级最高的观测日" dot flat class="table-card">
          <el-table :data="summary.topEvents || []" stripe size="default">
            <el-table-column label="排名" width="70" align="center">
              <template #default="{ $index }"><span class="num-mono">{{ $index + 1 }}</span></template>
            </el-table-column>
            <el-table-column label="站点" min-width="140">
              <template #default="{ row }">{{ row.station_name || row.station_code }}</template>
            </el-table-column>
            <el-table-column label="日期" width="130">
              <template #default="{ row }"><span class="num-mono fs-12 text-2">{{ fmtDay(row.obs_date) }}</span></template>
            </el-table-column>
            <el-table-column label="综合等级" width="110" align="center">
              <template #default="{ row }"><AlertLevelTag :level="row.comp_level" /></template>
            </el-table-column>
            <el-table-column label="有效雨量" width="120" align="right">
              <template #default="{ row }">
                <span class="num-mono fs-12">{{ row.r_eff != null ? Number(row.r_eff).toFixed(0) + ' mm' : '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </AuCard>
      </div>
    </template>

    <!-- ======================== 风险研判明细 (灾害判别结果) ======================== -->
    <template v-else-if="tab === 'risk'">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="riskFilter.year" placeholder="年份" clearable style="width: 120px" @change="reloadRisk">
            <el-option label="全部年份" :value="null" />
            <el-option v-for="y in YEARS" :key="y" :label="String(y)" :value="y" />
          </el-select>
          <el-select v-model="riskFilter.type" placeholder="灾种" style="width: 150px" @change="reloadRisk">
            <el-option v-for="t in RISK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
          <el-select v-model="riskFilter.level" placeholder="预警级别" clearable style="width: 130px" @change="reloadRisk">
            <el-option label="蓝色预警" :value="1" />
            <el-option label="黄色预警" :value="2" />
            <el-option label="橙色预警" :value="3" />
            <el-option label="红色预警" :value="4" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button :icon="Refresh" @click="loadRisk">刷新</el-button>
        </div>
      </div>

      <AuCard title="风险研判明细" :subtitle="`逐日灾害判别结果 · 共 ${riskTotal} 条`" dot flat class="table-card">
        <el-table :data="riskRows" v-loading="riskLoading" stripe size="default">
          <el-table-column label="站点" min-width="180">
            <template #default="{ row }">
              <div class="row-title">
                <div class="title-name">{{ row.station_name || row.station_code }}</div>
                <div class="title-desc num-mono">{{ row.station_code }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="风险日" width="130">
            <template #default="{ row }">
              <span class="num-mono fs-12 text-2">{{ fmtDay(row.obs_date) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="主导灾种" width="120">
            <template #default="{ row }">
              <span class="type-tag">{{ dominantTypeLabel(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="levelColLabel" width="100" align="center">
            <template #default="{ row }">
              <AlertLevelTag v-if="row[levelCol] >= 1" :level="row[levelCol]" />
              <span v-else class="text-3 fs-12">无</span>
            </template>
          </el-table-column>
          <el-table-column label="有效雨量" width="110" align="right">
            <template #default="{ row }">
              <span class="num-mono fs-12">{{ row.r_eff != null ? Number(row.r_eff).toFixed(0) + ' mm' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分项等级" min-width="220">
            <template #default="{ row }">
              <div class="sub-levels">
                <span v-for="d in RISK_COLS" :key="d.col" class="sub-lv" :class="`sl-${row[d.col] || 0}`">
                  {{ d.short }}{{ row[d.col] || 0 }}
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="riskFilter.page"
            v-model:page-size="riskFilter.size"
            :total="riskTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadRisk"
            @size-change="loadRisk"
          />
        </div>
      </AuCard>
    </template>

    <!-- ======================== 气象站点 ======================== -->
    <template v-else>
      <div class="toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="stationKeyword"
            placeholder="搜索站点名 / 区站号"
            :prefix-icon="Search"
            clearable
            style="width: 240px"
          />
          <el-select v-model="stationYear" placeholder="判别年份" clearable style="width: 140px" @change="loadStations">
            <el-option label="全部年份" :value="null" />
            <el-option v-for="y in YEARS" :key="y" :label="String(y)" :value="y" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <span class="text-3 fs-12">共 {{ filteredStations.length }} 个站点</span>
          <el-button :icon="Refresh" @click="loadStations" />
        </div>
      </div>

      <AuCard title="气象观测站点" :subtitle="`四川省逐日观测 · 共 ${filteredStations.length} 个`" dot flat class="table-card">
        <el-table :data="filteredStations" v-loading="stationLoading" stripe size="default" max-height="620">
          <el-table-column label="区站号" width="110">
            <template #default="{ row }">
              <span class="num-mono fs-12 text-2">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="站点名称" min-width="160" />
          <el-table-column label="行政区码" width="120">
            <template #default="{ row }">
              <span class="num-mono fs-12 text-3">{{ row.region_code || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="经纬度" width="200">
            <template #default="{ row }">
              <span class="num-mono fs-12 text-2">
                {{ Number(row.lon).toFixed(3) }}, {{ Number(row.lat).toFixed(3) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="覆盖年份" min-width="150">
            <template #default="{ row }">
              <span class="fs-12 text-2">{{ row.year_coverage || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="观测记录" width="120" align="right">
            <template #default="{ row }">
              <span class="num-mono fs-12">{{ (row.record_count ?? 0).toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="历史最高等级" width="130" align="center">
            <template #default="{ row }">
              <AlertLevelTag v-if="row.max_level >= 1" :level="row.max_level" />
              <span v-else class="text-3 fs-12">无风险</span>
            </template>
          </el-table-column>
        </el-table>
      </AuCard>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import * as echarts from 'echarts'
import { Search, Refresh } from '@element-plus/icons-vue'
import { apiEvalEvents, apiEvalStations, apiEvalSummary } from '@/api/disasterEval'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'
import AuKpiCard from '@/components/aurora/AuKpiCard.vue'
import AuCard from '@/components/aurora/AuCard.vue'
import StormChart from '@/components/charts/StormChart.vue'

// 学术云蓝图表通用样式 (与监测大屏 AuroraDashboard 保持一致)
const AU_TOOLTIP = {
  backgroundColor: '#FFFFFF',
  borderColor: '#E2E8F0',
  borderWidth: 1,
  textStyle: { color: '#1E293B', fontSize: 12 },
  extraCssText: 'box-shadow: 0 10px 30px rgba(30,64,175,0.10); border-radius: 10px; padding: 10px 12px;',
}
const AU_XAXIS = {
  axisLine: { lineStyle: { color: '#CBD5E1' } },
  axisTick: { show: false },
  axisLabel: { color: '#64748B', fontSize: 11 },
}
const AU_YAXIS = {
  axisLine: { show: false },
  axisTick: { show: false },
  axisLabel: { color: '#64748B', fontSize: 11 },
  splitLine: { lineStyle: { color: '#E2E8F0', type: 'dashed' } },
}

// 当前视图: overview(统计概览) | risk(判别明细) | stations(气象站点)
const tab = ref('overview')

// ---------- 风险研判事件 (biz_disaster_eval, 与大屏「风险日」同源) ----------
const YEARS = [2020, 2021, 2022, 2023]

// ---------- 统计概览 (历史分析, 复用 /disaster-eval/summary) ----------
const LEVEL_META = [
  { level: 1, label: '蓝色', color: '#2563EB' },
  { level: 2, label: '黄色', color: '#E8B300' },
  { level: 3, label: '橙色', color: '#E8710A' },
  { level: 4, label: '红色', color: '#DC2626' },
]
// 灾种色板与监测大屏一致 (indigo / cyan / blue / violet)
const TYPE_META = [
  { key: 'landslide', label: '滑坡', color: '#6366F1' },
  { key: 'mudslide', label: '暴雨', color: '#06B6D4' },
  { key: 'freezethaw', label: '高温热浪', color: '#3B82F6' },
  { key: 'collapse', label: '干旱', color: '#8B5CF6' },
]

const overviewYear = ref(null)
const summary = ref({})
const summaryLoading = ref(false)
const summaryLoaded = ref(false)

async function loadSummary() {
  summaryLoading.value = true
  try {
    summary.value = (await apiEvalSummary(overviewYear.value ?? undefined)) || {}
    summaryLoaded.value = true
  } finally { summaryLoading.value = false }
}

// 综合等级分布 -> 柱图
const levelChartOption = computed(() => {
  const byLevel = summary.value.byLevel || []
  const map = {}
  byLevel.forEach(l => { map[Number(l.level)] = Number(l.cnt) })
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', ...AU_TOOLTIP, axisPointer: { type: 'shadow' } },
    grid: { top: 20, left: 8, right: 12, bottom: 24, containLabel: true },
    xAxis: { type: 'category', data: LEVEL_META.map(m => m.label), ...AU_XAXIS },
    yAxis: { type: 'value', minInterval: 1, ...AU_YAXIS },
    series: [{
      type: 'bar', barWidth: '52%',
      itemStyle: { borderRadius: [4, 4, 0, 0] },
      data: LEVEL_META.map(m => ({ value: map[m.level] || 0, itemStyle: { color: m.color } })),
    }],
  }
})

// 四灾种风险日占比 -> 饼图
const typeChartOption = computed(() => {
  const byType = summary.value.byType || {}
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', ...AU_TOOLTIP, formatter: '{b}: {c} 天 ({d}%)' },
    legend: {
      bottom: 0, left: 'center',
      icon: 'circle', itemWidth: 8, itemHeight: 8,
      textStyle: { color: '#475569', fontSize: 11 },
    },
    series: [{
      type: 'pie', radius: ['46%', '70%'], center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { show: false },
      labelLine: { show: false },
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 3, borderRadius: 4 },
      emphasis: {
        label: { show: true, formatter: '{b}\n{d}%', color: '#0F172A', fontSize: 12, fontWeight: 600 },
      },
      data: TYPE_META.map(m => ({
        name: m.label, value: byType[m.key] || 0, itemStyle: { color: m.color },
      })),
    }],
  }
})

// 逐年风险日趋势 -> 折线
const yearChartOption = computed(() => {
  const byYear = summary.value.byYear || []
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis', ...AU_TOOLTIP,
      axisPointer: { type: 'line', lineStyle: { color: '#94A3B8', type: 'dashed' } },
    },
    grid: { top: 20, left: 8, right: 16, bottom: 24, containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: byYear.map(y => String(y.year)), ...AU_XAXIS },
    yAxis: { type: 'value', minInterval: 1, ...AU_YAXIS },
    series: [{
      type: 'line', smooth: true, symbol: 'circle', symbolSize: 7,
      lineStyle: { width: 2, color: '#6366F1' },
      itemStyle: { color: '#6366F1' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: '#6366F170' },
          { offset: 1, color: '#06B6D408' },
        ]),
      },
      data: byYear.map(y => Number(y.cnt)),
    }],
  }
})
const RISK_TYPES = [
  { label: '综合', value: 'comp' },
  { label: '滑坡', value: 'landslide' },
  { label: '暴雨', value: 'mudslide' },
  { label: '高温热浪', value: 'freezethaw' },
  { label: '干旱', value: 'collapse' },
]
const RISK_COLS = [
  { col: 'landslide_level', short: '滑坡' },
  { col: 'mudslide_level', short: '暴雨' },
  { col: 'freezethaw_level', short: '高温' },
  { col: 'collapse_level', short: '干旱' },
]
const RISK_TYPE_NAME = { landslide: '滑坡', mudslide: '暴雨', freezethaw: '高温热浪', collapse: '干旱' }

const riskRows = ref([])
const riskTotal = ref(0)
const riskLoading = ref(false)
const riskLoaded = ref(false)
const riskFilter = reactive({ year: 2022, type: 'comp', level: null, page: 1, size: 20 })

const fmtDay = (d) => (d ? String(d).slice(0, 10) : '—')

// 所选灾种 -> 等级列 & 列标题 (与后端 TYPE_COL 保持一致: 列名沿用早期命名, 实际语义见 RISK_TYPES)
const TYPE_LEVEL_COL = {
  comp: 'comp_level',
  landslide: 'landslide_level',
  mudslide: 'mudslide_level',
  freezethaw: 'freezethaw_level',
  collapse: 'collapse_level',
}
const levelCol = computed(() => TYPE_LEVEL_COL[riskFilter.type] || 'comp_level')
const levelColLabel = computed(() => {
  const t = RISK_TYPES.find(x => x.value === riskFilter.type)
  return riskFilter.type === 'comp' ? '综合等级' : `${t?.label || ''}等级`
})

/** 取分项等级最高的灾种作为主导灾种 */
function dominantTypeLabel(row) {
  let best = null, bestLv = 0
  for (const d of RISK_COLS) {
    const lv = row[d.col] || 0
    if (lv > bestLv) { bestLv = lv; best = d.col }
  }
  if (!best) return '综合'
  const key = best.replace('_level', '')
  return RISK_TYPE_NAME[key] || '综合'
}

async function loadRisk() {
  riskLoading.value = true
  try {
    // 拦截器已拆包, 直接得到 { total, list }
    const data = await apiEvalEvents({
      year: riskFilter.year ?? undefined,
      type: riskFilter.type,
      level: riskFilter.level ?? undefined,
      page: riskFilter.page,
      size: riskFilter.size,
    })
    riskRows.value = data?.list || []
    riskTotal.value = data?.total || 0
    riskLoaded.value = true
  } finally { riskLoading.value = false }
}

/** 过滤条件变更: 回到第一页再拉 */
function reloadRisk() {
  riskFilter.page = 1
  loadRisk()
}

// ---------- 气象站点 (gis_weather_station) ----------
const stationRows = ref([])
const stationLoading = ref(false)
const stationLoaded = ref(false)
const stationYear = ref(null)
const stationKeyword = ref('')

const filteredStations = computed(() => {
  const q = stationKeyword.value.trim().toLowerCase()
  if (!q) return stationRows.value
  return stationRows.value.filter(s =>
    String(s.name || '').toLowerCase().includes(q) ||
    String(s.code || '').toLowerCase().includes(q)
  )
})

async function loadStations() {
  stationLoading.value = true
  try {
    // 拦截器已拆包, 直接得到数组
    const list = await apiEvalStations(stationYear.value ?? undefined)
    stationRows.value = list || []
    stationLoaded.value = true
  } finally { stationLoading.value = false }
}

// 切到各视图时懒加载一次
function ensureTabData() {
  if (tab.value === 'overview' && !summaryLoaded.value) loadSummary()
  else if (tab.value === 'risk' && !riskLoaded.value) loadRisk()
  else if (tab.value === 'stations' && !stationLoaded.value) loadStations()
}

// 监听 tab 切换以懒加载
watch(tab, ensureTabData)

onMounted(() => {
  loadSummary()   // 默认进入统计概览
})
</script>

<style scoped lang="scss">
.aurora-root {
  min-height: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background: var(--au-bg-page);
}

/* ---- HERO (与监测大屏一致) ---- */
.hero { padding: 26px 28px; }
.hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}
.hero-text { min-width: 260px; flex: 1; }
.hero-title {
  margin: 10px 0 6px;
  font-size: 28px;
  font-weight: 700;
  color: var(--au-text-strong);
  letter-spacing: -0.02em;
}
.hero-sub {
  margin: 0;
  font-size: 13px;
  color: var(--au-text-secondary);
  max-width: 540px;
  line-height: 1.6;
}
.hero-tabs { flex-shrink: 0; }

/* ---- 统计概览 ---- */
.overview-body { display: flex; flex-direction: column; gap: 16px; }
.kpi-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
}
.chart-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 16px;
}
.chart-card-wide { grid-column: 1 / -1; }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
}
.toolbar-left { display: flex; gap: 10px; align-items: center; }
.toolbar-right { display: flex; gap: 8px; align-items: center; }

.row-title {
  .title-name { font-size: 14px; color: var(--text-1); font-weight: 600; }
  .title-desc { font-size: 12px; color: var(--text-3); margin-top: 2px;
    overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px;
  }
}
.type-tag {
  font-size: 12px; padding: 2px 8px; border-radius: 16px;
  background: rgb(248, 249, 252); color: var(--au-text-strong);
  border: 1px solid var(--au-border-subtle);
}
.pager { padding: 14px; display: flex; justify-content: flex-end; }

.unit { font-style: normal; font-size: 10px; color: var(--au-text-tertiary); }

/* 分项等级小徽标 */
.sub-levels { display: flex; flex-wrap: wrap; gap: 4px; }
.sub-lv {
  font-size: 11px; padding: 1px 6px; border-radius: 16px;
  font-family: var(--au-font-num, monospace);
  border: 1px solid var(--au-border-subtle);
  background: rgb(248, 249, 252); color: var(--au-text-tertiary);
}
.sub-lv.sl-1 { color: #2563EB; border-color: rgba(37, 99, 235, 0.28); background: rgba(37, 99, 235, 0.08); }
.sub-lv.sl-2 { color: #B45309; border-color: rgba(245, 158, 11, 0.30); background: rgba(245, 158, 11, 0.10); }
.sub-lv.sl-3 { color: #C2410C; border-color: rgba(249, 115, 22, 0.32); background: rgba(249, 115, 22, 0.10); }
.sub-lv.sl-4 { color: #DC2626; border-color: rgba(220, 38, 38, 0.34); background: rgba(220, 38, 38, 0.10); }

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .aurora-root { padding: 12px; gap: 12px; }
  .hero { padding: 18px 16px; }
  .hero-inner { flex-direction: column; align-items: stretch; gap: 14px; }
  .hero-tabs { width: 100%; }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .toolbar-left,
  .toolbar-right { width: 100%; flex-wrap: wrap; }
  .toolbar-right { justify-content: flex-end; }
  .row-title .title-desc { max-width: 60vw; }
  .pager { justify-content: center; padding: 10px; }
  .kpi-grid { grid-template-columns: repeat(2, 1fr); gap: 8px; }
  .chart-grid { grid-template-columns: 1fr; gap: 10px; }
}
</style>
