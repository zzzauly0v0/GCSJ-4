<template>
  <div class="page">
    <!-- 时间轴: 与回放窗口绑定 -->
    <AuTimelineMini />

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
        <el-select v-model="filter.status" placeholder="状态" clearable style="width: 130px" @change="loadPage">
          <el-option label="进行中" :value="1" />
          <el-option label="处置中" :value="2" />
          <el-option label="已结束" :value="3" />
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
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="danger" :icon="BellFilled" @click="onPublishAlert(row)">发布预警</el-button>
            <el-button v-if="row.status === 1" size="small" link type="warning" @click="onChangeStatus(row, 2)">处置中</el-button>
            <el-button v-if="row.status === 2" size="small" link type="success" @click="onChangeStatus(row, 3)">已结束</el-button>
            <el-button v-if="row.status === 3" size="small" link @click="onChangeStatus(row, 1)">重新开启</el-button>
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

    <!-- 发布预警弹窗 -->
    <el-dialog v-model="alertDlg" title="发布预警" width="520px">
      <el-form :model="alertForm" label-width="80px" size="default">
        <el-form-item label="事件">
          <el-input :model-value="alertForm.eventTitle" disabled />
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="alertForm.title" placeholder="留空则用事件标题 + ' 预警'" /></el-form-item>
        <el-form-item label="等级">
          <el-radio-group v-model="alertForm.level">
            <el-radio-button :value="1">蓝色</el-radio-button>
            <el-radio-button :value="2">黄色</el-radio-button>
            <el-radio-button :value="3">橙色</el-radio-button>
            <el-radio-button :value="4">红色</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="通道">
          <el-checkbox-group v-model="alertForm.channels">
            <el-checkbox value="in_site">站内信</el-checkbox>
            <el-checkbox value="sms">短信</el-checkbox>
            <el-checkbox value="email">邮件</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="alertForm.content" type="textarea" :rows="3" placeholder="留空则用事件描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="alertDlg = false">取消</el-button>
        <el-button type="primary" @click="onAlertSubmit">发布</el-button>
      </template>
    </el-dialog>

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
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Plus, BellFilled } from '@element-plus/icons-vue'
import { apiDisasterCreate, apiDisasterChangeStatus } from '@/api/disaster'
import { apiAlertCreateFromEvent } from '@/api/alert'
import { apiReplayEvents } from '@/api/replay'
import { useReplayStore } from '@/store/replay'
import { apiDictionaryByType } from '@/api/dictionary'
import { formatDateTime } from '@/utils/format'
import AlertLevelTag from '@/components/common/AlertLevelTag.vue'
import AuTimelineMini from '@/components/aurora/AuTimelineMini.vue'

const replayStore = useReplayStore()

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const filter = reactive({ keyword: '', type: null, level: null, status: null, page: 1, size: 10 })

const STATUS_LABELS = { 1: '进行中', 2: '处置中', 3: '已结束' }
const STATUS_TAG_TYPES = { 1: 'warning', 2: 'primary', 3: 'info' }
function statusLabel(s) { return STATUS_LABELS[s] || '未知' }
function statusTagType(s) { return STATUS_TAG_TYPES[s] || 'info' }

const dlg = ref(false)
const form = reactive({
  title: '', type: 'landslide', level: 2,
  longitude: 104.0, latitude: 35.0,
  occurredAt: new Date().toISOString().slice(0, 19) + 'Z',
  description: ''
})
const types = ref([])
async function loadTypes() {
  const list = await apiDictionaryByType('disaster_type')
  types.value = (list || []).map(d => ({ label: d.itemValue, value: d.itemCode }))
  if (types.value.length && !types.value.find(t => t.value === form.type)) {
    form.type = types.value[0].value
  }
}
function typeLabel(v) { return types.value.find(t => t.value === v)?.label || v }

/**
 * 数据来源: 回放灾害事件 GeoJSON (按虚拟时刻过滤)
 * 客户端再做 keyword/type/level/status 过滤 + 分页
 */
async function loadPage() {
  await replayStore.init()
  const at = replayStore.virtualNowIso
  if (!at) return
  loading.value = true
  try {
    const geo = await apiReplayEvents(at)
    const all = (geo?.features || []).map(f => {
      const p = f.properties || {}
      const c = f.geometry?.coordinates || [null, null]
      return {
        id: p.id,
        code: p.code,
        title: p.title,
        description: p.description,
        type: p.type,
        level: p.level,
        regionCode: p.regionCode,
        occurredAt: p.occurredAt,
        longitude: c[0],
        latitude:  c[1],
        status: 1,
      }
    })
    let filtered = all
    if (filter.keyword) filtered = filtered.filter(r => (r.title || '').includes(filter.keyword) || (r.description || '').includes(filter.keyword))
    if (filter.type)   filtered = filtered.filter(r => r.type === filter.type)
    if (filter.level)  filtered = filtered.filter(r => r.level === filter.level)
    if (filter.status) filtered = filtered.filter(r => r.status === filter.status)
    total.value = filtered.length
    rows.value  = filtered.slice((filter.page - 1) * filter.size, filter.page * filter.size)
  } finally { loading.value = false }
}

let _disasterDebounce = null
watch(() => replayStore.virtualNow, () => {
  if (_disasterDebounce) return
  _disasterDebounce = setTimeout(() => { _disasterDebounce = null; loadPage() }, 800)
})

function onAdd() { dlg.value = true }
async function onSave() {
  await apiDisasterCreate(form)
  ElMessage.success('已新增')
  dlg.value = false
  loadPage()
}

async function onChangeStatus(row, next) {
  await apiDisasterChangeStatus(row.id, next)
  ElMessage.success(`已变更为「${statusLabel(next)}」`)
  loadPage()
}

const alertDlg = ref(false)
const alertForm = reactive({
  eventId: null, eventTitle: '', title: '', level: 2, content: '', channels: ['in_site']
})
function onPublishAlert(row) {
  Object.assign(alertForm, {
    eventId: row.id,
    eventTitle: row.title,
    title: '',
    level: row.level || 2,
    content: '',
    channels: ['in_site']
  })
  alertDlg.value = true
}
async function onAlertSubmit() {
  await apiAlertCreateFromEvent(alertForm.eventId, {
    level: alertForm.level,
    title: alertForm.title || undefined,
    content: alertForm.content || undefined,
    channels: alertForm.channels
  })
  ElMessage.success('预警已发布')
  alertDlg.value = false
}

onMounted(() => {
  loadTypes()
  loadPage()
})
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
  font-size: 12px; padding: 2px 8px; border-radius: 16px;
  background: rgb(248, 249, 252); color: var(--au-text-strong);
  border: 1px solid var(--au-border-subtle);
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
  .toolbar-left,
  .toolbar-right { width: 100%; flex-wrap: wrap; }
  .toolbar-right { justify-content: flex-end; }
  .row-title .title-desc { max-width: 60vw; }
  .pager { justify-content: center; padding: 10px; }
}
</style>
