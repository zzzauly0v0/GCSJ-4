# API 接口文档

> **首选**: 启动后端后访问 [Swagger UI](http://localhost:8085/swagger-ui.html) — 由 springdoc-openapi 自动生成，与代码同步。
>
> 下表为人工梳理的接口骨架, 用于快速查阅。所有响应统一为:
>
> ```json
> { "code": 0, "message": "success", "data": <T>, "timestamp": 1747545600000 }
> ```
>
> 出错响应见 [错误码](#错误码) 章节。

## 通用约定

| 项 | 规则 |
|----|------|
| BaseURL | `http://<host>:8085/api` |
| 认证 | `Authorization: Bearer <jwt>` |
| 时间格式 | ISO 8601 (UTC), 例: `2026-05-18T08:00:00Z` |
| 坐标系 | 入参/出参统一 EPSG:4326 (经度/纬度), 前端展示自行转 EPSG:3857 |
| 分页 | `?page=1&size=10` (page 1-based) |

## 1. 认证

| Method | URL | 说明 |
|--------|-----|------|
| POST | `/auth/login` | 登录, 返回 `{token, user}` |
| GET  | `/auth/profile` | 获取当前登录用户 |
| POST | `/auth/logout` | 登出 (前端清 token 即可) |

请求体 `LoginDTO`: `{ username, password }`

## 2. 系统管理

| 模块 | URL | 说明 |
|------|-----|------|
| 用户 | `GET /users?keyword&page&size` | 分页 |
| 用户 | `POST /users` | `CreateUserDTO` |
| 用户 | `PUT /users/{id}` | `UpdateUserDTO` |
| 用户 | `DELETE /users/{id}` | 软删 |
| 角色 | `GET /roles` | 列表 |
| 角色 | `POST /roles` | `CreateRoleDTO` |
| 权限 | `GET /permissions/tree` | 树形 |
| 组织 | `GET /orgs/tree` | 树形 |
| 字典 | `GET /dictionaries?type=` | 按类型 |

## 3. 传感器 / 监测

| Method | URL | 说明 |
|--------|-----|------|
| GET    | `/sensors?keyword&type&page&size` | 分页 |
| GET    | `/sensors/all` | 全部 |
| GET    | `/sensors/{id}` | 详情 |
| POST   | `/sensors` | 新建 (`CreateSensorDTO`) |
| PUT    | `/sensors/{id}` | 更新 |
| DELETE | `/sensors/{id}` | 删除 |
| GET    | `/sensors/geojson` | FeatureCollection |
| POST   | `/observations` | 上报观测 (`CreateObservationDTO`) — 触发规则评估 |
| GET    | `/observations?sensorId&page&size` | 分页 |
| GET    | `/observations/series?sensorId&indicator&from&to` | 时间序列 |
| GET    | `/observations/latest?limit` | 最新 N 条 |

## 4. 预警

| Method | URL | 说明 |
|--------|-----|------|
| GET    | `/alerts?level&status&page&size` | 分页 |
| GET    | `/alerts/{id}` | 详情 |
| POST   | `/alerts` | 手动创建 (`CreateAlertDTO`) |
| POST   | `/alerts/{id}/confirm` | 确认 |
| POST   | `/alerts/{id}/close` | 关闭 |
| GET    | `/alerts/latest?limit` | 最新 N 条 |
| GET    | `/alerts/geojson` | FeatureCollection |
| GET    | `/alert-rules` | 全部规则 |
| POST   | `/alert-rules` | 新建规则 |
| PUT    | `/alert-rules/{id}` | 更新 |
| DELETE | `/alert-rules/{id}` | 删除 |

## 5. 灾害事件 / 应急

| Method | URL | 说明 |
|--------|-----|------|
| GET    | `/disasters?level&type&page&size` | 分页 |
| POST   | `/disasters` | 新建 (`CreateDisasterEventDTO`) |
| GET    | `/disasters/geojson` | 含影响区域 |
| GET    | `/plans` | 应急预案列表 |
| POST   | `/plans` | 新建预案 |
| GET    | `/plans/applicable?disasterType&level` | 适用预案查询 |

## 6. 空间图层

| Method | URL | 说明 |
|--------|-----|------|
| GET    | `/layers` | 全部 |
| POST   | `/layers` | 新建 |
| PUT    | `/layers/{id}` | 更新 |
| DELETE | `/layers/{id}` | 删除 |

## 7. 数据采集

| Method | URL | 说明 |
|--------|-----|------|
| POST   | `/ingest/weather` | 立即拉取一次气象数据 (调试) |

定时拉取由 `WeatherIngestJob` 每 60s 执行 (`gcsj.weather.enabled=true` 时生效)。

## 8. WebSocket (STOMP over SockJS)

- 连接端点: `ws://<host>:8085/ws`
- 订阅:
  - `/topic/alerts` — 预警广播 (`AlertVO`)
  - `/topic/observations` — 最新观测
  - `/topic/disasters` — 灾害事件
  - `/user/queue/notice` — 单用户消息

## 错误码

| 范围 | 含义 |
|------|------|
| 0 | 成功 |
| 10000-10099 | 系统/通用 (10001 参数, 10002 未授权, 10003 无权限) |
| 20000-20999 | 用户/认证 |
| 30000-30999 | 传感器/观测 |
| 40000-40999 | 预警/事件 |
| 50000-50999 | 空间/图层 |
| 90000-99999 | 第三方 |

详见 `backend/src/main/java/com/gcsj/disaster/common/ErrorCode.java`.
