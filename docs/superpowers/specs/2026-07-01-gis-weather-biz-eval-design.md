# 气象数据 gis 存储 + 灾害分析 biz 存储 + 回放按日推送 · 设计

> 2026-07-01 · 分支 feature/zzz

## 目标

将四川气象站点 CSV（2020-2023 逐日, GBK）接入数据库，按 **gis=原始空间数据源 / biz=分析结果** 的分层重构现有实现：

```
CSV → load_weather_stations.py
        ↓
gis.gis_weather_station   站点元数据 + location geometry(Point,4326)
gis.gis_weather_daily     逐日观测时序
        ↓  POST /api/disaster-eval/run  (复用 DisasterEvalEngine)
biz.biz_disaster_eval     逐日逐站判别结果 (专用表)
        ↓  POST /api/disaster-eval/push?date=YYYY-MM-DD
WebSocket /topic/disasters → 前端时间轴回放按日渲染/弹窗
```

## 关键原则

- 算法引擎 `DisasterEvalEngine`（算法 .md 全实现）**原样复用**，不改。
- WebSocket 推送基础设施（STOMP `/ws`、`SimpMessagingTemplate`、前端 `useWS`）**原样复用**。
- 本次只改**数据读写位置**（biz→gis）+ **新增按日推送接口/前端订阅**。

## 变更清单

### 1. 数据库 (data/)
- 新增 `03_gis_weather_schema.sql`：`gis.gis_weather_station`、`gis.gis_weather_daily`
- 新增 `04_disaster_eval_schema.sql`：`biz.biz_disaster_eval`（`station_code` 关联 `gis.gis_weather_station.code`）

### 2. Python (spatial_analyse/load_weather_stations.py)
- 目标表 `biz.biz_monitor_station`/`biz_weather_daily` → `gis.gis_weather_station`/`gis.gis_weather_daily`
- GBK 读取、缺测清洗、经纬度→Point、region_code 反查、年份/记录数统计、幂等 upsert 逻辑保留

### 3. 后端 (DisasterEvalController.java)
- `/run` `/series` `/stations` `/summary` `/heatmap`：表引用 biz→gis
- 新增 `POST /push?date=`：查该日 `comp_level>=3` 记录+经纬度，推 `/topic/disasters`

### 4. 前端
- 新增 `hooks/useDisasterSocket.js`（仿 `useAlertSocket.js`）订阅 `/topic/disasters`
- 回放到某日时调 `apiDisasterPush(date)`，地图/弹窗按等级渲染

## 推送触发

历史数据非实时流 → **回放时按日推送**：前端时间轴回放到某天 → 调 `/push?date=` → 后端广播该日高风险(橙/红)事件。
