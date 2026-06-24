# 区间回放 + WeatherLab 风格地图大屏 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复监测大屏回放从 1970 开始的 Bug, 改为基于真实日数据 (2020–2023) 的区间可选回放, 并把大屏重构为「数据地图 / KPI 信息」顶部 Tab 切换的 WeatherLab 风格。

**Architecture:** 后端在 `DisasterEvalController` 新增 `/window` 与 `/snapshot` 两个只读接口 (JdbcTemplate 直查 `biz_weather_daily` + `biz_disaster_eval`)。前端 `store/replay.js` 改为日尺度区间回放, `useReplayLayers.js` 切到日数据快照, `AuroraDashboard.vue` 重构为顶部 Tab + 浮层化地图 + 独立 KPI 区, `AuTimelinePlayer.vue` 改造为年份/区间可选的日尺度 scrubber。

**Tech Stack:** Spring Boot (JdbcTemplate, PostgreSQL/PostGIS), Vue 3 (`<script setup>`), Pinia, OpenLayers 9, ECharts 5, Vite。

## Global Constraints

- 后端沿用 `JdbcTemplate` 直查模式, 不引入 entity/service/repo (与 `ReplayController`/`DisasterEvalController` 一致)。
- 后端接口返回统一用 `com.gcsj.disaster.common.Result.ok(...)`。
- 新接口走 `/api/disaster-eval/**`, 该前缀已 `permitAll` (无需 token)。
- 前端不新增路由; 监测大屏仍是 `/dashboard` → `DashboardView` → `AuroraDashboard`。
- 未路由的 `MonitorDashboard.vue` 不改动。
- 旧 `ReplayController` 逐时接口与 `api/replay.js` 旧导出保留 (不删除), 仅大屏回放路径不再调用。
- 前端无单元测试框架; 前端任务用 `npm run lint` + `npm run build` + 手动浏览器验证。命令在 `frontend/` 目录下执行。
- 后端构建/测试用 `mvn` (无 wrapper), 在 `backend/` 目录下执行。
- 默认回放窗口 = 全部年份 (2020–2023), 年份 `0` 表示全部。
- 回放以「天」为步进单位, 当前时刻对外用 `YYYY-MM-DD` 字符串。
- 等级配色统一: `1蓝 #3B82F6 / 2黄 #F59E0B / 3橙 #F97316 / 4红 #DC2626`。

---

## File Structure

**后端 (修改):**
- `backend/src/main/java/com/gcsj/disaster/controller/DisasterEvalController.java` — 新增 `window()` `snapshot()` 两个方法。

**前端 (修改):**
- `frontend/src/api/replay.js` — 新增 2 个日尺度 API 导出。
- `frontend/src/store/replay.js` — 重写为日尺度区间回放 store。
- `frontend/src/hooks/useReplayLayers.js` — `refresh()` 切到日快照数据源。
- `frontend/src/components/aurora/AuTimelinePlayer.vue` — 改造为年份/区间 scrubber。
- `frontend/src/views/dashboard/AuroraDashboard.vue` — 顶部 Tab、浮层化地图、独立 KPI 区、WeatherLab 皮肤、KPI 改用 snapshot。

**前端 (新增):**
- `frontend/src/components/aurora/AuTabSwitch.vue` — 轻量两段式 Tab 切换组件。

---

## Task 1: 后端新增 `/disaster-eval/window` 接口 (真实数据窗口源)

**Files:**
- Modify: `backend/src/main/java/com/gcsj/disaster/controller/DisasterEvalController.java`

**Interfaces:**
- Produces: `GET /api/disaster-eval/window?year=<int|absent>` →
  `Result<Map>`，data 形如
  `{ startAt: "2020-01-01", endAt: "2023-12-31", years: [2020,2021,2022,2023], description: "..." }`。
  `startAt/endAt` 为 `java.sql.Date` (JSON 序列化成 `YYYY-MM-DD`)。

- [ ] **Step 1: 在 `DisasterEvalController` 末尾 (最后一个方法 `heatmap` 之后、`toD` 之前) 新增 `window` 方法**

在 `private static Double toD(Object o) {` 这一行之前插入:

```java
    /** 回放窗口元信息: 真实日数据的起止日期 + 可选年份 (替代空表 ReplayController.window) */
    @Operation(summary = "回放窗口元信息 (基于 biz_weather_daily 真实日期)")
    @GetMapping("/window")
    public Result<Map<String, Object>> window(@RequestParam(required = false) Integer year) {
        String yearFilter = year != null ? " WHERE EXTRACT(YEAR FROM obs_date) = " + year + " " : "";
        Map<String, Object> range = jdbc.queryForMap(
                "SELECT MIN(obs_date) AS start_at, MAX(obs_date) AS end_at " +
                "FROM biz.biz_weather_daily" + yearFilter);

        List<Integer> years = jdbc.queryForList(
                "SELECT DISTINCT EXTRACT(YEAR FROM obs_date)::int AS y " +
                "FROM biz.biz_weather_daily ORDER BY y", Integer.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startAt", range.get("start_at"));
        out.put("endAt", range.get("end_at"));
        out.put("years", years);
        out.put("description", year != null
                ? (year + " 年四川逐日气象灾害回放")
                : "2020–2023 四川逐日气象灾害回放");
        return Result.ok(out);
    }
```

- [ ] **Step 2: 编译后端验证通过**

