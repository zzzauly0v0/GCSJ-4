# sys schema 设计

`sys` 模式负责系统基础能力：用户、角色、权限、字典、操作日志。
采用经典 **RBAC（Role-Based Access Control）** 模型：用户不直接绑定权限，中间隔一层"角色"。

## 表关系总览

```
┌────────────┐  N:M  ┌────────────────┐  N:M  ┌──────────────┐  N:M  ┌──────────────────┐
│  sys_user  │──────▶│  sys_user_role │──────▶│   sys_role   │──────▶│ sys_role_permission│
└────────────┘       └────────────────┘       └──────────────┘       └──────────────────┘
                                                                              │
                                                                              ▼
                                                                     ┌──────────────┐
                                                                     │ sys_permission│ (自关联: parent_id)
                                                                     └──────────────┘

  sys_dictionary       独立, 不与上面的表外键关联 (字典是配置数据)
  sys_operation_log    独立, 仅冗余 user_id/username 字段, 不做外键
```

链路：**用户 → (user_role) → 角色 → (role_permission) → 权限**

## 主表

### sys_user — 系统用户

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | 主键 |
| username | VARCHAR(64) UNIQUE | 登录名 |
| password | VARCHAR(128) | BCrypt 哈希 |
| real_name | VARCHAR(64) | 姓名 |
| phone / email | VARCHAR | 联系方式 |
| avatar | VARCHAR(256) | 头像 URL |
| status | SMALLINT | 1 启用 / 0 停用 |
| last_login_at | TIMESTAMPTZ | 最近登录时间 |
| deleted | BOOLEAN | 软删除标记 |
| created_at / updated_at | TIMESTAMPTZ | 审计字段 |

### sys_role — 角色

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | 主键 |
| name | VARCHAR(64) | 角色名 (如"超级管理员") |
| code | VARCHAR(64) UNIQUE | 角色编码 (如 `ROLE_ADMIN`)，代码里靠这个判断 |
| description | VARCHAR(256) | 描述 |
| deleted / created_at / updated_at | | 审计 |

种子角色：`ROLE_ADMIN` / `ROLE_OPERATOR` / `ROLE_VIEWER`

### sys_permission — 权限/菜单

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGSERIAL PK | 主键 |
| name | VARCHAR(64) | 显示名 |
| code | VARCHAR(128) UNIQUE | 权限码，格式 `资源:操作` (如 `user:read`) |
| type | SMALLINT | 1 菜单 / 2 按钮 / 3 接口 |
| parent_id | BIGINT | **自关联**，构造菜单树 |
| path / icon / sort | | 前端渲染用 |

> 自关联：`parent_id` 指向同表的 `id`，形成菜单树。顶级菜单 `parent_id IS NULL`。

## 关联表（中间表）

### sys_user_role — 用户↔角色 (N:M)

```sql
user_id  BIGINT  → sys_user(id)   ON DELETE CASCADE
role_id  BIGINT  → sys_role(id)   ON DELETE CASCADE
PRIMARY KEY (user_id, role_id)
```

- 一个用户可以有多个角色，一个角色可以分配给多个用户
- 删除用户或角色时，关联记录自动清理
- 联合主键保证同一对 (user, role) 不重复

### sys_role_permission — 角色↔权限 (N:M)

```sql
role_id        BIGINT  → sys_role(id)        ON DELETE CASCADE
permission_id  BIGINT  → sys_permission(id)  ON DELETE CASCADE
PRIMARY KEY (role_id, permission_id)
```

- 权限挂在**角色**上，不挂在用户上
- 新用户加入只需绑定已有角色，不用动这张表
- 只有"新增角色"或"新增权限"时才需要修改

## 独立表

### sys_dictionary — 数据字典

```
UNIQUE (type_code, item_code)
```

| 字段 | 说明 |
|---|---|
| type_code | 字典类型 (如 `disaster_type`、`alert_level`、`alert_status`) |
| item_code | 字典项编码 (如 `landslide`) |
| item_value | 显示值 (如 "滑坡") |
| sort | 排序 |
| description | 备注 (alert_level 里复用为颜色 hex) |

不与其他表建立外键。业务表里通过 `type_code + item_code` 间接引用，灵活解耦。

### sys_operation_log — 操作日志

| 字段 | 说明 |
|---|---|
| user_id / username | 操作人，**冗余存储**，不做外键（用户被删后日志仍要保留） |
| module / action | Controller 类名 / 方法名 |
| method / uri / ip | HTTP 上下文 |
| result_code / cost_ms | 业务响应码、耗时 |
| created_at | 发生时间 |

索引：`idx_oplog_user(user_id)`、`idx_oplog_time(created_at)`

## 鉴权查询链路

用户登录后，要拿到他能访问的菜单/按钮：

