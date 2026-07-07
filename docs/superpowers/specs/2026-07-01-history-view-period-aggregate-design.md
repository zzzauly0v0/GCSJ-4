# 历史灾害分析页 — 2020–2023 区间聚合展示

日期: 2026-07-01
文件: `frontend/src/views/history/HistoryView.vue`

## 背景

页面当前按单年（年份下拉）展示站点风险分布地图、区县热力 Top 表、风险日列表。
现有 2020–2023 四年数据，需要能够对整段区间做聚合展示。

## 目标

新增“全部 (2020-2023)”年份选项并设为默认，选中时地图 / 热力 / 列表 / 单站时序
全部聚合整段区间的数据；单年选项保留，用户可切回。

## 关键前提（无需改后端）

后端 `/api/disaster-eval/**` 各只读接口的 `year` 参数均为可选：
- `/stations` 省略 year → `MAX(comp_level)` 覆盖全部年份
- `/heatmap`  省略 year → 对整段区间求平均
- `/events`   省略 year → 不加年份过滤
- `/series`   接收 from/to 日期区间
- `/run`      省略 year → 对全部观测批算

axios 会自动丢弃值为 `undefined` 的查询参数，因此“全部”时传 `undefined` 即可。

## 前端改动 (仅 HistoryView.vue)

1. **年份选择器**：`year` 默认值改为哨兵值 `'all'`；在单年 `<el-option>` 前插入
   一条 `label="全部 (2020-2023)" value="all"`。
2. **year 解析辅助**：`const yearParam = computed(() => year.value === 'all' ? undefined : year.value)`。
   所有 API 调用改用 `yearParam.value`。
3. **地图标题**：`站点风险分布（{{ yearLabel }}）`，`yearLabel` 在 'all' 时显示 `2020-2023`。
4. **单站时序 openStation**：'all' 时区间用 `2020-01-01` → `2023-12-31`，否则单年首尾。
5. **runEval**：'all' 时传 `undefined`（对全部年份批算）；提示文案不变。

## 不改动

后端、echarts 图表逻辑、样式。

## 测试

手动验证：默认加载显示“全部”聚合；切到 2022 显示单年；切回“全部”恢复；
点站点弹出时序区间正确；执行灾害判别在“全部”下对全量批算。
