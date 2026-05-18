# 本地开发指南

## 0. 前置依赖

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 后端运行 |
| Node.js | 20+ | 前端构建 |
| PostgreSQL | 16 + PostGIS 3.4 | 数据库 |
| Maven (可选) | 3.9+ | 后端打包 (`./mvnw` 已自带) |
| Docker (可选) | 24+ | 一键启动 |

## 1. 准备数据库

### 方式 A: Docker (推荐)
```bash
cd deploy
docker compose up -d postgres
```
镜像 `postgis/postgis:16-3.4` 启动时会自动执行 `data/init.sql`。

### 方式 B: 本地直装
按 CLAUDE.md 第 "数据库" 段落操作, 完成后:
```bash
psql -U gcsj -d gcsj -h localhost -f data/init.sql
```

## 2. 启动后端

```bash
cd backend
./mvnw spring-boot:run
```
默认监听 `8085`, profile=`dev`, 热部署可结合 IDE。

启动后:
- Swagger: http://localhost:8085/swagger-ui.html
- 健康检查: http://localhost:8085/actuator/health

> 默认账号: `admin / admin123` (`operator/viewer` 同密码)

## 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```
浏览器打开 http://localhost:5173 (Vite 已配 `/api` 与 `/ws` 反代后端 8085)。

## 4. 一键 Docker (生产部署预演)

```bash
cd deploy
docker compose up -d
```
- 前端: http://localhost
- 后端: http://localhost:8085
- GeoServer: http://localhost:8600/geoserver
- 数据库: localhost:5432

## 5. 关键调试技巧

### 5.1 模拟一次气象数据采集
```bash
curl -X POST http://localhost:8085/api/ingest/weather \
  -H "Authorization: Bearer <token>"
```
返回写入条数。生产由 `@Scheduled` 定时执行。

### 5.2 上报一条观测触发预警
```bash
curl -X POST http://localhost:8085/api/observations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "sensorId": 1,
    "indicator": "RAINFALL_HOURLY",
    "value": 120.0,
    "unit": "mm",
    "observedAt": "2026-05-18T08:00:00Z"
  }'
```
若超过阈值规则, 后端会:
1. 入库观测, `abnormal=true`
2. 创建 Alert
3. 通过策略分发器推送 (in_site / sms / email)
4. 通过 STOMP 发布到 `/topic/alerts`
5. 前端大屏地图自动刷新红色标记

### 5.3 前端 WebSocket 调试
浏览器控制台:
```js
import('@/utils/ws').then(({ useWS }) => {
  const ws = useWS()
  ws.connect().then(() => ws.subscribe('/topic/alerts', a => console.log('NEW ALERT', a)))
})
```

## 6. 常见问题

- **前端报 401**: 检查 token 是否过期 (`gcsj.jwt.expire-minutes` 默认 720). axios 拦截器会自动跳登录。
- **PostGIS dialect 报错**: 确认连接的库已 `CREATE EXTENSION postgis`。Docker 镜像自动处理。
- **CORS 报错**: `application.yml` 中 `gcsj.cors.allowed-origins` 增加来源。
- **Swagger 404**: 必须 `application.yml` 内 `springdoc.swagger-ui.enabled: true`, 且 SecurityConfig 已放行 `/swagger-ui/**`。
