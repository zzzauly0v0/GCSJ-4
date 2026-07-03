# 区间回放 + WeatherLab 风格地图大屏 — 设计

> 日期: 2026-06-24
> 范围: 监测大屏 (`/dashboard` → AuroraDashboard) 的回放功能与布局重构
> 状态: 待实施

## 背景与问题

1. **回放从 1970 开始的 Bug**: `store/replay.js` 的 `init()` 调 `/api/replay/window`,
   该接口对 `biz.biz_weather_observation` 取 `MIN/MAX(observed_at)`。该合成逐时表在
   切换到真实日数据后已为空, `MIN` 返回 `null` → 前端 `new Date(null)` → `1970-01-01`。
2. **无区间选择**: 回放窗口写死, 无法按年 (2020/2021/2022/2023) 或自定义区间回放。
3. **布局诉求**: 参考 Google DeepMind WeatherLab
   (https://deepmind.google.com/science/weatherlab) 风格 —— 地图做成主视觉大区,
   KPI 数据卡片独立成区。

## 决策 (已与用户确认)

- **回放区间**: 按年份快选 (全部 / 2020 / 2021 / 2022 / 2023) + 自定义起止区间。
- **默认窗口**: 全部年份 (2020–2023), 但各年份可独立切换展示。
- **地图独立**: 监测大屏页顶部用 **Tab 切换**两个数据展示 —— 「数据地图」与「KPI 信息」。
  仍在 `/dashboard` 一页内, 回放时间轴两个 tab 共享 (同一 replay store)。
- **数据源**: 真实日数据 `biz_weather_daily` + 判别结果 `biz_disaster_eval` 驱动回放。
- **细节面板** (趋势/灾种/等级/列表/传感): 保留, 但按 WeatherLab 风格重新皮肤化。

## 第一部分 · 区间可选的日尺度回放 (修复 1970)

### 后端 — `DisasterEvalController` 新增 2 个只读接口

沿用现有 `JdbcTemplate` 直查风格, 与 `/summary` `/heatmap` 等并列。

1. **`GET /api/disaster-eval/window?year=`**
   - 返回 `{ startAt, endAt, years: [2020,2021,2022,2023], description }`
   - `startAt/endAt` 取 `MIN/MAX(obs_date)` FROM `biz_weather_daily` (传 year 则年内过滤)。
   - `years` 取 `DISTINCT EXTRACT(YEAR FROM obs_date)`。
   - 这是替代当前空表 `window` 的真实数据窗口源。

2. **`GET /api/disaster-eval/snapshot?date=YYYY-MM-DD`**
   - 返回某一天全部气象站: `code, name, region_code, lon, lat, rainfall, comp_level, r_eff`
   - SQL: `biz_weather_daily w` JOIN `biz_monitor_station s` (type='weather')
     LEFT JOIN `biz_disaster_eval e ON e.station_code=w.station_code AND e.obs_date=w.obs_date`
     WHERE `w.obs_date = ?::date`。
   - 驱动: 雨量热力 (rainfall 权重)、站点染色 (rainfall 分级)、风险标记/影响圈 (comp_level)。

### 前端 — `api/replay.js`

新增:
```js
export const apiReplayWindowDaily = (year)  => request.get('/disaster-eval/window', { params: year ? { year } : {} })
export const apiReplaySnapshotDaily = (date) => request.get('/disaster-eval/snapshot', { params: { date } })
```
旧的逐时 `apiReplayWindow / apiReplaySnapshot / apiReplayWeatherSnapshot` 在大屏回放路径不再调用
(保留导出, 避免影响未路由的 MonitorDashboard)。

### 前端 — `store/replay.js` 重构 (日尺度)

state 调整:
- `years: []` — 可选年份列表 (来自 window 接口)
- `year: 0` — 当前选中年份, `0` = 全部 (默认)
- `windowStart / windowEnd` — 改为日期 (ISO date)
- `virtualNow` — Date, 以**天**为步进单位
- `stepDays: 1` — 每 tick 推进天数 (倍速: 1×/3×/7×/30× 天)
- `playing / ready / _timer` 不变

actions:
- `init()` → 调 `apiReplayWindowDaily()`, 填 years/window, virtualNow = windowStart
- `selectYear(y)` → 重新拉 window(y), reset 到该窗口起点 (y=0 取全部)
- `setRange(startDate, endDate)` → 自定义窗口覆盖, reset 到起点
- `tick()` → virtualNow += stepDays 天; 到 windowEnd 则停
- `setSpeed(days)` → 设 stepDays
- `seek(progress01)` → 同现逻辑, 按天插值
- `jumpToPeak()` → 改为「跳到窗口内最高风险日」: 用 summary.topEvents[0].obs_date
  (无则跳窗口中点)。命名/文案去掉「暴雨峰值」「2022-09-06」硬编码。
- getter `virtualNowDate` → `YYYY-MM-DD` 字符串 (给 snapshot 接口)

### 前端 — `hooks/useReplayLayers.js`

`refresh(dateStr)` 切到日数据:
- 拉 `apiReplaySnapshotDaily(dateStr)` (单次, 不再并行逐时 snapshot + events)
- 热力权重: `rainfall / 100` 满档 (复用 rainWeight, 入参改 daily rainfall)
- 站点染色: 按 `rainfall` 分级 (沿用 rainColor 阈值)
- 风险标记 + 影响圈: 由 `comp_level >= 1` 的站点生成 (替代原 events GeoJSON 的 level)
  影响半径按 comp_level 映射 (复用 impactRadiusByLevel)。

## 第二部分 · WeatherLab 风格布局重构

### 页面结构 (AuroraDashboard.vue) — 顶部 Tab 切换

```
┌─────────────────────────────────────────────────────┐
│  HERO banner (标题 + 历史分析入口)                     │
├─────────────────────────────────────────────────────┤
│  [ 数据地图 ] [ KPI 信息 ]   ← 顶部 Tab               │
├─────────────────────────────────────────────────────┤
│  ▼ 时间轴 scrubber (两个 tab 共享, 常驻)               │
│   [全部|2020|2021|2022|2023] [自定义区间]             │
│   ◀ ▶  2022-09-06  ━━━━●━━━  倍速  ⚡最高风险日        │
└─────────────────────────────────────────────────────┘

TAB A · 数据地图
┌─────────────────────────────────────────────────────┐
│  HERO MAP (full-bleed, ~70vh, 圆角大卡)               │
│   ├ 左上浮层: 图层 chips (省界/市州/河流/居民点/         │
│   │           雨量热力/气象站/风险范围)                 │
│   └ 右上浮层: 图例 (红/橙/黄/蓝)                        │
└─────────────────────────────────────────────────────┘

TAB B · KPI 信息
  KPI STRIP (4 卡, 由当天 snapshot 驱动)
  ┌────────┬────────┬────────┬────────┐
  │风险站点 │最大雨量 │窗口风险日│重点区域 │
  └────────┴────────┴────────┴────────┘
  DETAIL PANELS (WeatherLab 皮肤)
  趋势 / 灾种分布 / 等级 / 实时列表 / 传感数据流
```

### 关键改动

1. **顶部 Tab 切换**: 监测大屏页 hero 之下放两个 tab ——「数据地图」「KPI 信息」。
   用本地 `activeTab` ref 控制, 不新增路由。切 tab 不重置回放状态 (共享 replay store)。
2. **时间轴常驻 (两 tab 共享)**: 时间轴 scrubber 放在 tab 切换条下方、tab 内容之上,
   两个 tab 都可见可操作。改造 `AuTimelinePlayer`:
   - 顶行: 年份快选 pills (全部/2020/2021/2022/2023) + 自定义区间 (两个 date input)
   - 主行: 播放/暂停、上一天/下一天、当前日期 (YYYY-MM-DD)、进度条、倍速、最高风险日
3. **TAB A 数据地图**: 地图升为 hero, 高度 ~70vh, 占满该 tab 主视觉。
   图层 toggle 由右上工具栏改为**地图内浮层 chips**, 图例浮在右上角。
4. **TAB B KPI 信息**: KPI 4 卡 + 细节面板都在此 tab。KPI 来源改为当天 snapshot 聚合:
   - 风险站点 (当天 comp_level>=1 的站数)
   - 最大 24h 雨量 (当天 max rainfall)
   - 窗口风险日 (窗口内 riskDays, 来自 summary)
   - 重点区域 (当天最高 comp_level 站点所在区县)
   细节面板 (趋势/灾种/等级/列表/传感) 保留并按 WeatherLab 风格重皮肤
   (更多留白、细边框、克制配色、统一卡片圆角与阴影)。
5. **去地震/暴雨峰值硬编码**: 移除 `2022-09-06T04:00:00Z` 锚点与「暴雨峰值」文案。

> 注意 ECharts/地图懒挂载: tab 切换为 `v-show` 而非 `v-if`, 避免 echarts/OL
> 实例反复销毁重建; 切到地图 tab 时调一次 `olMap.updateSize()`, 切到 KPI tab 时
> `chart.resize()`, 解决隐藏容器尺寸为 0 的问题。

### 数据流 (回放推进时)

```
virtualNow (天) 变化
  → watch 防抖 (1s)
  → reloadAll(dateStr):
      apiReplaySnapshotDaily(dateStr)  → KPI 卡 + 传感卡 + 图表
      replayLayers.refresh(dateStr)    → 地图热力/站点/风险
  (年份/区间切换时同样触发 reloadAll)
```

## 不在范围内

- DB schema 变更 (表已存在, 无需新建)。
- 新增顶级路由 (仍单页 `/dashboard`)。
- 未路由的 `MonitorDashboard.vue` 不动。
- 灾害判别算法本身不改 (复用 `biz_disaster_eval` 已算结果)。

## 验收要点

- [ ] 回放默认显示 2020–2023 全部区间, 起点为最早 obs_date, 不再出现 1970。
- [ ] 年份 pills 可切到单年, 自定义区间可选任意起止日。
- [ ] 顶部 Tab 可在「数据地图」「KPI 信息」间切换, 切换不重置回放。
- [ ] 时间轴两个 tab 共享、常驻可操作。
- [ ] 数据地图 tab: 地图为主视觉 (~70vh), 图层 chips/图例浮层化。
- [ ] KPI 信息 tab: KPI 卡 + 细节面板, 数值随回放当天 snapshot 变化, 呈 WeatherLab 风格。
- [ ] 无地震/暴雨峰值硬编码残留。
