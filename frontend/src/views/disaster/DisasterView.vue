<template>
  <div class="page">
    <!-- 时间轴: 与回放窗口绑定 -->

    <!-- 视图切换: 风险研判 / 气象站点 -->
    <div class="gcsj-card tabbar">
      <el-radio-group v-model="tab" size="default">
        <el-radio-button value="risk">风险研判事件</el-radio-button>
        <el-radio-button value="stations">气象站点</el-radio-button>
      </el-radio-group>
      <span class="tab-hint">
        <template v-if="tab === 'risk'">与监测大屏「风险日」统计同源 · biz_disaster_eval</template>
        <template v-else-if="tab === 'stations'">四川逐日气象观测站点 · gis_weather_station</template>
      </span>
    </div>

    <!-- ======================== 风险研判事件 (灾害判别结果) ======================== -->
    <template v-if="tab === 'risk'">
      <div class="gcsj-card toolbar">
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

      <div class="gcsj-card">
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
          <el-table-column label="R_eff" width="100" align="right">
            <template #default="{ row }">
              <span class="num-mono fs-12">{{ Number(row.r_eff ?? 0).toFixed(0) }} <i class="unit">mm</i></span>
            </template>
          </el-table-column>
          <el-table-column label="综合指数" width="110" align="right">
            <template #default="{ row }">
              <span class="num-mono fs-12">{{ Number(row.comp_index ?? 0).toFixed(2) }}</span>
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
      </div>
    </template>

    <!-- ======================== 气象站点 ======================== -->
    <template v-else>
      <div class="gcsj-card toolbar">
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

      <div class="gcsj-card">
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
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { apiEvalEvents, apiEvalStations } from '@/api/disasterEval'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'
import AuTimelineMini from '@/components/aurora/AuTimelineMini.vue'

// 当前视图: risk(判别结果) | stations(气象站点)
const tab = ref('risk')

// ---------- 风险研判事件 (biz_disaster_eval, 与大屏「风险日」同源) ----------
const YEARS = [2020, 2021, 2022, 2023]
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
const riskFilter = reactive({ year: 2022, type: 'comp', level: null, page: 1, size: 20 })

const fmtDay = (d) => (d ? String(d).slice(0, 10) : '—')

// 所选灾种 -> 等级列 & 列标题 (与后端 events 排序/过滤列保持一致)
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

// 切到站点视图时懒加载一次
function ensureTabData() {
  if (tab.value === 'stations' && !stationLoaded.value) loadStations()
}

// 监听 tab 切换以懒加载
watch(tab, ensureTabData)

onMounted(() => {
  loadRisk()
})
</script>

<style scoped lang="scss">
.page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }

.tabbar {
  display: flex; align-items: center; gap: 16px;
  padding: 12px 18px;
}
.tab-hint { font-size: 12px; color: var(--au-text-tertiary); }

.toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
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
  .page { padding: 10px; gap: 10px; }
  .tabbar { flex-direction: column; align-items: stretch; gap: 8px; padding: 12px; }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    padding: 12px;
  }
  .toolbar-left,
  .toolbar-right { width: 100%; flex-wrap: wrap; }
  .toolbar-right { justify-content: flex-end; }
  .row-title .title-desc { max-width: 60vw; }
  .pager { justify-content: center; padding: 10px; }
}
</style>
