# GIS 配置说明

本目录存放 GeoServer 工作区配置、SLD 样式以及空间数据切片脚本说明。

## 目录

```
gis/
├── styles/                # SLD 样式
│   ├── sensor_point.sld   # 传感器点位样式
│   ├── alert_level.sld    # 预警分级配色 (蓝/黄/橙/红)
│   └── disaster_buffer.sld# 灾害影响范围多边形样式
└── README.md
```

## 1. GeoServer 部署

`deploy/docker-compose.yml` 已包含 GeoServer 服务 (端口 8600)。本目录的 `styles/` 会以只读方式挂载到容器 `/opt/geoserver_data/styles`。

启动后访问: http://localhost:8600/geoserver  
默认账号: `admin / gcsj123` (由 docker-compose 环境变量决定)。

## 2. 创建工作区与图层 (一次性)

1. **新建工作区**: `gcsj` (URI: `http://gcsj.local`)
2. **新建数据存储**: `gcsj-pg`
   - 类型: `PostGIS`
   - host: `postgres` (容器内)，本机调试用 `localhost`
   - port: `5432`
   - database: `gcsj`
   - schema: `public`
   - user/pwd: `gcsj / gcsj123`
3. **发布图层**:
   - `biz_sensor`        → 关联样式 `sensor_point`
   - `biz_alert`         → 关联样式 `alert_level`
   - `biz_disaster_event`→ 关联样式 `disaster_buffer` (展示 affected_area 多边形)

## 3. 注册 SLD 样式

GeoServer Web UI → Styles → Add new style → 选择 SLD 文件上传, 工作区选 `gcsj`。
或将 `styles/*.sld` 直接放入 `<DATA_DIR>/workspaces/gcsj/styles/` 后重启。

## 4. 矢量切片 (MVT) 切片脚本说明

CLAUDE.md 要求大数据量图层走 MVT。GeoServer 2.24 内置 MVT WMS 输出, 调用方式:

```
http://localhost:8600/geoserver/gcsj/wms
  ?service=WMS&version=1.1.0&request=GetMap
  &layers=gcsj:biz_sensor
  &bbox={bbox-epsg-3857}&width=256&height=256
  &srs=EPSG:3857
  &format=application/vnd.mapbox-vector-tile
```

OpenLayers 端使用 `ol/source/VectorTile` + `ol/format/MVT` 即可消费。

## 5. 后续扩展

- 上传 DEM / 卫星影像作为 `raster` 图层 (使用 GeoTIFF 数据存储)
- 编写 WPS 流程做缓冲区分析、叠加分析 (CLAUDE.md 应急决策模块)
- 通过 REST API (`/rest/workspaces/gcsj/datastores`) 自动化注册图层
