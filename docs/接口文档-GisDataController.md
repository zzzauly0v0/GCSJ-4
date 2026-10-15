# 接口文档 · GisDataController（空间数据 GeoJSON）

> 遵循《接口文档生成规范》。
>
> - 基础路径（@RequestMapping）：`/api`（注意：本类直接挂在 `/api` 下，各方法自带子路径）
> - 统一返回包装：`Result<T>`
> - 对应服务：`IGisDataService`
> - 业务定位：四川空间底图数据出口——行政区、河流、居民点的 GeoJSON。`gis_layer.source_url` 中配置的 `/api/regions/geojson` 等指针即落到这里。

---

## 一、Controller 功能表

| 类功能 | GisData 功能（行政区/河流/居民点 GeoJSON 输出） |
| --- | --- |
| **方法一** | `regions(Short level, String parent, String adcode)` |
| | 功能：行政区 GeoJSON（level=1 省 / 2 市 / 3 县；parent=父级 adcode） |
| | 输入：查询参数 `level`、`parent`、`adcode`（均可空） |
| | 输出：`Result<Map<String,Object>>` —— 行政区 GeoJSON（FeatureCollection） |
| | 路由：`GET /api/regions/geojson` |
| **方法二** | `rivers()` |
| | 功能：河流 GeoJSON |
| | 输入：无 |
| | 输出：`Result<Map<String,Object>>` —— 河流 GeoJSON |
| | 路由：`GET /api/rivers/geojson` |
| **方法三** | `settlements(String type)` |
| | 功能：居民点 GeoJSON（type 可选 city/county/town） |
| | 输入：查询参数 `type`（可空） |
| | 输出：`Result<Map<String,Object>>` —— 居民点 GeoJSON |
| | 路由：`GET /api/settlements/geojson` |

---

## 二、功能流程结构图

> 形状约定：圆角=开始/结束，矩形=处理，**棱形=判断/分支**，平行四边形=输入/输出，圆柱=数据存储。

```mermaid
flowchart TD
    classDef startend fill:#e3f2fd,stroke:#1565c0,color:#0d47a1;
    classDef process fill:#fff3e0,stroke:#e65100,color:#bf360c;
    classDef decision fill:#fff8e1,stroke:#f9a825,color:#e65100;
    classDef io fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c;
    classDef store fill:#eceff1,stroke:#455a64,color:#263238;

    START(["开始 · 空间数据请求"]):::startend
    D1{"数据类型 ?"}:::decision
    START --> D1

    D1 -->|行政区 regions| D2{"行政级别 level ?"}:::decision
    D2 -->|1 省| R1["按省过滤"]:::process
    D2 -->|2 市 (parent=省adcode)| R2["按 parent 过滤市"]:::process
    D2 -->|3 县| R3["按 parent 过滤县"]:::process
    D1 -->|河流 rivers| R4["取全部河流"]:::process
    D1 -->|居民点 settlements| D3{"type ?"}:::decision
    D3 -->|city/county/town| R5["按 type 过滤"]:::process
    D3 -->|未传| R6["取全部居民点"]:::process

    DB[("gis 空间数据<br/>(行政区/河流/居民点)")]:::store
    R1 --> DB
    R2 --> DB
    R3 --> DB
    R4 --> DB
    R5 --> DB
    R6 --> DB

    P["转换为 GeoJSON FeatureCollection"]:::process
    DB --> P
    VO[/"Map&lt;String,Object&gt; 输出<br/>GeoJSON"/]:::io
    P --> VO
    END(["结束 · 供前端地图渲染"]):::startend
    VO --> END
```

---

## 三、Entity 实体表

> GisDataController **不直接持有业务实体**，从 GIS 空间数据表（行政区/河流/居民点）读取并转换为 GeoJSON 返回，输出为通用 `Map<String,Object>`（GeoJSON 结构），无对应 VO 类。空间数据表结构由 `IGisDataService` 维护，不在本 Controller 暴露。

---

## 四、关联 DTO / VO

> 本模块全部为 GET 查询，无入参 DTO；输出为 GeoJSON 通用 Map（非自定义 VO）。

### 输出结构 · GeoJSON（FeatureCollection）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| type | String | 固定 "FeatureCollection" |
| features | List\<Object\> | 要素数组，每个含 geometry + properties |

### 查询参数说明

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| regions | level | 1 省 / 2 市 / 3 县 |
| regions | parent | 父级 adcode（如 level=2 时传 510000） |
| regions | adcode | 指定行政区编码 |
| settlements | type | city / county / town |