Run: `cd backend && mvn -q -o compile`
Expected: BUILD SUCCESS (无编译错误)。若离线依赖缺失去掉 `-o`。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/gcsj/disaster/controller/DisasterEvalController.java
git commit -m "feat(disaster-eval): 新增 /window 接口 (真实日数据窗口源, 修复回放 1970)"
```

---

## Task 2: 后端新增 `/disaster-eval/snapshot` 接口 (某日全站快照)

**Files:**
- Modify: `backend/src/main/java/com/gcsj/disaster/controller/DisasterEvalController.java`

**Interfaces:**
- Produces: `GET /api/disaster-eval/snapshot?date=YYYY-MM-DD` →
  `Result<List<Map>>`，每行
  `{ code, name, region_code, region_name, lon (double), lat (double), rainfall (numeric|null), comp_level (int, 0 表无判别), r_eff (numeric|null) }`。
  按 `comp_level DESC, rainfall DESC` 排序。

- [ ] **Step 1: 在 `window` 方法之后、`toD` 之前新增 `snapshot` 方法**

```java
    /** 某一天全部气象站快照: 经纬度 + 当日雨量 + 综合风险等级 (供地图热力/染色/风险圈) */
    @Operation(summary = "某日全站快照 (供回放地图)")
    @GetMapping("/snapshot")
    public Result<List<Map<String, Object>>> snapshot(@RequestParam String date) {
        return Result.ok(jdbc.queryForList("""
            SELECT s.code, s.name, s.region_code, r.name AS region_name,
                   ST_X(s.location) AS lon, ST_Y(s.location) AS lat,
                   w.rainfall,
                   COALESCE(e.comp_level, 0) AS comp_level,
                   e.r_eff
            FROM   biz.biz_monitor_station s
            LEFT   JOIN biz.biz_weather_daily w
                   ON w.station_code = s.code AND w.obs_date = ?::date
            LEFT   JOIN biz.biz_disaster_eval e
                   ON e.station_code = s.code AND e.obs_date = ?::date
            WHERE  s.type = 'weather'
            ORDER BY comp_level DESC, w.rainfall DESC NULLS LAST
        """, date, date));
    }
```

- [ ] **Step 2: 编译后端验证通过**

Run: `cd backend && mvn -q -o compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: (可选, 若本地有库) 手动 curl 验证**

Run (后端已启动时): `curl "http://localhost:8080/api/disaster-eval/snapshot?date=2022-09-06"`
Expected: 返回 `{"code":...,"data":[{"code":...,"lon":...,"comp_level":...}, ...]}`。无库可跳过, 编译通过即可。

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/gcsj/disaster/controller/DisasterEvalController.java
git commit -m "feat(disaster-eval): 新增 /snapshot 接口 (某日全站快照供回放地图)"
```

---

## Task 3: 前端新增日尺度 replay API

**Files:**
- Modify: `frontend/src/api/replay.js`

**Interfaces:**
- Consumes: Task 1/2 的 `/disaster-eval/window`、`/disaster-eval/snapshot`。
- Produces:
  - `apiReplayWindowDaily(year?)` → Promise，resolve 为 window data 对象。
  - `apiReplaySnapshotDaily(date)` → Promise，resolve 为快照数组。

- [ ] **Step 1: 在 `frontend/src/api/replay.js` 末尾追加两个导出**

在文件最后 (`apiReplayGeoSeries` 定义之后) 追加:

```js

/* ---- 日尺度回放 (真实 biz_weather_daily 数据, 走 /disaster-eval/**) ---- */
export const apiReplayWindowDaily   = (year)  => request.get('/disaster-eval/window', { params: year ? { year } : {} })
export const apiReplaySnapshotDaily = (date)  => request.get('/disaster-eval/snapshot', { params: { date } })
```

- [ ] **Step 2: Lint 验证**

Run: `cd frontend && npm run lint`
Expected: 无新增 error (warning 可接受)。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/replay.js
git commit -m "feat(replay): 新增日尺度回放 API (window/snapshot)"
```

---

## Task 4: 重写 `store/replay.js` 为日尺度区间回放

**Files:**
- Modify: `frontend/src/store/replay.js`

**Interfaces:**
- Consumes: `apiReplayWindowDaily` (Task 3)。
- Produces: `useReplayStore` 暴露:
  - state: `years[]`, `year` (0=全部), `windowStart`/`windowEnd` (ISO date 串),
    `virtualNow` (Date), `stepDays`, `playing`, `ready`, `description`。
  - getters: `progress` (0..1), `virtualNowDate` (`YYYY-MM-DD`)。
  - actions: `init()`, `selectYear(y)`, `setRange(start,end)`, `start()`, `pause()`,
    `toggle()`, `tick(steps?)`, `setSpeed(days)`, `seek(p01)`, `reset()`, `setPeakDate(dateStr)`, `jumpToPeak()`。
  - `peakDate` (ISO date 串或 null) — 由外部 `setPeakDate` 注入 (来自 summary.topEvents)。

- [ ] **Step 1: 用以下完整内容替换 `frontend/src/store/replay.js`**