```sql
SELECT DISTINCT p.code, p.path, p.icon, p.parent_id
FROM sys_user u
JOIN sys_user_role ur       ON ur.user_id = u.id
JOIN sys_role r             ON r.id = ur.role_id
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permission p       ON p.id = rp.permission_id
WHERE u.id = ? AND u.status = 1 AND u.deleted = FALSE;
```

返回结果交给前端动态渲染侧边栏 + 按钮级 `v-permission` 指令。

## 种子数据的角色权限矩阵

| 角色 | dashboard | disaster | alert | layer | plan | system |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| ROLE_ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| ROLE_OPERATOR | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ |
| ROLE_VIEWER | ✓ | ✓ | ✓ | ✗ | ✗ | ✗ |

## 设计要点

1. **RBAC 三层结构**：用户 → 角色 → 权限。新增用户不动权限表，扩展性好
2. **外键 + CASCADE**：保证引用完整性，删主记录自动清理关联
3. **联合主键 + 自动索引**：中间表用 `(a_id, b_id)` 作主键，既防重复又自带索引
4. **软删除**：业务表统一用 `deleted` 标记，不物理删，方便审计
5. **日志表故意不外键**：用户被删除后日志仍要可查，所以 `user_id` 只是冗余字段
6. **字典表解耦**：业务字段（如 `disaster_type`、`alert_level`）用字符串 / 数字编码，通过字典表翻译成显示值，新增类型不用改表结构

---

# gis schema 设计

`gis` 模式负责**空间参考数据 + 图层目录**：行政区、河流、居民点、图层注册表。所有几何统一存 **EPSG:4326**，每张表都建 GIST 空间索引。

## 表关系

```
        ┌──────────────────┐
        │   gis_layer      │  图层目录 (前端图层管理面板从这读)
        │   source_url     │ ─── 指向各业务表的 GeoJSON 接口或 WMS
        └──────────────────┘

  数据表 (各自独立, 不通过外键引用)
  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
  │ gis_admin_region │  │   gis_river      │  │  gis_settlement  │
  │ (省/市/县三级树) │  │  (LineString)    │  │  (Point)         │
  └──────────────────┘  └──────────────────┘  └──────────────────┘
```

## gis_admin_region — 行政区划

```sql
adcode      VARCHAR(12) UNIQUE NOT NULL    -- 国标行政区划代码
level       SMALLINT       -- 1 省 2 市/州 3 县/区
parent_code VARCHAR(12)    -- 上级 adcode (省级为 NULL), 形成自引用树
center      geometry(Point,        4326)
boundary    geometry(MultiPolygon, 4326)
area_km2    NUMERIC(12,2)  -- 入库时一次算好, 避免重复 ST_Area
```

索引：`boundary GIST`、`center GIST`、`level`、`parent_code`

数据来源：阿里 DataV `geo.datav.aliyun.com/areas_v3/bound/{adcode}_full.json`
入库脚本：`data/load_sichuan_boundary.py`，仅入四川（510000）+ 21 市州 + 全部区县。

## gis_river — 主要河流

```sql
name      VARCHAR(128)
grade     SMALLINT                            -- 1 干流 / 2 支流 / 3 一般
geom      geometry(LineString, 4326)
length_km NUMERIC(10,2)                       -- ST_Length(geom::geography)/1000
```

四川六大水系种子：长江、岷江、大渡河、雅砻江、嘉陵江、沱江。

## gis_settlement — 居民点

```sql
name       VARCHAR(128)
type       VARCHAR(32)         -- city/county/town
population INT                 -- 单位: 万
geom       geometry(Point, 4326)
```

种子涵盖四川主要城市 + 川西高山地灾带的县城与重点乡镇（汶川/北川/茂县/映秀镇等）。

## gis_layer — 图层目录

唯一一张"管理表"，前端**图层管理面板**就读这一张表来枚举可勾选的图层。它**不存几何**，只存指向其他数据源的指针：

```sql
name        VARCHAR(128)        -- 显示名 ("四川省界")
code        VARCHAR(64) UNIQUE  -- 唯一编码 ("biz_sc_province")
type        VARCHAR(32)         -- vector / raster / wms / wmts / xyz
source_url  VARCHAR(512)        -- 矢量: 后端 GeoJSON 接口
                                -- 栅格: GeoServer WMS / 第三方 XYZ
visible     BOOLEAN             -- 默认可见性
z_index     INT                 -- 叠加顺序: 0~9 底图 / 10+ 业务
```

后期接 GeoServer 时，只要往这张表 INSERT 一行 `type='wms'` 的记录，前端无需改代码就能加载新图层。

---

# biz schema 设计 (灾害业务)

`biz` 模式承载灾害业务：灾害事件、预警、应急预案。和 `gis` schema 不通过外键关联，而是**通过空间关系（ST_Within / ST_DWithin）+ region_code 冗余字段**两种方式互联。

## biz_disaster_event — 灾害事件

