# GCSJ 灾害预警系统 · 后端接口文档总索引

> 本目录收录后端全部 Controller 的接口文档。每个模块含两份产出：接口文档 md（功能表 + 流程图 + 实体表 + DTO/VO）与流程图 drawio（iodraw/draw.io 可编辑）。
> 编写规范见 [接口文档生成规范.md](接口文档生成规范.md)。

## 模块清单（共 12 个）

| # | 模块 | 基础路径 | 业务职责 | 接口文档 | 流程图 |
| --- | --- | --- | --- | --- | --- |
| 1 | Alert 预警事件 | `/api/alerts` | 预警创建/分发/确认/关闭、大屏与地图展示 | [md](接口文档-AlertController.md) | [drawio](AlertController-功能流程结构图.drawio) |
| 2 | DisasterEvent 灾害事件 | `/api/disasters` | 灾害事件全生命周期管理，预警的上游来源 | [md](接口文档-DisasterEventController.md) | [drawio](DisasterEventController-功能流程结构图.drawio) |
| 3 | User 用户管理 | `/api/users` | 系统用户增删改查（软删） | [md](接口文档-UserController.md) | [drawio](UserController-功能流程结构图.drawio) |
| 4 | Auth 认证 | `/api/auth` | 登录、当前用户、登出 | [md](接口文档-AuthController.md) | [drawio](AuthController-功能流程结构图.drawio) |
| 5 | Role 角色管理 | `/api/roles` | 角色增删改查，关联权限（RBAC） | [md](接口文档-RoleController.md) | [drawio](RoleController-功能流程结构图.drawio) |
| 6 | Permission 权限管理 | `/api/permissions` | 权限树查询 | [md](接口文档-PermissionController.md) | [drawio](PermissionController-功能流程结构图.drawio) |
| 7 | Dictionary 字典 | `/api/dictionaries` | 数据字典维护（按类型分组键值项） | [md](接口文档-DictionaryController.md) | [drawio](DictionaryController-功能流程结构图.drawio) |
| 8 | EmergencyPlan 应急预案 | `/api/plans` | 预案增删改查，按灾种+等级匹配适用预案 | [md](接口文档-EmergencyPlanController.md) | [drawio](EmergencyPlanController-功能流程结构图.drawio) |
| 9 | Layer 图层管理 | `/api/layers` | GIS 地图图层增删改查 | [md](接口文档-LayerController.md) | [drawio](LayerController-功能流程结构图.drawio) |
| 10 | GisData 空间数据 | `/api` | 行政区/河流/居民点 GeoJSON 出口 | [md](接口文档-GisDataController.md) | [drawio](GisDataController-功能流程结构图.drawio) |
| 11 | OperationLog 操作日志 | `/api/logs` | 操作审计日志分页查询（只读） | [md](接口文档-OperationLogController.md) | [drawio](OperationLogController-功能流程结构图.drawio) |
| 12 | Public 公开接口 | `/api/public` | 健康检查（无需鉴权） | [md](接口文档-PublicController.md) | [drawio](PublicController-功能流程结构图.drawio) |

## 通用约定

- 统一返回包装 `Result<T>`；分页 `Result<PageResult<T>>`。
- 实体公共字段（id / deleted / createdAt / updatedAt）继承自 `BaseEntity`；少数实体（Dictionary、OperationLog）未继承，已在各文档单独标注。
- 数据库分三个 schema：`biz`（业务：预警/灾害事件/预案）、`sys`（系统：用户/角色/权限/字典/日志）、`gis`（空间：图层）。
- 空间字段使用 PostGIS（`geometry(Point/Polygon, 4326)`）。

## 模块关系速览

- **DisasterEvent → Alert**：灾害事件可挂起预警（`/api/alerts/from-event/{eventId}`）。
- **User ↔ Role ↔ Permission**：RBAC 权限体系，UserVO 输出 roleCodes 与 permissions。
- **Layer → GisData**：图层 `source_url` 可指向本系统 GeoJSON 接口（`/api/regions/geojson` 等）。
- **OperationLog**：由切面/拦截器横向记录各模块操作，本接口仅供审计检索。