```js
/**
 * 时间轴回放 Pinia store (日尺度)
 *
 * 基于真实日数据 biz_weather_daily (2020–2023)。
 * virtualNow 在 [windowStart, windowEnd] 区间内按「天」行进。
 * year=0 表示全部年份; selectYear / setRange 切换回放窗口。
 *
 * 用法:
 *   const replay = useReplayStore()
 *   await replay.init()
 *   watch(() => replay.virtualNow, () => loadByDate(replay.virtualNowDate))
 */
import { defineStore } from 'pinia'
import { apiReplayWindowDaily } from '@/api/replay'

const DAY_MS = 24 * 60 * 60 * 1000

/** Date -> YYYY-MM-DD (按本地日期, 不做时区偏移) */
function toDateStr(d) {
  if (!d) return null
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** YYYY-MM-DD 串 -> 当地 00:00 的 Date */
function parseDate(s) {
  if (!s) return null
  const [y, m, d] = String(s).slice(0, 10).split('-').map(Number)
  return new Date(y, (m || 1) - 1, d || 1)
}

export const useReplayStore = defineStore('replay', {
  state: () => ({
    /** 可选年份列表 (来自 window 接口) */
    years: [],
    /** 当前选中年份, 0 = 全部 (默认) */
    year: 0,

    /** 窗口起止 (YYYY-MM-DD 串) */
    windowStart: null,
    windowEnd: null,
    description: '',

    /** 当前虚拟日期 (Date 对象, 当地 00:00) */
    virtualNow: null,

    /** 每 tick 推进的天数 (倍速) */
    stepDays: 1,

    /** 最高风险日 (YYYY-MM-DD), 由外部注入 */
    peakDate: null,

    playing: false,
    _timer: null,
    ready: false,
  }),
  getters: {
    progress(state) {
      const a = parseDate(state.windowStart)?.getTime()
      const b = parseDate(state.windowEnd)?.getTime()
      if (a == null || b == null || !state.virtualNow || b === a) return 0
      const t = state.virtualNow.getTime()
      return Math.max(0, Math.min(1, (t - a) / (b - a)))
    },
    /** 给后端 snapshot 接口的日期串 */
    virtualNowDate(state) {
      return toDateStr(state.virtualNow)
    },
  },
  actions: {
    async _loadWindow(year) {
      const w = await apiReplayWindowDaily(year || undefined)
      this.windowStart = w.startAt ? String(w.startAt).slice(0, 10) : null
      this.windowEnd = w.endAt ? String(w.endAt).slice(0, 10) : null
      this.description = w.description || ''
      if (Array.isArray(w.years) && w.years.length) this.years = w.years
      this.virtualNow = parseDate(this.windowStart)
    },
    async init() {
      if (this.ready) return
      await this._loadWindow(0)
      this.ready = true
    },
    async selectYear(y) {
      this.pause()
      this.year = y || 0
      await this._loadWindow(this.year)
    },
    setRange(start, end) {
      this.pause()
      this.year = 0
      this.windowStart = start ? String(start).slice(0, 10) : this.windowStart
      this.windowEnd = end ? String(end).slice(0, 10) : this.windowEnd
      this.virtualNow = parseDate(this.windowStart)
    },
    start() {
      if (!this.ready || this.playing) return
      this.playing = true
      this._timer = setInterval(() => this.tick(), 1000)
    },
    pause() {
      this.playing = false
      if (this._timer) {
        clearInterval(this._timer)
        this._timer = null
      }
    },
    toggle() {
      this.playing ? this.pause() : this.start()
    },
    tick(steps = 1) {
      if (!this.virtualNow) return
      const end = parseDate(this.windowEnd)?.getTime()
      const next = new Date(this.virtualNow.getTime() + steps * this.stepDays * DAY_MS)
      if (end != null && next.getTime() >= end) {
        this.virtualNow = new Date(end)
        this.pause()
        return
      }
      this.virtualNow = next
    },
    setSpeed(days) {
      this.stepDays = days
    },
    seek(progress01) {
      const a = parseDate(this.windowStart)?.getTime()
      const b = parseDate(this.windowEnd)?.getTime()
      if (a == null || b == null) return
      const t = a + (b - a) * Math.max(0, Math.min(1, progress01))
      // 吸附到整天
      this.virtualNow = new Date(Math.round(t / DAY_MS) * DAY_MS)
    },
    setPeakDate(dateStr) {
      this.peakDate = dateStr ? String(dateStr).slice(0, 10) : null
    },
    jumpToPeak() {
      const target = this.peakDate || this.windowStart
      const d = parseDate(target)
      if (d) this.virtualNow = d
    },
    reset() {
      this.pause()
      this.virtualNow = parseDate(this.windowStart)
    },
  },
})
```

- [ ] **Step 2: Lint 验证**

Run: `cd frontend && npm run lint`
Expected: 无新增 error。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/store/replay.js
git commit -m "refactor(replay): store 改为日尺度区间回放 (年份/自定义区间, 修复 1970)"
```

---

## Task 5: `useReplayLayers.js` 切到日快照数据源

**Files:**
- Modify: `frontend/src/hooks/useReplayLayers.js`

**Interfaces:**
- Consumes: `apiReplaySnapshotDaily` (Task 3)。
- Produces: `useReplayLayers(olMapRef)` 仍返回 `{ attach, detach, refresh, toggle, layers }`，
  但 `refresh(dateStr)` 改为接受 `YYYY-MM-DD`，单次拉日快照驱动热力/站点/风险圈。

- [ ] **Step 1: 替换 import 段**

把文件顶部的:

```js
import {
  apiReplayWeatherSnapshot,
  apiReplayEvents,
} from '@/api/replay'
```

替换为:

```js
import { apiReplaySnapshotDaily } from '@/api/replay'
```

- [ ] **Step 2: 替换 `refresh` 函数 (整段)**

把现有 `async function refresh(atIso) { ... }` 整个函数体替换为:

```js
  async function refresh(dateStr) {
    if (!attached || !dateStr) return
    const olMap = olMapRef.value
    if (!olMap) return

    const snapshot = await apiReplaySnapshotDaily(dateStr).catch(() => [])

    const heatSrc = layers.heatmap.getSource()
    const stationSrc = layers.stations.getSource()
    const impactSrc = layers.impact.getSource()
    const dotSrc = layers.eventDot.getSource()
    heatSrc.clear(); stationSrc.clear(); impactSrc.clear(); dotSrc.clear()

    ;(snapshot || []).forEach(s => {
      if (s.lon == null || s.lat == null) return
      const center3857 = fromLonLat([s.lon, s.lat])

      // 热力: 当日 rainfall 作权重
      const heatFeat = new Feature({ geometry: new Point(center3857) })
      heatFeat.set('weight', rainWeight(s.rainfall))
      heatSrc.addFeature(heatFeat)

      // 站点染色: 当日 rainfall 分级 (复用 1h 阈值近似日尺度展示)
      const stFeat = new Feature({ geometry: new Point(center3857) })
      stFeat.set('rainfall_1h', s.rainfall)
      stFeat.set('rainfall_24h', s.rainfall)
      stFeat.set('code', s.code)
      stFeat.set('name', s.name)
      stationSrc.addFeature(stFeat)

      // 风险圈 + 中心点: comp_level >= 1 的站
      const level = Number(s.comp_level) || 0
      if (level >= 1) {
        const cf = new Feature({ geometry: new Circle(center3857, impactRadiusByLevel(level)) })
        cf.set('level', level)
        cf.set('title', s.name)
        impactSrc.addFeature(cf)

        const df = new Feature({ geometry: new Point(center3857) })
        df.set('level', level)
        df.set('title', s.name)
        df.set('code', s.code)
        dotSrc.addFeature(df)
      }
    })
  }
