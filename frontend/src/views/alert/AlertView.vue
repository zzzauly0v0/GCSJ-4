<template>
  <div class="alert-page">
    <!-- ====== 顶部统计卡片 ====== -->
    <div class="stats">
      <div v-for="s in statsData" :key="s.key" class="stat" :style="{'--c': s.color}">
        <div class="stat-icon"><el-icon><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value num-mono">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- ====== 筛选 + 列表 ====== -->
    <div class="gcsj-card">
      <!-- 灾害类型 Tab -->
      <div class="tab-bar">
        <div
          v-for="t in disasterTypeTabs"
          :key="t.value"
          class="tab"
          :class="{ active: filter.disasterType === t.value }"
          @click="onDisasterTypeChange(t.value)"
        >
          <span>{{ t.label }}</span>
          <span class="tab-count">{{ t.count }}</span>
        </div>
      </div>
      <!-- 预警等级 Tab -->
      <div class="tab-bar tab-bar-level">
        <div
          v-for="t in levelTabs"
          :key="t.value"
          class="tab"
          :class="{ active: filter.level === t.value }"
          @click="onLevelChange(t.value)"
        >
          <span v-if="t.color" class="tab-dot" :style="{ background: t.color }" />
          <span>{{ t.label }}</span>
          <span class="tab-count">{{ t.count }}</span>
        </div>

        <div class="tab-actions">
          <el-select v-model="filter.status" placeholder="处理状态" clearable style="width: 130px" size="default" @change="onFilterChange">
            <el-option label="全部状态" :value="null" />
            <el-option label="未处理" :value="1" />
            <el-option label="处理中" :value="2" />
            <el-option label="已解除" :value="3" />
          </el-select>
          <el-button :icon="Refresh" @click="refresh" circle />
        </div>
      </div>

      <!-- ====== 预警列表表格 ====== -->
      <div class="alert-table-wrap" v-loading="loading">
        <el-table
          :data="rows"
          style="width: 100%"
          size="default"
          row-key="id"
          @row-click="onRowClick"
          :empty-text="'暂无预警记录'"
          highlight-current-row
          row-class-name="alert-row"
        >
          <el-table-column label="预警编号" width="170">
            <template #default="{ row }">
              <span class="alert-code">{{ row.code }}</span>
            </template>
          </el-table-column>
          <el-table-column label="灾害类型" width="110">
            <template #default="{ row }">
              <span class="disaster-type-tag" :class="`dt-${row.dominantType}`">
                {{ row.disasterTypeLabel }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="所属地区" min-width="100">
            <template #default="{ row }">
              <span>{{ row.regionName || row.stationName || row.stationCode || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="站点编号" width="100">
            <template #default="{ row }">
              <span class="num-mono">{{ row.stationCode }}</span>
            </template>
          </el-table-column>
          <el-table-column label="预警等级" width="100">
            <template #default="{ row }">
              <span class="level-tag" :class="`lvl-${row.level}`">
                {{ levelLabel(row.level) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="有效雨量" width="100">
            <template #default="{ row }">
              <span class="num-mono">{{ row.rEff != null ? Number(row.rEff).toFixed(0) + ' mm' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="120">
            <template #default="{ row }">
              <span>{{ row.obsDate }}</span>
            </template>
          </el-table-column>
          <el-table-column label="当前状态" width="90">
            <template #default="{ row }">
              <span class="status-tag" :class="`s-${row._status}`">{{ statusLabel(row._status) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="170" fixed="right">
            <template #default="{ row }">
              <div class="action-btns">
                <el-button size="small" type="primary" @click.stop="openDetail(row)">查看详情</el-button>
                <el-button
                  v-if="row._status === 1"
                  size="small"
                  type="warning"
                  @click.stop="onStartProcess(row)"
                >开始处理</el-button>
                <el-button
                  v-if="row._status === 2"
                  size="small"
                  type="success"
                  @click.stop="onResolve(row)"
                >解除预警</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
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

    <!-- ====== 预警详情弹窗 ====== -->
    <el-dialog
      v-model="detailDlg"
      :title="`预警详情 — ${detailRow?.stationName || detailRow?.stationCode || ''}`"
      width="820px"
      top="40px"
      destroy-on-close
      class="detail-dialog"
    >
      <template v-if="detail">
        <!-- 综合等级卡片 -->
        <div class="risk-summary-card" :class="`risk-bg-${detail.eval?.comp_level || 0}`">
          <div class="risk-main">
            <div class="risk-level-badge" :class="`lvl-${detail.eval?.comp_level || 0}`">
              {{ levelLabel(detail.eval?.comp_level) }}
            </div>
            <div class="risk-index">
              <span class="risk-index-label">有效雨量 R<sub>eff</sub></span>
              <span class="risk-index-value num-mono">{{ detail.eval?.r_eff != null ? Number(detail.eval.r_eff).toFixed(0) + ' mm' : '-' }}</span>
            </div>
          </div>
          <div class="risk-type-list">
            <div class="risk-type-item" v-for="dt in disasterTypeDetails" :key="dt.key">
              <span class="risk-type-label">{{ dt.label }}</span>
              <span class="level-tag" :class="`lvl-${dt.level}`">{{ levelLabel(dt.level) }}</span>
            </div>
          </div>
        </div>

        <!-- 基本信息 -->
        <div class="detail-section">
          <h4 class="section-title">基本信息</h4>
          <div class="info-grid">
            <div class="info-item"><span class="info-label">预警编号</span><span class="info-value num-mono">{{ detailRow?.code }}</span></div>
            <div class="info-item"><span class="info-label">灾害类型</span><span class="info-value">{{ detailRow?.disasterTypeLabel }}</span></div>
            <div class="info-item"><span class="info-label">所属地区</span><span class="info-value">{{ detailRow?.regionName || detailRow?.stationName || '-' }}</span></div>
            <div class="info-item"><span class="info-label">气象站编号</span><span class="info-value num-mono">{{ detailRow?.stationCode }}</span></div>
            <div class="info-item"><span class="info-label">经度 / 纬度</span><span class="info-value num-mono">{{ detail.eval?.lon?.toFixed(4) }}, {{ detail.eval?.lat?.toFixed(4) }}</span></div>
            <div class="info-item"><span class="info-label">发布时间</span><span class="info-value">{{ detailRow?.obsDate }}</span></div>
            <div class="info-item"><span class="info-label">当前状态</span><span class="info-value"><span class="status-tag" :class="`s-${detailRow?._status || 1}`">{{ statusLabel(detailRow?._status || 1) }}</span></span></div>
            <div class="info-item"><span class="info-label">站点高程</span><span class="info-value num-mono">{{ detail.eval?.station_elevation != null ? detail.eval.station_elevation + ' m' : '-' }}</span></div>
          </div>
        </div>

        <!-- 气象监测数据 -->
        <div class="detail-section">
          <h4 class="section-title">气象监测数据</h4>
          <div class="info-grid">
            <template v-if="detailRow?.dominantType === 'landslide'">
              <div class="info-item"><span class="info-label">日降水量</span><span class="info-value num-mono">{{ detail.weather?.rainfall != null ? detail.weather.rainfall + ' mm' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">有效降雨量 (R<sub>eff</sub>)</span><span class="info-value num-mono">{{ detail.eval?.r_eff != null ? detail.eval.r_eff + ' mm' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">日平均气温</span><span class="info-value num-mono">{{ detail.weather?.temp_avg != null ? detail.weather.temp_avg + ' ℃' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">日较差 (DTR)</span><span class="info-value num-mono">{{ detail.eval?.dtr != null ? detail.eval.dtr + ' ℃' : '-' }}</span></div>
            </template>
            <template v-else-if="detailRow?.dominantType === 'mudslide'">
              <div class="info-item"><span class="info-label">日降水量</span><span class="info-value num-mono">{{ detail.weather?.rainfall != null ? detail.weather.rainfall + ' mm' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">最大风速</span><span class="info-value num-mono">{{ detail.weather?.wind_max != null ? detail.weather.wind_max + ' m/s' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">平均湿度</span><span class="info-value num-mono">{{ detail.weather?.rh_avg != null ? detail.weather.rh_avg + ' %' : '-' }}</span></div>
            </template>
            <template v-else-if="detailRow?.dominantType === 'freezethaw'">
              <div class="info-item"><span class="info-label">日最高气温</span><span class="info-value num-mono">{{ detail.weather?.temp_max != null ? detail.weather.temp_max + ' ℃' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">日最低气温</span><span class="info-value num-mono">{{ detail.weather?.temp_min != null ? detail.weather.temp_min + ' ℃' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">日较差 (DTR)</span><span class="info-value num-mono">{{ detail.eval?.dtr != null ? detail.eval.dtr + ' ℃' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">平均湿度</span><span class="info-value num-mono">{{ detail.weather?.rh_avg != null ? detail.weather.rh_avg + ' %' : '-' }}</span></div>
            </template>
            <template v-else-if="detailRow?.dominantType === 'collapse'">
              <div class="info-item"><span class="info-label">月累计降水</span><span class="info-value num-mono">{{ detail.monthly?.monthly_rain != null ? Number(detail.monthly.monthly_rain).toFixed(1) + ' mm' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">无雨日比例</span><span class="info-value num-mono">{{ detail.monthly?.days ? (detail.monthly.dry_days / detail.monthly.days * 100).toFixed(1) + '%' : '-' }}</span></div>
              <div class="info-item"><span class="info-label">所属季节</span><span class="info-value">{{ dryWetSeason(detail.month) }}</span></div>
              <div class="info-item"><span class="info-label">日最高气温</span><span class="info-value num-mono">{{ detail.weather?.temp_max != null ? detail.weather.temp_max + ' ℃' : '-' }}</span></div>
            </template>
          </div>
        </div>

        <!-- 地形环境数据 (滑坡专项) -->
        <div class="detail-section" v-if="detailRow?.dominantType === 'landslide'">
          <h4 class="section-title">地形环境数据（滑坡专项）</h4>
          <div class="info-grid">
            <div class="info-item"><span class="info-label">站点高程</span><span class="info-value num-mono">{{ detail.eval?.station_elevation != null ? detail.eval.station_elevation + ' m' : '-' }}</span></div>
            <div class="info-item"><span class="info-label">岩土粘聚力</span><span class="info-value num-mono">c' = 12.0 kPa</span></div>
            <div class="info-item"><span class="info-label">内摩擦角</span><span class="info-value num-mono">φ' = 28°</span></div>
            <div class="info-item"><span class="info-label">土体厚度</span><span class="info-value num-mono">Z = 2.0 m</span></div>
            <div class="info-item"><span class="info-label">饱和渗透系数</span><span class="info-value num-mono">K<sub>s</sub> = 0.05 m/s</span></div>
          </div>
        </div>

        <!-- 灾害判别依据 -->
        <div class="detail-section">
          <h4 class="section-title">灾害判别依据</h4>
          <div class="criteria-box" v-html="criteriaText"></div>
        </div>

        <!-- 风险评估结果 -->
        <div class="detail-section">
          <h4 class="section-title">风险评估结果</h4>
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">综合等级</span>
              <span class="info-value">
                <span class="level-tag" :class="`lvl-${detail.eval?.comp_level}`">{{ levelLabel(detail.eval?.comp_level) }}</span>
              </span>
            </div>
            <div class="info-item">
              <span class="info-label">有效雨量 R<sub>eff</sub></span>
              <span class="info-value num-mono">{{ detail.eval?.r_eff != null ? Number(detail.eval.r_eff).toFixed(1) + ' mm' : '-' }}</span>
            </div>
            <div class="info-item" v-if="detailRow?.dominantType === 'landslide'">
              <span class="info-label">CRI 风险标签</span>
              <span class="info-value">{{ criLevelLabel(detail.eval?.landslide_level) }}</span>
            </div>
          </div>
          <div class="info-grid" style="margin-top:12px">
            <div class="info-item">
              <span class="info-label">滑坡</span>
              <span class="info-value"><span class="level-tag" :class="`lvl-${detail.eval?.landslide_level || 0}`">{{ levelLabel(detail.eval?.landslide_level || 0) }}</span></span>
            </div>
            <div class="info-item">
              <span class="info-label">暴雨</span>
              <span class="info-value"><span class="level-tag" :class="`lvl-${detail.eval?.mudslide_level || 0}`">{{ levelLabel(detail.eval?.mudslide_level || 0) }}</span></span>
            </div>
            <div class="info-item">
              <span class="info-label">高温热浪</span>
              <span class="info-value"><span class="level-tag" :class="`lvl-${detail.eval?.freezethaw_level || 0}`">{{ levelLabel(detail.eval?.freezethaw_level || 0) }}</span></span>
            </div>
            <div class="info-item">
              <span class="info-label">干旱</span>
              <span class="info-value"><span class="level-tag" :class="`lvl-${detail.eval?.collapse_level || 0}`">{{ levelLabel(detail.eval?.collapse_level || 0) }}</span></span>
            </div>
          </div>
        </div>

        <!-- 防御建议 -->
        <div class="detail-section">
          <h4 class="section-title">防御建议</h4>
          <div class="suggest-box">
            <ul>
              <li v-for="(s, i) in suggestions" :key="i">{{ s }}</li>
            </ul>
          </div>
        </div>
      </template>
      <template v-else>
        <div style="text-align:center;padding:40px"><el-icon class="is-loading" :size="32"><Loading /></el-icon><p style="margin-top:12px">加载中...</p></div>
      </template>

      <template #footer>
        <el-button @click="detailDlg = false">关闭</el-button>
        <el-button v-if="detailRow?._status === 1" type="warning" @click="onStartProcess(detailRow); detailDlg = false">开始处理</el-button>
        <el-button v-if="detailRow?._status === 2" type="success" @click="onResolve(detailRow); detailDlg = false">解除预警</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  BellFilled, Warning, Check, Refresh, Loading
} from '@element-plus/icons-vue'
import { apiEvalEvents, apiEvalDetail } from '@/api/disasterEval'

// ---- 常量 ----
const DISASTER_TYPES = {
  landslide: { key: 'landslide', label: '滑坡', col: 'landslideLevel' },
  mudslide: { key: 'mudslide', label: '暴雨', col: 'mudslideLevel' },
  freezethaw: { key: 'freezethaw', label: '高温热浪', col: 'freezethawLevel' },
  collapse: { key: 'collapse', label: '干旱', col: 'collapseLevel' }
}

const LEVEL_LABELS = { 0: '无风险', 1: '蓝色预警', 2: '黄色预警', 3: '橙色预警', 4: '红色预警' }
const LEVEL_SHORT = { 0: '无', 1: '蓝色', 2: '黄色', 3: '橙色', 4: '红色' }
const STATUS_LABELS = { 1: '未处理', 2: '处理中', 3: '已解除' }

function levelLabel(lv) { return LEVEL_SHORT[lv] || '-' }
function statusLabel(s) { return STATUS_LABELS[s] || '-' }
function criLevelLabel(lv) {
  return ({ 1: '低风险', 2: '中风险', 3: '高风险', 4: '极高风险' })[lv] || '-'
}
function dryWetSeason(m) {
  return [11, 12, 1, 2, 3, 4].includes(m) ? '干季' : '湿季'
}

// 生成预警编号: A + obs_date(YYYYMMDD) + station_code后3位 + 序号
function makeCode(stationCode, obsDate) {
  const d = (obsDate || '').replace(/-/g, '')
  const suffix = (stationCode || '000').slice(-3)
  return `A${d}${suffix}`
}

// 确定主导灾种 (兼容后端 snake_case 和前端映射后的 camelCase)
function getDominantType(row) {
  const cols = [
    { key: 'landslide', val: row.landslide_level ?? row.landslideLevel ?? 0 },
    { key: 'mudslide', val: row.mudslide_level ?? row.mudslideLevel ?? 0 },
    { key: 'freezethaw', val: row.freezethaw_level ?? row.freezethawLevel ?? 0 },
    { key: 'collapse', val: row.collapse_level ?? row.collapseLevel ?? 0 }
  ]
  let best = cols[0]
  for (const c of cols) { if (c.val > best.val) best = c }
  return best.val > 0 ? best.key : 'comp'
}

// ---- 状态 ----
// 预警管理 = 应急处置台账: 只关注需要处置的高风险 (橙3/红4), 一次性拉取全部高风险记录到
// sourceRows, 灾种/等级/状态筛选与分页全部在前端本地完成 (与只读的"历史灾害分析"页区隔)。
const sourceRows = ref([])   // 全部高风险(comp_level>=3)记录, 已映射
const loading = ref(false)
const filter = reactive({
  disasterType: null,   // null=全部, or landslide/mudslide/freezethaw/collapse
  level: null,          // null=全部高风险, 3=橙 / 4=红
  status: null,         // null=全部, 1=未处理/2=处理中/3=已解除
  page: 1,
  size: 20
})

// 本地状态管理 (因 biz_disaster_eval 无 status 列, 前端本地维护; 刷新丢失, 暂不落库)
const localStatus = reactive({})  // key: "stationCode|obsDate" → status

// ---- 计算属性 ----
// 高风险(橙红)记录的等级计数, 供等级 tab 徽标 + 统计卡, 前端聚合摆脱蓝黄。
const highRiskCounts = computed(() => {
  let red = 0, orange = 0
  for (const r of sourceRows.value) {
    if (r.compLevel === 4) red++
    else if (r.compLevel === 3) orange++
  }
  return { red, orange, total: red + orange }
})

// 灾种 tab 徽标: 由已加载的高风险记录按主导灾种前端聚合 (只计橙红)。
const disasterTypeTabs = computed(() => {
  const byType = { landslide: 0, mudslide: 0, freezethaw: 0, collapse: 0 }
  for (const r of sourceRows.value) {
    if (byType[r.dominantType] != null) byType[r.dominantType]++
  }
  return [
    { value: null, label: '全部灾种', count: sourceRows.value.length },
    { value: 'landslide', label: '滑坡', count: byType.landslide },
    { value: 'mudslide', label: '暴雨', count: byType.mudslide },
    { value: 'freezethaw', label: '高温热浪', count: byType.freezethaw },
    { value: 'collapse', label: '干旱', count: byType.collapse }
  ]
})

// 按当前灾种/等级/状态筛选后的全量高风险记录 (_status 从 localStatus 实时派生, 保证响应式)
const filteredRows = computed(() => {
  return sourceRows.value
    .map(r => ({ ...r, _status: localStatus[getStatusKey(r)] || 1 }))
    .filter(r => {
      if (filter.disasterType && r.dominantType !== filter.disasterType) return false
      if (filter.level != null && r.compLevel !== filter.level) return false
      if (filter.status != null && r._status !== filter.status) return false
      return true
    })
})

// 本地分页后的当页数据 + 总数
const total = computed(() => filteredRows.value.length)
const rows = computed(() => {
  const start = (filter.page - 1) * filter.size
  return filteredRows.value.slice(start, start + filter.size)
})

// 预警管理只处置橙(3)/红(4)高风险; 计数由当前已加载的高风险全量在前端聚合得出。
const levelTabs = computed(() => {
  const c = highRiskCounts.value
  return [
    { value: null, label: '全部高风险', count: c.total, color: null },
    { value: 4, label: '红色预警', count: c.red, color: '#D93025' },
    { value: 3, label: '橙色预警', count: c.orange, color: '#E8710A' }
  ]
})

// 顶部统计卡: 处置导向指标 (高风险总数 + 红/橙 + 处置进度), 不再出现蓝黄。
const statsData = computed(() => {
  const c = highRiskCounts.value
  const statusVals = sourceRows.value.map(r => localStatus[getStatusKey(r)] || 1)
  const pending = statusVals.filter(s => s === 1).length
  const processing = statusVals.filter(s => s === 2).length
  const done = statusVals.filter(s => s === 3).length
  return [
    { key: 'total', label: '高风险预警', value: c.total, color: '#3C4043', icon: 'BellFilled' },
    { key: 'red', label: '红色预警', value: c.red, color: '#D93025', icon: 'Warning' },
    { key: 'orange', label: '橙色预警', value: c.orange, color: '#E8710A', icon: 'Warning' },
    { key: 'pending', label: '待处理', value: pending, color: '#B3261E', icon: 'BellFilled' },
    { key: 'processing', label: '处理中', value: processing, color: '#E8710A', icon: 'Warning' },
    { key: 'done', label: '已处置', value: done, color: '#1E8E3E', icon: 'Check' }
  ]
})

// ---- 数据加载 ----
function getStatusKey(row) {
  return `${row.stationCode || row.station_code}|${row.obsDate || row.obs_date}`
}

function mapRow(r) {
  const dominantType = getDominantType(r)
  const stationCode = r.station_code || r.stationCode
  const obsDate = r.obs_date || r.obsDate
  return {
    code: makeCode(stationCode, obsDate),
    stationCode,
    stationName: r.station_name || r.stationName,
    obsDate,
    regionCode: r.region_code || r.regionCode,
    regionName: r.region_name || r.regionName || null,
    compLevel: r.comp_level ?? r.compLevel ?? 0,
    level: r.comp_level ?? r.compLevel ?? 0,   // 展示等级 = 综合等级
    dominantType,
    disasterTypeLabel: DISASTER_TYPES[dominantType]?.label || '综合',
    landslideLevel: r.landslide_level ?? r.landslideLevel ?? 0,
    mudslideLevel: r.mudslide_level ?? r.mudslideLevel ?? 0,
    freezethawLevel: r.freezethaw_level ?? r.freezethawLevel ?? 0,
    collapseLevel: r.collapse_level ?? r.collapseLevel ?? 0,
    lon: r.lon,
    lat: r.lat,
    elevation: r.elevation,
    rEff: r.r_eff ?? r.rEff,
    dtr: r.dtr
  }
}

// 只拉高风险(橙3/红4)记录; 灾种/等级/状态筛选与分页均在前端本地完成。
// 后端 /events 的 level 是精确匹配, 无法一次取"3或4", 故对 3、4 各发一次请求再合并 (比拉全量 >=1 再本地过滤高效得多)。
async function loadSource() {
  loading.value = true
  try {
    const [orange, red] = await Promise.all([
      apiEvalEvents({ level: 3, page: 1, size: 100000 }),
      apiEvalEvents({ level: 4, page: 1, size: 100000 }),
    ])
    const list = [...(orange?.list || []), ...(red?.list || [])].map(mapRow)
    // 按综合等级降序、日期降序, 红色高风险优先
    list.sort((a, b) => b.compLevel - a.compLevel || String(b.obsDate).localeCompare(String(a.obsDate)))
    sourceRows.value = list
  } finally {
    loading.value = false
  }
}

async function refresh() {
  filter.page = 1
  await loadSource()
}

// loadPage 仅用于翻页/换页大小, 数据已全量在本地, 无需重新请求 (rows/total 为 computed)。
function loadPage() { /* no-op: 分页由 computed rows/total 自动完成 */ }

function onDisasterTypeChange(val) {
  filter.disasterType = val
  filter.page = 1
}

function onLevelChange(val) {
  filter.level = val
  filter.page = 1
}

function onFilterChange() {
  filter.page = 1
}

// ---- 状态操作 ----
function onStartProcess(row) {
  localStatus[getStatusKey(row)] = 2
  ElMessage.success(`预警 ${row.code} 已标记为"处理中"`)
}

function onResolve(row) {
  localStatus[getStatusKey(row)] = 3
  ElMessage.success(`预警 ${row.code} 已解除`)
}

function onRowClick(row) {
  openDetail(row)
}

// ---- 预警详情 ----
const detailDlg = ref(false)
const detail = ref(null)
const detailRow = ref(null)

async function openDetail(row) {
  detailRow.value = row
  detail.value = null
  detailDlg.value = true
  try {
    detail.value = await apiEvalDetail(row.stationCode, row.obsDate)
  } catch {
    ElMessage.error('加载预警详情失败')
    detailDlg.value = false
  }
}

const criteriaText = computed(() => {
  if (!detail.value || !detailRow.value) return ''
  const dt = detailRow.value.dominantType
  const evalData = detail.value.eval || {}
  const weather = detail.value.weather || {}
  const monthly = detail.value.monthly || {}
  const level = detailRow.value.level

  if (dt === 'mudslide') {
    // 暴雨
    const rain = weather.rainfall ?? '-'
    return `
      <p><strong>依据：</strong>GB/T 28592—2012《降水量等级》</p>
      <table class="criteria-table">
        <tr><th>等级</th><th>条件</th><th>含义</th></tr>
        <tr class="${level === 1 ? 'active' : ''}"><td>1</td><td>25 ≤ Rain < 50 mm</td><td>蓝色</td></tr>
        <tr class="${level === 2 ? 'active' : ''}"><td>2</td><td>50 ≤ Rain < 100 mm</td><td>黄色</td></tr>
        <tr class="${level === 3 ? 'active' : ''}"><td>3</td><td>100 ≤ Rain < 250 mm</td><td>橙色</td></tr>
        <tr class="${level === 4 ? 'active' : ''}"><td>4</td><td>Rain ≥ 250 mm</td><td>红色</td></tr>
      </table>
      <p style="margin-top:8px">监测降水量：<strong>${rain} mm</strong> → 判定结果：<strong>${LEVEL_LABELS[level]}</strong></p>
    `
  }
  if (dt === 'freezethaw') {
    // 高温热浪
    const tmax = weather.temp_max ?? '-'
    return `
      <p><strong>依据：</strong>GB/T 20481—2017《高温热浪等级》</p>
      <table class="criteria-table">
        <tr><th>等级</th><th>条件</th><th>含义</th></tr>
        <tr class="${level === 1 ? 'active' : ''}"><td>1</td><td>33 ≤ Tmax < 35 ℃</td><td>蓝色</td></tr>
        <tr class="${level === 2 ? 'active' : ''}"><td>2</td><td>35 ≤ Tmax < 37 ℃</td><td>黄色</td></tr>
        <tr class="${level === 3 ? 'active' : ''}"><td>3</td><td>37 ≤ Tmax < 40 ℃</td><td>橙色</td></tr>
        <tr class="${level === 4 ? 'active' : ''}"><td>4</td><td>Tmax ≥ 40 ℃</td><td>红色</td></tr>
      </table>
      <p style="margin-top:8px">最高气温：<strong>${tmax} ℃</strong> → 判定结果：<strong>${LEVEL_LABELS[level]}</strong></p>
    `
  }
  if (dt === 'collapse') {
    // 干旱
    const m = detail.value.month
    const mrain = monthly.monthly_rain ?? '-'
    const season = dryWetSeason(m)
    return `
      <p><strong>依据：</strong>GB/T 20481—2017《气象干旱等级》月累计降水简化方案</p>
      <table class="criteria-table">
        <tr><th>季节</th><th>月份</th><th>等级1</th><th>等级2</th><th>等级3</th><th>等级4</th></tr>
        <tr class="${[11,12,1,2,3,4].includes(m) ? 'active' : ''}">
          <td>干季</td><td>11-4月</td><td>&lt;30</td><td>&lt;20</td><td>&lt;10</td><td>&lt;5 mm</td>
        </tr>
        <tr class="${[5,6,7,8,9,10].includes(m) ? 'active' : ''}">
          <td>湿季</td><td>5-10月</td><td>&lt;80</td><td>&lt;60</td><td>&lt;40</td><td>&lt;20 mm</td>
        </tr>
      </table>
      <p style="margin-top:8px">
        月份：<strong>${m} 月（${season}）</strong><br/>
        月累计降水：<strong>${typeof mrain === 'number' ? mrain.toFixed(1) : mrain} mm</strong><br/>
        判定结果：<strong>${LEVEL_LABELS[level]}</strong>
      </p>
    `
  }
  if (dt === 'landslide') {
    // 滑坡 TRIGRS + CRI
    const rEff = evalData.r_eff ?? '-'
    const lsLevel = evalData.landslide_level ?? 0
    return `
      <p><strong>依据：</strong>TRIGRS 无限边坡模型 + CRI 复合风险指数（两步法）</p>
      <p><strong>模型参数：</strong>c'=12.0 kPa, φ'=28°, γ<sub>s</sub>=19.0 kN/m³, γ<sub>t</sub>=17.2 kN/m³, Z=2.0 m, K<sub>s</sub>=0.05 m/s, α=0.85</p>
      <p style="margin-top:6px"><strong>FS 等级判定：</strong></p>
      <table class="criteria-table">
        <tr><th>等级</th><th>Fs 范围</th><th>含义</th></tr>
        <tr class="${lsLevel === 1 ? 'active' : ''}"><td>1</td><td>1.5 ≤ Fs < 2.0</td><td>低风险</td></tr>
        <tr class="${lsLevel === 2 ? 'active' : ''}"><td>2</td><td>1.2 ≤ Fs < 1.5</td><td>中风险</td></tr>
        <tr class="${lsLevel === 3 ? 'active' : ''}"><td>3</td><td>1.0 ≤ Fs < 1.2</td><td>高风险</td></tr>
        <tr class="${lsLevel === 4 ? 'active' : ''}"><td>4</td><td>Fs < 1.0</td><td>极高风险</td></tr>
      </table>
      <p style="margin-top:6px"><strong>CRI 风险等级：</strong></p>
      <table class="criteria-table">
        <tr><th>等级</th><th>CRI 范围</th><th>风险标签</th></tr>
        <tr class="${lsLevel === 1 ? 'active' : ''}"><td>1</td><td>CRI < 0.22</td><td>低风险</td></tr>
        <tr class="${lsLevel === 2 ? 'active' : ''}"><td>2</td><td>0.22 ≤ CRI < 0.28</td><td>中风险</td></tr>
        <tr class="${lsLevel === 3 ? 'active' : ''}"><td>3</td><td>0.28 ≤ CRI < 0.35</td><td>高风险</td></tr>
        <tr class="${lsLevel === 4 ? 'active' : ''}"><td>4</td><td>CRI ≥ 0.35</td><td>极高风险</td></tr>
      </table>
      <p style="margin-top:8px">
        有效降雨量 R<sub>eff</sub>：<strong>${typeof rEff === 'number' ? rEff.toFixed(1) : rEff} mm</strong><br/>
        判定结果：<strong>${criLevelLabel(lsLevel)}滑坡预警</strong>
      </p>
    `
  }
  return '<p>综合风险判定，综合等级：<strong>' + LEVEL_LABELS[level] + '</strong></p>'
})

const disasterTypeDetails = computed(() => {
  if (!detail.value) return []
  const e = detail.value.eval || {}
  return [
    { key: 'landslide', label: '滑坡', level: e.landslide_level ?? 0 },
    { key: 'mudslide', label: '暴雨', level: e.mudslide_level ?? 0 },
    { key: 'freezethaw', label: '高温热浪', level: e.freezethaw_level ?? 0 },
    { key: 'collapse', label: '干旱', level: e.collapse_level ?? 0 }
  ]
})

const suggestions = computed(() => {
  if (!detailRow.value) return []
  const dt = detailRow.value.dominantType
  const level = detailRow.value.level
  const criLevel = detail.value?.eval?.landslide_level || level

  if (dt === 'landslide') {
    const items = []
    if (criLevel >= 3) {
      items.push('加强山区地质灾害巡查排查')
      items.push('发现地面裂缝、树木倾斜等前兆迹象立即报告')
      items.push('居住在陡坡、沟口等危险区域的居民做好转移准备')
      items.push('强降雨期间避免进入山区道路')
      items.push('关注地质灾害气象风险预警信息')
    }
    if (criLevel >= 4) {
      items.push('立即启动地质灾害应急预案')
      items.push('危险区域人员立即转移至安全地带')
      items.push('对重要交通干线进行巡查和管控')
      items.push('安排 24 小时值班监测')
    }
    if (items.length === 0) {
      items.push('持续关注天气变化和地质环境监测数据')
      items.push('定期检查边坡稳定性')
    }
    return items
  }
  if (dt === 'mudslide') {
    return [
      '注意山区滑坡、泥石流风险',
      '做好城市排水工作',
      '避免前往低洼地区'
    ]
  }
  if (dt === 'freezethaw') {
    return [
      '减少高温时段户外活动',
      '注意补水、防暑降温',
      '加强森林防火巡查'
    ]
  }
  if (dt === 'collapse') {
    return [
      '做好农业灌溉工作',
      '加强水资源调度',
      '注意森林火险风险'
    ]
  }
  return ['持续关注各类灾害监测预警信息']
})

onMounted(refresh)
</script>

<style scoped lang="scss">
.alert-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }

/* ---- 统计卡片 ---- */
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

/* ---- Tab 栏 ---- */
.tab-bar {
  display: flex; align-items: center; gap: 4px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--au-border-subtle);
}
.tab-bar-level { border-bottom: none; padding-top: 4px; }
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

/* ---- 表格 ---- */
.alert-table-wrap {
  padding: 0 12px 12px;
  min-height: 300px;
}
:deep(.alert-row) {
  height: 52px;
  td { padding-top: 10px; padding-bottom: 10px; }
}
.action-btns {
  display: flex; align-items: center; gap: 8px;
  white-space: nowrap;
}
.alert-code {
  font-family: var(--au-font-num);
  font-size: 12px;
  color: var(--au-text-secondary);
  padding: 1px 8px;
  border: 1px dashed var(--au-border-base);
  border-radius: 16px;
}
.disaster-type-tag {
  font-size: 12px; padding: 2px 10px; border-radius: 16px; font-weight: 600;
  color: #fff;
  &.dt-landslide { background: #8B4513; }
  &.dt-mudslide { background: #1E88E5; }
  &.dt-freezethaw { background: #E8710A; }
  &.dt-collapse { background: #F5B041; color: #3C4043; }
  &.dt-comp { background: #5F6368; }
}
.level-tag {
  font-size: 12px; padding: 2px 10px; border-radius: 16px; font-weight: 600;
  &.lvl-1 { background: #E8F0FE; color: #1A73E8; }
  &.lvl-2 { background: #FEF7E0; color: #B8860B; }
  &.lvl-3 { background: #FFF3E0; color: #E8710A; }
  &.lvl-4 { background: #FCE8E6; color: #B3261E; font-weight: 700; }
}
.status-tag {
  font-size: 11px; padding: 2px 10px; border-radius: 16px; font-weight: 600;
  &.s-1 { background: #FCE8E6; color: #B3261E; }
  &.s-2 { background: #FEF7E0; color: #E8710A; }
  &.s-3 { background: #E6F4EA; color: #1E8E3E; }
}

.pager { padding: 12px 16px; display: flex; justify-content: flex-end; border-top: 1px solid var(--au-border-subtle); }

/* ---- 详情弹窗 ---- */
.risk-summary-card {
  display: flex; align-items: center; gap: 24px;
  padding: 20px 24px; border-radius: 16px; margin-bottom: 20px;
  &.risk-bg-0 { background: #F1F3F4; }
  &.risk-bg-1 { background: #E8F0FE; }
  &.risk-bg-2 { background: #FEF7E0; }
  &.risk-bg-3 { background: #FFF3E0; }
  &.risk-bg-4 { background: #FCE8E6; }
}
.risk-main {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  min-width: 100px;
}
.risk-level-badge {
  font-size: 20px; font-weight: 700; padding: 6px 24px; border-radius: 24px;
  &.lvl-0 { background: #E8EAED; color: #5F6368; }
  &.lvl-1 { background: #E8F0FE; color: #1A73E8; border: 2px solid #1A73E8; }
  &.lvl-2 { background: #FEF7E0; color: #B8860B; border: 2px solid #E8B300; }
  &.lvl-3 { background: #FFF3E0; color: #E8710A; border: 2px solid #E8710A; }
  &.lvl-4 { background: #FCE8E6; color: #B3261E; border: 2px solid #D93025; }
}
.risk-index {
  text-align: center;
  .risk-index-label { font-size: 11px; color: var(--au-text-secondary); display: block; }
  .risk-index-value { font-size: 22px; font-weight: 700; color: var(--au-text-strong); }
}
.risk-type-list {
  display: flex; gap: 12px; flex: 1; flex-wrap: wrap;
}
.risk-type-item {
  display: flex; flex-direction: column; align-items: center; gap: 4px;
  .risk-type-label { font-size: 11px; color: var(--au-text-secondary); }
}

.detail-dialog {
  .detail-section {
    margin-bottom: 20px;
    &:last-child { margin-bottom: 0; }
  }
  .section-title {
    font-size: 14px; font-weight: 700; color: var(--au-text-strong);
    margin: 0 0 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--au-border-subtle);
  }
  .info-grid {
    display: grid; grid-template-columns: 1fr 1fr; gap: 8px 24px;
  }
  .info-item {
    display: flex; justify-content: space-between; align-items: center;
    padding: 6px 0;
    border-bottom: 1px dashed var(--au-border-subtle);
  }
  .info-label {
    font-size: 12px; color: var(--au-text-secondary);
    flex-shrink: 0; margin-right: 12px;
  }
  .info-value {
    font-size: 13px; color: var(--au-text-strong); font-weight: 500;
    text-align: right;
  }
  .criteria-box {
    font-size: 13px; line-height: 1.7; color: var(--au-text-primary);
    p { margin: 4px 0; }
  }
  .criteria-table {
    width: 100%; border-collapse: collapse; font-size: 12px; margin: 6px 0;
    th, td { border: 1px solid var(--au-border-subtle); padding: 4px 8px; text-align: center; }
    th { background: var(--au-bg-subtle); font-weight: 600; }
    tr.active { background: #FEF7E0; font-weight: 600; }
  }
  .suggest-box {
    background: #FEF7E0;
    border: 1px solid #F9E4B7;
    border-radius: 12px;
    padding: 12px 16px;
    ul { margin: 0; padding-left: 20px; }
    li { font-size: 13px; line-height: 1.8; color: var(--au-text-primary); }
  }
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .alert-page { padding: 10px; gap: 10px; }
  .stats { grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .stat { padding: 12px; gap: 10px; border-radius: 12px;
    .stat-icon { width: 36px; height: 36px; font-size: 18px; }
    .stat-value { font-size: 20px; }
    .stat-label { font-size: 11px; }
  }
  .tab-bar { flex-wrap: wrap; padding: 6px; gap: 2px; }
  .tab { padding: 6px 10px; font-size: 12px; }
  .tab-actions { margin-left: 0; width: 100%; justify-content: flex-end; padding: 4px 0 0; }
  .pager { justify-content: center; padding: 10px; }
  .info-grid { grid-template-columns: 1fr; }
}
</style>
