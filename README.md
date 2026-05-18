# GCSJ-4 气象地质灾害监测预警管理系统

基于 WebGIS 的气象地质灾害监测预警平台。整合气象数据、地质监测数据与地理空间信息，对滑坡、泥石流、崩塌等地质灾害进行实时监测、风险评估与预警发布。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + Element Plus + OpenLayers + Echarts + Pinia |
| 后端 | Spring Boot 3.2 + Java 17 + Spring Data JPA + Hibernate Spatial |
| GIS  | GeoServer + PostGIS |
| 数据库 | PostgreSQL 16 + PostGIS 3.4 |
| 部署 | Docker Compose |

## 目录结构

```
.
├── frontend/        # 前端工程（Vue3 + Vite）
├── backend/         # 后端服务（Spring Boot）
├── gis/             # GeoServer 配置 / SLD 样式 / 切片脚本
├── docs/            # 设计文档、接口文档
├── deploy/          # Docker 部署脚本
└── data/            # 示例数据 / init.sql 初始化脚本
```

## 快速开始

### 1. 数据库

```bash
# 方式 A：本地 PostgreSQL (Ubuntu 示例)
sudo apt install -y postgresql-16 postgresql-16-postgis-3
sudo -u postgres psql -c "CREATE USER gcsj WITH PASSWORD 'gcsj123';"
sudo -u postgres psql -c "CREATE DATABASE gcsj OWNER gcsj;"
psql -U gcsj -d gcsj -h localhost -f data/init.sql

# 方式 B：Docker
docker compose -f deploy/docker-compose.yml up -d postgres
docker exec -i gcsj-postgres psql -U gcsj -d gcsj < data/init.sql
```

### 2. 后端

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
# 默认: http://localhost:8085
# Swagger UI: http://localhost:8085/swagger-ui.html
```

### 3. 前端

```bash
cd frontend
npm install
npm run dev
# 默认: http://localhost:5173
```

### 4. 一键启动 (Docker)

```bash
docker compose -f deploy/docker-compose.yml up -d
# 前端: http://localhost
# 后端: http://localhost:8085
# GeoServer: http://localhost:8600/geoserver  (admin/gcsj123)
```

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin  | admin123 | 超级管理员 |
| operator | admin123 | 监测员 |
| viewer | admin123 | 普通用户 |

## 模块约定

参考 `CLAUDE.md` 的"代码规范 / 分层职责速查"部分。前端 `页面只管渲染、逻辑抽出去、接口统一管`；后端 `面向接口、依赖注入、AOP 处理横切、异常统一捕获`。

## 文档

- 系统总览: `docs/README.md`
- 接口文档: 启动后端访问 `/swagger-ui.html`
- Git 工作流: `GIT_WORKFLOW.md`
- 项目规范: `CLAUDE.md`