```

- [ ] **Step 3: Lint 验证**

Run: `cd frontend && npm run lint`
Expected: 无新增 error (注意 `apiReplayEvents` 不再在本文件 import — 确认无残留引用)。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/useReplayLayers.js
git commit -m "refactor(replay-layers): refresh 切到日快照数据源 (rainfall/comp_level 驱动)"
```

---

## Task 6: 新增 `AuTabSwitch.vue` Tab 切换组件

**Files:**
- Create: `frontend/src/components/aurora/AuTabSwitch.vue`

**Interfaces:**
- Produces: `<AuTabSwitch v-model="activeTab" :tabs="[{value,label}]" />`。
  `v-model` 为当前选中 tab 的 `value`，点击 tab emit `update:modelValue`。

- [ ] **Step 1: 创建 `frontend/src/components/aurora/AuTabSwitch.vue`**

```vue
<template>
  <div class="au-tabs" role="tablist">
    <button
      v-for="t in tabs"
      :key="t.value"
      class="au-tab"
      :class="{ active: modelValue === t.value }"
      role="tab"
      :aria-selected="modelValue === t.value"
      @click="$emit('update:modelValue', t.value)"
    >
      <span v-if="t.icon" class="au-tab-icon">{{ t.icon }}</span>
      {{ t.label }}
    </button>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: [String, Number], required: true },
  tabs: { type: Array, required: true }, // [{ value, label, icon? }]
})
defineEmits(['update:modelValue'])
</script>

<style scoped>
.au-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: var(--au-bg-subtle);
  border: 1px solid var(--au-border-subtle);
  border-radius: 999px;
}
.au-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  font-size: 13px;
  font-weight: 600;
  color: var(--au-text-secondary);
  background: transparent;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: color var(--au-dur-fast) var(--au-ease),
              background var(--au-dur-fast) var(--au-ease);
}
.au-tab:hover { color: var(--au-text-primary); }
.au-tab.active {
  color: #fff;
  background: var(--au-grad-primary, linear-gradient(135deg, #2563EB, #06B6D4));
  box-shadow: var(--au-shadow-sm);
}
.au-tab-icon { font-size: 14px; line-height: 1; }
</style>
```

- [ ] **Step 2: Lint 验证**

Run: `cd frontend && npm run lint`
Expected: 无新增 error。

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/aurora/AuTabSwitch.vue
git commit -m "feat(aurora): 新增 AuTabSwitch 两段式 Tab 切换组件"
```

---

## Task 7: 改造 `AuTimelinePlayer.vue` 为年份/区间日尺度 scrubber

**Files:**
- Modify: `frontend/src/components/aurora/AuTimelinePlayer.vue`

**Interfaces:**
- Consumes: `useReplayStore` (Task 4) 的新 API: `years`, `year`, `selectYear`,
  `setRange`, `windowStart`, `windowEnd`, `virtualNowDate`, `progress`, `stepDays`,
  `setSpeed`, `toggle`, `reset`, `tick`, `jumpToPeak`, `peakDate`。
- Produces: 自给自足的播放器组件 (无对外 props/emit 变化)。

- [ ] **Step 1: 用以下完整内容替换 `frontend/src/components/aurora/AuTimelinePlayer.vue` 的 `<template>` 与 `<script setup>`**

替换 `<template> ... </template>`:

```vue
<template>
  <div class="au-tl">
    <!-- 第一行: 年份快选 + 自定义区间 -->
    <div class="au-tl-range">
      <div class="au-tl-years">
        <button
          class="year-pill"
          :class="{ active: replay.year === 0 }"
          @click="onYear(0)"
        >全部</button>
        <button
          v-for="y in replay.years"
          :key="y"
          class="year-pill"
          :class="{ active: replay.year === y }"
          @click="onYear(y)"
        >{{ y }}</button>
      </div>
      <div class="au-tl-custom">
        <input type="date" class="date-input" v-model="customStart" :min="replay.windowStart" :max="replay.windowEnd" />
        <span class="tilde">~</span>
        <input type="date" class="date-input" v-model="customEnd" :min="replay.windowStart" :max="replay.windowEnd" />
        <button class="apply-btn" @click="onApplyRange">应用</button>
      </div>
    </div>

    <!-- 第二行: 播放控制 + 进度 -->
    <div class="au-tl-main">
      <div class="au-tl-btns">
        <button class="ctrl-btn" @click="replay.reset()" title="回到起点">⏮</button>
        <button class="ctrl-btn" @click="replay.tick(-1)" title="上一天">◀</button>
        <button class="ctrl-btn primary" @click="replay.toggle()">
          {{ replay.playing ? '⏸' : '▶' }}
        </button>
        <button class="ctrl-btn" @click="replay.tick(1)" title="下一天">▶</button>
        <button class="ctrl-btn" @click="replay.jumpToPeak()" title="跳到最高风险日">⚡ 最高风险日</button>
      </div>

      <span class="au-tl-date">{{ replay.virtualNowDate || '--' }}</span>

      <div class="au-tl-bar" @click="onSeek" ref="barRef">
        <div class="au-tl-bar-bg"></div>
        <div class="au-tl-bar-fill" :style="{ width: `${replay.progress * 100}%` }"></div>
        <div
          v-if="peakProgress != null"
          class="au-tl-anchor"
          :style="{ left: `${peakProgress * 100}%` }"
          title="最高风险日"
        ></div>
        <div class="au-tl-thumb" :style="{ left: `${replay.progress * 100}%` }"></div>
      </div>

      <div class="au-tl-speed">
        <span class="speed-label">倍速</span>
        <button
          v-for="s in speedOptions"
          :key="s.value"
          class="speed-btn"
          :class="{ active: replay.stepDays === s.value }"
          @click="replay.setSpeed(s.value)"
        >{{ s.label }}</button>
      </div>
    </div>
  </div>
</template>
```

替换 `<script setup> ... </script>`:

```vue
<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useReplayStore } from '@/store/replay'

const replay = useReplayStore()
const barRef = ref(null)
const customStart = ref('')
const customEnd = ref('')

// 倍速以「天/秒」为单位
const speedOptions = [
  { value: 1,  label: '1天' },
  { value: 3,  label: '3天' },
  { value: 7,  label: '7天' },
  { value: 30, label: '30天' },
]

