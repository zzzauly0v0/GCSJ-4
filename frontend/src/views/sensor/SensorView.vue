<template>
  <div class="page">
    <!-- 顶部统计 -->
    <div class="stat-row">
      <div v-for="s in statBlocks" :key="s.key" class="stat" :style="{ '--c': s.color }">
        <div class="stat-icon"><el-icon><component :is="s.icon" /></el-icon></div>
        <div class="stat-body">
          <div class="stat-value num-mono">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="gcsj-card toolbar">
      <div class="toolbar-left">
        <el-input v-model="filter.keyword" placeholder="搜索编码/名称" clearable :prefix-icon="Search" style="width: 240px" @change="loadPage" />
        <el-select v-model="filter.type" placeholder="类型" clearable style="width: 160px" @change="loadPage">
          <el-option label="雨量计" value="rain_gauge" />
          <el-option label="位移监测" value="displacement" />
          <el-option label="土壤含水率" value="soil_moisture" />
          <el-option label="气象站" value="weather_station" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-button :icon="Refresh" @click="loadPage" />
        <el-button type="primary" :icon="Plus" @click="onAdd">新增传感器</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="gcsj-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="code" label="编码" width="130">
          <template #default="{ row }"><span class="num-mono fs-13">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="type" label="类型" width="130">
          <template #default="{ row }">
            <span class="type-tag" :class="`t-${row.type}`">{{ typeLabel(row.type) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="经纬度" width="200">
          <template #default="{ row }">
            <span class="num-mono fs-12 text-2">{{ row.longitude?.toFixed(4) }}, {{ row.latitude?.toFixed(4) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-pill" :class="row.status === 1 ? 'on' : 'off'">
              <span class="led" />
              {{ row.status === 1 ? '在线' : '离线' }}
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

    <!-- 表单弹窗 -->
    <el-dialog v-model="dlg" :title="form.id ? '编辑传感器' : '新增传感器'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="雨量计" value="rain_gauge" />
            <el-option label="位移监测" value="displacement" />
            <el-option label="土壤含水率" value="soil_moisture" />
            <el-option label="气象站" value="weather_station" />
          </el-select>
        </el-form-item>
        <el-form-item label="经度"><el-input-number v-model="form.longitude" :precision="6" style="width: 100%" /></el-form-item>
        <el-form-item label="纬度"><el-input-number v-model="form.latitude" :precision="6" style="width: 100%" /></el-form-item>
        <el-form-item label="高程"><el-input-number v-model="form.elevation" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="form.unit" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Plus, Cpu, Connection, Histogram, CircleClose } from '@element-plus/icons-vue'
import { apiSensorPage, apiSensorCreate, apiSensorUpdate, apiSensorDelete, apiSensorAll } from '@/api/sensor'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const allSensors = ref([])
const filter = reactive({ keyword: '', type: null, page: 1, size: 10 })
const dlg = ref(false)
const form = reactive({ id: null, code: '', name: '', type: 'rain_gauge', longitude: 104, latitude: 35, elevation: 0, unit: 'mm', description: '' })

const TYPE_MAP = {
  rain_gauge: '雨量计',
  displacement: '位移监测',
  soil_moisture: '土壤含水率',
  weather_station: '气象站'
}
function typeLabel(t) { return TYPE_MAP[t] || t }

const statBlocks = computed(() => {
  const total = allSensors.value.length
  const online = allSensors.value.filter(s => s.status === 1).length
  return [
    { key: 'total',  label: '传感器总数', value: total,           color: '#3B82F6', icon: 'Cpu' },
    { key: 'online', label: '在线',       value: online,          color: '#22C55E', icon: 'Connection' },
    { key: 'offline',label: '离线',       value: total - online,  color: '#EF4444', icon: 'CircleClose' },
    { key: 'rate',   label: '在线率',     value: total ? Math.round(online / total * 100) + '%' : '0%', color: '#F59E0B', icon: 'Histogram' }
  ]
})

async function loadPage() {
  loading.value = true
  try {
    const data = await apiSensorPage(filter)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally { loading.value = false }
}
async function loadStat() { allSensors.value = await apiSensorAll() }

function reset() { Object.assign(form, { id: null, code: '', name: '', type: 'rain_gauge', longitude: 104, latitude: 35, elevation: 0, unit: 'mm', description: '' }) }
function onAdd() { reset(); dlg.value = true }
function onEdit(row) { Object.assign(form, row); dlg.value = true }
async function onSave() {
  if (form.id) await apiSensorUpdate(form.id, form)
  else await apiSensorCreate(form)
  ElMessage.success('已保存')
  dlg.value = false
  loadPage(); loadStat()
}
async function onDel(row) {
  await ElMessageBox.confirm(`删除 [${row.name}]?`, '确认操作', { type: 'warning' })
  await apiSensorDelete(row.id)
  ElMessage.success('已删除')
  loadPage(); loadStat()
}

onMounted(() => { loadPage(); loadStat() })
</script>

<style scoped lang="scss">
.page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }

.stat-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.stat {
  --c: #3B82F6;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  position: relative;
  &::before { content: ''; position: absolute; left: 0; top: 14px; bottom: 14px; width: 3px; background: var(--c); border-radius: 0 2px 2px 0; }
  .stat-icon {
    width: 44px; height: 44px; border-radius: 8px;
    background: color-mix(in srgb, var(--c) 12%, white);
    color: var(--c);
    display: flex; align-items: center; justify-content: center;
    font-size: 22px;
  }
  .stat-value { font-size: 24px; font-weight: 700; color: var(--text-1); line-height: 1; }
  .stat-label { font-size: 12px; color: var(--text-3); margin-top: 4px; }
}

.toolbar { display: flex; justify-content: space-between; padding: 14px 18px; }
.toolbar-left { display: flex; gap: 10px; }
.toolbar-right { display: flex; gap: 8px; }

.type-tag {
  font-size: 12px; padding: 2px 8px; border-radius: 10px;
  background: rgba(37, 99, 235, 0.08); color: var(--primary);
  &.t-displacement { background: rgba(245, 158, 11, 0.1); color: #D97706; }
  &.t-soil_moisture { background: rgba(34, 197, 94, 0.1); color: #16A34A; }
  &.t-weather_station { background: rgba(99, 102, 241, 0.1); color: #4F46E5; }
}

.status-pill {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; padding: 2px 10px; border-radius: 10px; font-weight: 500;
  .led { width: 6px; height: 6px; border-radius: 50%; }
  &.on  { background: rgba(34,197,94,0.1); color: #16A34A; .led { background: #22C55E; box-shadow: 0 0 6px #22C55E; } }
  &.off { background: rgba(148,163,184,0.1); color: #64748B; .led { background: #94A3B8; } }
}

.pager { padding: 14px; display: flex; justify-content: flex-end; }
</style>