```sql
code            VARCHAR(64) UNIQUE      -- 业务编号 (如 SC20260601001)
title           VARCHAR(256)
type            VARCHAR(32)             -- landslide / debris_flow / collapse / flood
level           SMALLINT                -- 1 蓝 2 黄 3 橙 4 红
location        geometry(Point,   4326) -- 灾害点
affected_area   geometry(Polygon, 4326) -- 影响范围 (可选)
occurred_at     TIMESTAMPTZ
end_at          TIMESTAMPTZ
status          SMALLINT                -- 1 进行中 / 2 处置中 / 3 已结束
region_code     VARCHAR(12)             -- 所在区县 adcode (空间 JOIN 回填)
elevation       NUMERIC(7,2)            -- 高程 m (DEM 提取, 当前 seed 手填)
slope_deg       NUMERIC(5,2)            -- 坡度 deg
```

> **`region_code` 是预计算结果**：入库时跑一次 `UPDATE ... ST_Within` 回填，前端按区县筛选只用普通索引扫描，不用每次跑空间 JOIN。

索引：`location GIST`、`affected_area GIST`、`level`、`occurred_at DESC`、`region_code`

## biz_alert — 预警事件

通过 `event_id` 软关联到 `biz_disaster_event`。一个事件可挂多条预警（升级、关闭通知等）。

```sql
event_id        BIGINT REFERENCES biz_disaster_event(id)
source          VARCHAR(64)   -- manual / event / external
location        geometry(Point, 4326)
channels        VARCHAR(128)  -- 推送通道, 逗号分隔
status          SMALLINT      -- 1 待发送 / 2 已发送 / 3 已确认 / 4 已关闭
```

## biz_emergency_plan — 应急预案

按 `disaster_type + level` 匹配预案，预警发布时联动检索。

---

# PostGIS 空间分析视图

`02_sichuan_schema.sql` 末尾创建了 3 个视图，前端大屏直接 SELECT 即可拿到分析结果。

## v_event_by_county — 按区县聚合灾害

```sql
SELECT * FROM biz.v_event_by_county WHERE event_count > 0;
```

| 输出 | 含义 |
|---|---|
| county_name | 区县名 |
| event_count | 总灾害数 |
| high_level_count | 橙/红预警数 |
| density_per_100km2 | 单位面积灾害密度 |

底层用 `ST_Within(e.location, r.boundary)` 把灾害点聚合到县。

## v_event_river_distance — 距最近河流

```sql
SELECT * FROM biz.v_event_river_distance ORDER BY distance_m;
```

用 `LATERAL` 子查询 + `<->` KNN 操作符快速取每个事件的最近河流，距离用 `geography` 出米精度。

## v_settlement_at_risk — 受影响居民点

```sql
SELECT * FROM biz.v_settlement_at_risk WHERE event_id = ?;
```

进行中事件 5km 缓冲内的居民点，`ST_DWithin(geography, 5000)` 米精度。

---

# 四川数据使用流程

1. **跑基础脚本**
   ```bash
   psql -U gcsj -d gcsj -h localhost -f data/init.sql
   psql -U gcsj -d gcsj -h localhost -f data/02_sichuan_schema.sql
   ```
   完成：六大水系、20+ 居民点、15 条灾害事件、4 条预警入库；3 个分析视图建好。

2. **拉行政区边界**
   ```bash
   cd spatial_analyse
   uv sync                              # 装 psycopg2-binary / requests / shapely
   uv run python ../data/load_sichuan_boundary.py
   ```
   完成：四川省 + 21 市州 + 全部区县边界写入 `gis_admin_region`，并自动回填灾害事件的 `region_code`。

3. **验证 PostGIS 真的在干活**
   ```sql
   -- 灾害密度排名
   SELECT * FROM biz.v_event_by_county WHERE event_count > 0 ORDER BY density_per_100km2 DESC;

   -- 进行中事件附近 5km 哪些居民点受威胁
   SELECT * FROM biz.v_settlement_at_risk;

   -- 灾害点距河流距离
   SELECT * FROM biz.v_event_river_distance ORDER BY distance_m LIMIT 10;
   ```

# 数据规模 (四川范围)

| 实体 | 量级 | 来源 |
|---|---|---|
| 行政区 | 省 1 + 市 21 + 县 ~180 | 阿里 DataV |
| 河流 | 6 | 手工种子 (六大水系简化路径) |
| 居民点 | ~23 | 手工种子 (城市/重点县乡) |
| 灾害事件 | 15 | 手工种子 (川西高山带, 真实区位) |
| 预警 | 4 | 手工种子 (挂在进行中事件上) |
| 应急预案 | 3+2 | 手工种子 |

> 所有种子都对得上**真实地理位置**（汶川、北川、九寨沟、康定等），方便在地图上演示时一眼看出"这就是地灾高发带"。