const peakProgress = computed(() => {
  const a = replay.windowStart ? new Date(replay.windowStart).getTime() : null
  const b = replay.windowEnd ? new Date(replay.windowEnd).getTime() : null
  const q = replay.peakDate ? new Date(replay.peakDate).getTime() : null
  if (a == null || b == null || q == null || b === a) return null
  const p = (q - a) / (b - a)
  if (p < 0 || p > 1) return null
  return p
})

async function onYear(y) {
  await replay.selectYear(y)
  syncCustomInputs()
}

function onApplyRange() {
  if (customStart.value && customEnd.value && customStart.value <= customEnd.value) {
    replay.setRange(customStart.value, customEnd.value)
  }
}

function syncCustomInputs() {
  customStart.value = replay.windowStart || ''
  customEnd.value = replay.windowEnd || ''
}

function onSeek(e) {
  if (!barRef.value) return
  const rect = barRef.value.getBoundingClientRect()
  replay.seek((e.clientX - rect.left) / rect.width)
}

onMounted(async () => {
  await replay.init()
  syncCustomInputs()
})

// 窗口变化 (selectYear/setRange) 时同步 date input
watch(() => [replay.windowStart, replay.windowEnd], syncCustomInputs)
</script>
```

- [ ] **Step 2: 替换 `<style scoped>` 内容 (适配新结构 + WeatherLab 风格)**

把整个 `<style scoped> ... </style>` 替换为:

```vue
<style scoped>
.au-tl {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--au-card-bg, rgba(255, 255, 255, 0.92));
  border: 1px solid var(--au-border, #E5E7EB);
  border-radius: var(--au-radius-md, 12px);
  padding: 12px 16px;
  box-shadow: var(--au-shadow-sm, 0 2px 6px rgba(15, 23, 42, 0.04));
  font-family: var(--au-font-sans, system-ui);
  backdrop-filter: blur(8px);
}

/* ---- 年份 + 区间 ---- */
.au-tl-range {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.au-tl-years { display: flex; gap: 6px; flex-wrap: wrap; }
.year-pill {
  padding: 4px 14px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid #E5E7EB;
  background: #fff;
  color: #64748B;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.year-pill:hover { border-color: #93C5FD; color: #1D4ED8; }
.year-pill.active {
  background: linear-gradient(135deg, #2563EB, #06B6D4);
  color: #fff;
  border-color: transparent;
}
.au-tl-custom { display: flex; align-items: center; gap: 6px; }
.date-input {
  font-size: 12px;
  padding: 4px 8px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  color: #475569;
  font-family: var(--au-font-mono, monospace);
}
.tilde { color: #94A3B8; font-size: 12px; }
.apply-btn {
  padding: 4px 12px;
  font-size: 12px;
  border: 1px solid #93C5FD;
  background: #EFF6FF;
  color: #1D4ED8;
  border-radius: 8px;
  cursor: pointer;
}
.apply-btn:hover { background: #DBEAFE; }

/* ---- 播放主行 ---- */
.au-tl-main {
  display: flex;
  align-items: center;
  gap: 14px;
}
.au-tl-btns { display: flex; gap: 6px; flex-shrink: 0; }
.ctrl-btn {
  padding: 6px 10px;
  font-size: 12px;
  border: 1px solid #E5E7EB;
  border-radius: 8px;
  background: #F8FAFC;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.ctrl-btn:hover { background: #EFF6FF; border-color: #93C5FD; color: #1D4ED8; }
.ctrl-btn.primary {
  background: linear-gradient(135deg, #2563EB, #06B6D4);
  color: #fff;
  border: none;
  font-weight: 600;
  padding: 6px 14px;
}
.ctrl-btn.primary:hover { filter: brightness(1.08); }

.au-tl-date {
  font-family: var(--au-font-mono, ui-monospace);
  font-size: 15px;
  font-weight: 700;
  color: #1E293B;
  flex-shrink: 0;
  min-width: 96px;
}

.au-tl-bar {
  position: relative;
  flex: 1;
  min-width: 120px;
  height: 8px;
  border-radius: 999px;
  cursor: pointer;
}
.au-tl-bar-bg {
  position: absolute; inset: 0;
  background: linear-gradient(90deg, #E0E7FF, #DBEAFE);
  border-radius: 999px;
}
.au-tl-bar-fill {
  position: absolute; left: 0; top: 0; height: 100%;
  background: linear-gradient(90deg, #6366F1, #2563EB, #06B6D4);
  border-radius: 999px;
  transition: width 0.3s linear;
}
.au-tl-anchor {
  position: absolute; top: -4px;
  width: 2px; height: 16px;
  background: #EF4444;
  transform: translateX(-1px);
  pointer-events: none;
}
.au-tl-thumb {
  position: absolute; top: 50%;
  width: 14px; height: 14px;
  background: #fff;
  border: 3px solid #2563EB;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.3);
  pointer-events: none;
  transition: left 0.3s linear;
}

.au-tl-speed { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.speed-label { font-size: 11px; color: #94A3B8; }
.speed-btn {
  padding: 4px 10px;
  font-size: 11px;
  border: 1px solid #E5E7EB;
  background: #fff;
  color: #64748B;
  border-radius: 6px;
  cursor: pointer;
  font-family: var(--au-font-mono, monospace);
  transition: all 0.15s;
}
.speed-btn:hover { background: #F1F5F9; }
.speed-btn.active { background: #2563EB; color: #fff; border-color: #2563EB; }

@media (max-width: 768px) {
  .au-tl-main { flex-wrap: wrap; }
  .au-tl-bar { order: 5; flex-basis: 100%; }
}
</style>
```

- [ ] **Step 3: Lint 验证**

Run: `cd frontend && npm run lint`
Expected: 无新增 error。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/aurora/AuTimelinePlayer.vue
git commit -m "feat(timeline): 改造为年份/区间日尺度 scrubber (去暴雨峰值硬编码)"
```

---

## Task 8: `AuroraDashboard.vue` 顶部 Tab + 浮层化地图 + 独立 KPI

**Files:**
- Modify: `frontend/src/views/dashboard/AuroraDashboard.vue`

**Interfaces:**
- Consumes: `AuTabSwitch` (Task 6)、改造后的 `AuTimelinePlayer` (Task 7)、
  日尺度 `useReplayStore` (Task 4)、`useReplayLayers.refresh(dateStr)` (Task 5)、
  `apiReplaySnapshotDaily` (Task 3)、`apiEvalSummary` (已存在)。
- Produces: 监测大屏页 (无对外接口)。

> 本任务改动集中在 template 结构、新增 `activeTab` 状态、`reloadAll` 改用日快照、
> KPI 与传感卡数据来源切换, 以及 tab 切换时的 `resize/updateSize` 处理。

- [ ] **Step 1: 模板 — 把「时间轴 + 地图 + KPI + body」结构改为 Tab 切换**

将 `<template>` 中从 `<!-- TIMELINE -->` 段 (`<section class="timeline-section">`) 到
`<!-- BODY -->` 段结束 (`</section>` of `body-grid`) 之间的内容, 替换为以下结构。
即: 保留顶部 `au-hero` 与 `history-section` 不动, 替换其后的
timeline-section / kpi-row / map-section / body-grid 四段为:

```vue
    <!-- ============================================================
         TAB 切换 + 常驻时间轴
    ============================================================ -->
    <section class="tabbar-section">
      <AuTabSwitch
        v-model="activeTab"
        :tabs="[
          { value: 'map', label: '数据地图', icon: '🗺' },
          { value: 'kpi', label: 'KPI 信息', icon: '📊' },
        ]"
      />
    </section>

    <section class="timeline-section">
      <AuTimelinePlayer />
    </section>

    <!-- ========== TAB A · 数据地图 ========== -->
    <section v-show="activeTab === 'map'" class="map-section">
      <AuCard
        title="四川省 · 空间监测态势"
        subtitle="EPSG:3857"
        gradient-border
        dot
        flat
        class="card-map"
      >
        <template #extra>
          <div class="map-toolbar">
            <div class="map-toggle-group">
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.province" @change="syncScLayer('province')" />
                <span>省界</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.city" @change="syncScLayer('city')" />
                <span>市州</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.river" @change="syncScLayer('river')" />
                <span>河流</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="scLayerToggle.settlement" @change="syncScLayer('settlement')" />
                <span>居民点</span>
              </label>
              <span class="toggle-divider"></span>
              <label class="map-toggle">
                <input type="checkbox" v-model="replayToggle.heatmap" @change="syncReplayLayer('heatmap')" />
                <span>雨量热力</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="replayToggle.stations" @change="syncReplayLayer('stations')" />
                <span>气象站</span>
              </label>
              <label class="map-toggle">
                <input type="checkbox" v-model="replayToggle.impact" @change="syncReplayLayer('impact')" />
                <span>风险范围</span>
              </label>
            </div>
            <div class="map-legend">
              <span class="lg-item"><span class="lg-dot" style="background:#DC2626" />红色</span>
              <span class="lg-item"><span class="lg-dot" style="background:#F97316" />橙色</span>
              <span class="lg-item"><span class="lg-dot" style="background:#F59E0B" />黄色</span>
              <span class="lg-item"><span class="lg-dot" style="background:#3B82F6" />蓝色</span>
            </div>
          </div>
        </template>
        <div ref="mapEl" class="au-map-canvas" />
      </AuCard>
    </section>

    <!-- ========== TAB B · KPI 信息 ========== -->
    <section v-show="activeTab === 'kpi'" class="kpi-tab">
      <div class="kpi-row">
        <AuKpiCard
          v-for="k in kpis"
          :key="k.key"
          :label="k.label"
          :value="k.value"
          :unit="k.unit"
          :trend="k.trend"
          :trend-invert="k.trendInvert"
          :spark-data="k.sparkData"
          :tone="k.tone"
        />
      </div>

      <div class="body-grid">
        <AuCard title="24 小时预警趋势" subtitle="按等级 · 每小时聚合" gradient-border dot class="card-trend">
          <template #extra>
            <AuSelect
              v-model="trendStackMode"
              :options="[
                { value: 'stack', label: '堆叠面积' },
                { value: 'line',  label: '折线对比' },
              ]"
              :width="120"
            />
          </template>
          <div ref="trendEl" class="chart-canvas" />
        </AuCard>

        <AuCard title="灾害类型分布" subtitle="过去 7 天" dot class="card-pie">
          <div ref="pieEl" class="chart-canvas" />
        </AuCard>

        <AuCard title="预警等级" subtitle="当前活跃" dot class="card-levels">
          <div class="level-grid">
            <div v-for="lv in [4,3,2,1]" :key="lv" class="level-cell" :style="{ '--lv-color': levelColor(lv) }">
              <div class="level-ring" :style="ringStyle(lv)">
                <span class="level-num">{{ levelCount(lv) }}</span>
              </div>
              <div class="level-label">{{ levelLabel(lv) }}预警</div>
              <div class="level-pct">{{ lvPercent(lv) }}%</div>
            </div>
          </div>
        </AuCard>

        <AuCard title="实时预警列表" :subtitle="`共 ${alertStore.latest.length} 条`" dot class="card-list">
          <template #extra>
            <router-link to="/alerts" class="see-all">查看全部 →</router-link>
          </template>
          <div class="alert-list">
            <div v-for="a in alertStore.latest.slice(0, 7)" :key="a.id" class="alert-item">
              <span class="alert-level-bar" :style="{ background: levelColor(a.level) }" />
              <div class="alert-main">
                <div class="alert-title">{{ a.title || a.disasterType || '预警事件' }}</div>
                <div class="alert-meta">
                  <span class="alert-region">{{ a.region || a.location || '—' }}</span>
                  <span class="alert-time">{{ formatTime(a.triggeredAt) }}</span>
                </div>
              </div>
              <span class="alert-level-tag" :style="levelTagStyle(a.level)">{{ levelLabel(a.level) }}</span>
            </div>
            <div v-if="!alertStore.latest.length" class="alert-empty">
              <span>当前无活跃预警</span>
              <span class="muted">系统监测正常运行</span>
            </div>
          </div>
        </AuCard>

        <AuCard title="传感数据流" subtitle="当日观测" dot class="card-sensors">
          <div class="sensor-grid">
            <div v-for="s in sensorReadings" :key="s.key" class="sensor-cell">
              <span class="sensor-label">{{ s.label }}</span>
              <span class="sensor-val" :style="{ color: s.color }">{{ s.value }}</span>
              <span class="sensor-unit">{{ s.unit }}</span>
            </div>
          </div>
        </AuCard>
      </div>
    </section>
```

- [ ] **Step 2: script — 引入 AuTabSwitch + activeTab + snapshot API**

在 `<script setup>` 的组件 import 段, `import AuTimelinePlayer from ...` 之后加:

```js
import AuTabSwitch from '@/components/aurora/AuTabSwitch.vue'
```

在 `import { apiReplayAlerts, apiReplayEvents, apiReplaySnapshot } from '@/api/replay'`
这一段, 改为追加日尺度快照导入:

```js
import {
  apiReplayAlerts,
  apiReplayEvents,
  apiReplaySnapshot,
  apiReplaySnapshotDaily,
} from '@/api/replay'
```

在 `const replayStore = useReplayStore()` 之后新增 activeTab 状态:

```js
// ---------- Tab 切换 (数据地图 / KPI 信息) ----------
const activeTab = ref('map')
```

- [ ] **Step 3: script — `reloadAll` 改用日快照驱动 KPI/图层/传感**

把现有 `async function reloadAll() { ... }` 整个函数替换为:

```js
async function reloadAll() {
  await replayStore.init()
  const date = replayStore.virtualNowDate
  if (!date) return
  try {
    const snapshot = await apiReplaySnapshotDaily(date).catch(() => [])
    const rows = Array.isArray(snapshot) ? snapshot : []

    // 用当日快照构造「预警列表」: comp_level>=1 的站点
    alertStore.latest = rows
      .filter(s => Number(s.comp_level) >= 1)
      .map(s => ({
        id: s.code,
        code: s.code,
        title: `${s.name} 灾害风险`,
        level: Number(s.comp_level),
        status: 1,
        triggeredAt: date,
        region: s.region_name || s.region_code,
        disasterType: '气象灾害',
      }))

    // KPI 数据源
    const riskStations = rows.filter(s => Number(s.comp_level) >= 1).length
    const maxRain = rows.reduce((m, s) => Math.max(m, Number(s.rainfall) || 0), 0)
    const topRow = rows[0]
    kpiSnapshot.value = {
      riskStations,
      maxRain,
      topRegion: topRow?.region_name || topRow?.region_code || '—',
    }

    // 地图专题层
    replayLayers.refresh(date)

    // 传感卡: 用当日最大雨量站
    if (topRow) {
      sensorReadings.value[0].value = (Number(topRow.rainfall) || 0).toFixed(1)
      sensorReadings.value[3].value = String(Math.round(60 + (Number(topRow.rainfall) || 0) * 0.4))
    }
  } catch (_) {}
  renderCharts()
}
```

- [ ] **Step 4: script — 新增 kpiSnapshot 状态 + 改写 kpis computed**

在 `const eventCount = ref(0)` 附近 (KPIs 段之前) 新增:

```js
// 当日快照聚合 (KPI 用)
const kpiSnapshot = ref({ riskStations: 0, maxRain: 0, topRegion: '—' })
// 窗口风险日 (来自 summary)
const windowRiskDays = ref(0)
```

把 `const kpis = computed(() => { ... })` 整段替换为:

```js
const kpis = computed(() => {
  const snap = kpiSnapshot.value
  return [
    { key: 'risk',   label: '风险站点',  value: snap.riskStations,        unit: '站', trend: 0,  trendInvert: true,  sparkData: mkSpark(Math.max(snap.riskStations, 4)), tone: 'danger' },
    { key: 'rain',   label: '最大雨量',  value: Math.round(snap.maxRain), unit: 'mm', trend: 0,  trendInvert: true,  sparkData: mkSpark(Math.max(Math.round(snap.maxRain / 5), 4)), tone: 'info' },
    { key: 'days',   label: '窗口风险日', value: windowRiskDays.value,     unit: '天', trend: 0,  trendInvert: true,  sparkData: mkSpark(Math.max(windowRiskDays.value, 4)), tone: 'warning' },
    { key: 'region', label: '重点区域',  value: kpiSnapshot.value.topRegion, unit: '',  trend: null, trendInvert: false, sparkData: [], tone: 'success' },
  ]
})
```

- [ ] **Step 5: script — `loadHistSummary` 注入 peakDate + windowRiskDays**

在 `loadHistSummary` 函数体内, `renderHistCharts()` 调用之前, 追加:

```js
  // 把最高风险日注入回放时间轴锚点; 窗口风险日填 KPI
  const top = histSummary.value.topEvents?.[0]
  if (top?.obs_date) replayStore.setPeakDate(String(top.obs_date).slice(0, 10))
  windowRiskDays.value = histSummary.value.riskDays || 0
```

- [ ] **Step 6: script — tab 切换时 resize/updateSize**

在 `onMounted` 内已有逻辑之后 (或文件 watch 区), 新增对 activeTab 的监听。
在最后一个 `watch(...)` 之后追加:

```js
// tab 切换: 解决隐藏容器尺寸为 0 — 切回时重算地图/图表尺寸
watch(activeTab, (tab) => {
  nextTick(() => {
    if (tab === 'map') {
      olMapRef.value?.updateSize()
    } else {
      trendChart?.resize(); pieChart?.resize()
      renderCharts()
    }
  })
})
```

- [ ] **Step 7: script — 删除回放虚拟时间 watch 里的旧 ISO 引用**

把文件底部:

```js
let _replayDebounce = null
watch(() => replayStore.virtualNow, () => {
  if (_replayDebounce) return
  _replayDebounce = setTimeout(() => {
    _replayDebounce = null
    reloadAll()
  }, 1000)
})
```

保持不变 (仍监听 `virtualNow`, reloadAll 内部已改用 `virtualNowDate`)。
确认 `reloadAll` 内不再引用 `apiReplayAlerts`/`apiReplayEvents`/`apiReplaySnapshot`/
`loadGeoJson('disasters', ...)`。如仍有 `loadGeoJson` 残留调用则一并删除。

- [ ] **Step 8: style — 新增 tabbar 样式 + 地图浮层化 + 时间轴常驻**

在 `<style scoped>` 顶部 `.timeline-section { width: 100%; }` 之后追加:

```css
.tabbar-section {
  display: flex;
  justify-content: center;
  padding: 4px 0;
}
.kpi-tab {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
```

把 `.card-map { flex: 1; height: 600px; min-height: 520px; }` 改为更大的 hero 尺寸:

```css
.card-map {
  flex: 1;
  height: 70vh;
  min-height: 520px;
}
.map-toolbar {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  z-index: 5;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  pointer-events: none;
}
.map-toggle-group,
.map-legend {
  pointer-events: auto;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  border: 1px solid var(--au-border-subtle);
  border-radius: 10px;
  padding: 8px 12px;
  box-shadow: var(--au-shadow-sm);
}
.map-toggle-group { flex-wrap: wrap; }
```

> 注意: `.map-toolbar` 原来在 AuCard 的 `#extra` 插槽 (卡头), 现在改为绝对定位浮层,
> 需要其定位父级是地图卡。AuCard 的 `#extra` 仍渲染在卡头, 但因 `position:absolute`
> + 高 z-index, 视觉上浮在地图上。若 AuCard 卡头有 `overflow:hidden` 导致裁切,
> 在 `.card-map` 上加 `position: relative; overflow: visible;` (本步已通过浮层定位覆盖)。

- [ ] **Step 9: Lint + build 验证**

Run: `cd frontend && npm run lint && npm run build`
Expected: lint 无新增 error; build 成功产出 `dist/` (无未定义变量/缺失 import 报错)。

- [ ] **Step 10: Commit**

```bash
git add frontend/src/views/dashboard/AuroraDashboard.vue
git commit -m "feat(dashboard): 顶部 Tab 切换 数据地图/KPI, 地图浮层化, KPI 改用日快照"
```

---

## Task 9: 端到端手动验证

**Files:** 无 (验证任务)

- [ ] **Step 1: 启动后端 + 前端**

Run: 后端 `cd backend && mvn spring-boot:run` (或现有启动方式); 前端 `cd frontend && npm run dev`。
确保 PostgreSQL 已灌入 `biz_weather_daily` + 已跑过 `/disaster-eval/run` (有判别结果)。

- [ ] **Step 2: 打开监测大屏, 核对验收点**

打开 `http://localhost:5173/dashboard`，逐项确认:
- 时间轴默认显示 2020–2023 区间, 当前日期为最早 obs_date, **不是 1970**。
- 年份 pills (全部/2020/2021/2022/2023) 点击可切换窗口, 当前日期跳到该年起点。
- 自定义起止日期 + 应用, 区间生效。
- ▶ 播放后日期按天推进, 地图热力/站点/风险圈随之刷新。
- ⚡ 最高风险日跳到 summary 的 top 风险日。
- 顶部「数据地图 / KPI 信息」Tab 可切换, 切换不重置回放 (日期保持)。
- 数据地图 tab: 地图占主视觉, 图层 chips/图例浮在地图上, 切回地图无空白 (updateSize 生效)。
- KPI 信息 tab: 4 张卡 (风险站点/最大雨量/窗口风险日/重点区域) 数值随回放日期变化; 图表正常渲染 (resize 生效)。

- [ ] **Step 3: 无问题则记录验证结果 (无需 commit)**

把验证结论 (通过/发现的问题) 反馈给审阅者。若发现问题, 回到对应 Task 修复。

---

## Self-Review

**Spec coverage:**
- §第一部分 后端 `/window` → Task 1 ✓
- §第一部分 后端 `/snapshot` → Task 2 ✓
- §第一部分 `api/replay.js` 新增 → Task 3 ✓
- §第一部分 `store/replay.js` 日尺度重构 (years/year/setRange/selectYear/stepDays/jumpToPeak 去硬编码) → Task 4 ✓
- §第一部分 `useReplayLayers.refresh` 切日数据 → Task 5 ✓
- §第二部分 顶部 Tab 切换 → Task 6 (组件) + Task 8 Step 1-2 ✓
- §第二部分 时间轴常驻/年份 pills/自定义区间 → Task 7 ✓
- §第二部分 数据地图 tab 地图 hero + 浮层 chips/图例 → Task 8 Step 1, 8 ✓
- §第二部分 KPI 独立成区 + 改用 snapshot 聚合 (风险站点/最大雨量/窗口风险日/重点区域) → Task 8 Step 3-5 ✓
- §第二部分 细节面板保留 + WeatherLab 皮肤 → Task 8 Step 1 (保留) + Task 7/6 配色 ✓
- §第二部分 v-show + resize/updateSize → Task 8 Step 6 ✓
- §第二部分 去地震/暴雨峰值硬编码 → Task 4 (jumpToPeak) + Task 7 (模板文案) ✓
- 验收要点 → Task 9 ✓

**Placeholder scan:** 无 TBD/TODO; 所有代码步骤含完整代码。

**Type consistency:**
- `apiReplayWindowDaily(year)` / `apiReplaySnapshotDaily(date)` — Task 3 定义, Task 4/8 使用, 名称一致 ✓
- store getter `virtualNowDate` — Task 4 定义, Task 5/8 使用 ✓
- `replay.stepDays` / `setSpeed(days)` — Task 4 定义, Task 7 使用 ✓
- `replay.peakDate` / `setPeakDate` — Task 4 定义, Task 7/8 使用 ✓
- `useReplayLayers.refresh(dateStr)` — Task 5 改签名, Task 8 传 `date` (YYYY-MM-DD) ✓
- `AuTabSwitch` v-model + tabs — Task 6 定义, Task 8 使用 ✓
- 后端 snapshot 列名 `comp_level`/`rainfall`/`region_name` — Task 2 定义, Task 5/8 消费 ✓

无遗漏。
