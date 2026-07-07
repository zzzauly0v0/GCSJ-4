# GCSJ 项目 Docker 一键启动指南

## 🚀 快速开始

### Windows 用户

**PowerShell (推荐)**
```powershell
# 一键启动所有服务
.\start.ps1

# 停止所有服务
.\stop.ps1
```

**CMD (命令提示符)**
```cmd
# 一键启动所有服务
start.bat

# 停止所有服务
stop.bat
```

### Linux / macOS 用户
```bash
# 给脚本执行权限 (首次需要)
chmod +x start.sh stop.sh

# 一键启动所有服务
./start.sh

# 停止所有服务
./stop.sh
```

## 📋 项目架构

启动脚本会自动构建并启动以下 5 个容器：

```
┌─────────────────────────────────────────────────────┐
│                   Frontend (Nginx)                   │
│                    :80 / :443                        │
└────────────────────────┬────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ↓                ↓                ↓
   Backend (Java)    FastAPI (Python)  GeoServer
   :8085             :8062              :8600
        │                │                │
        └────────────────┼────────────────┘
                         │
                         ↓
                   PostgreSQL+PostGIS
                        :5432
```

## 🌐 服务访问地址

| 服务 | 地址 | 用户名 | 密码 | 说明 |
|------|------|--------|------|------|
| 前端 (主应用) | http://localhost | - | - | Vue.js 前端应用 |
| Java 后端 | http://localhost:8085 | - | - | Spring Boot API |
| FastAPI | http://localhost:8062 | - | - | Python AI 空间分析后端 |
| FastAPI 文档 | http://localhost:8062/docs | - | - | Swagger UI 交互文档 |
| GeoServer | http://localhost:8600/geoserver | admin | gcsj123 | 地理信息服务 |
| PostgreSQL | localhost:5432 | gcsj | gcsj123 | 数据库 |

## 📦 容器详情

| 容器名 | 镜像 | 端口 | 说明 |
|--------|------|------|------|
| gcsj-postgres | postgis:16-3.4 | 5432 | 地理数据库，包含 PostGIS 扩展 |
| gcsj-geoserver | geoserver:2.24.2 | 8600 | 地理信息服务器 |
| gcsj-backend | 本地构建 | 8085 | Java Spring Boot 后端 |
| gcsj-fastapi | 本地构建 | 8062 | Python FastAPI 后端 |
| gcsj-frontend | 本地构建 | 80 | Vue.js 前端 |

## 🛠️ 常用命令

### 查看日志
```bash
# 查看所有容器日志
docker compose -f deploy/docker-compose.yml logs -f

# 查看特定容器日志（实时）
docker compose -f deploy/docker-compose.yml logs -f gcsj-backend

# 查看最后 100 行日志
docker compose -f deploy/docker-compose.yml logs --tail 100 gcsj-fastapi
```

### 管理容器
```bash
# 查看运行中的容器
docker compose -f deploy/docker-compose.yml ps

# 重启特定容器
docker compose -f deploy/docker-compose.yml restart gcsj-fastapi

# 停止服务 (保留数据)
docker compose -f deploy/docker-compose.yml down

# 停止服务并清空数据卷
docker compose -f deploy/docker-compose.yml down -v

# 查看容器资源占用
docker stats
```

### 进入容器
```bash
# 进入 PostgreSQL 容器
docker exec -it gcsj-postgres psql -U gcsj -d gcsj

# 进入 FastAPI 容器（bash）
docker exec -it gcsj-fastapi bash

# 进入 Backend 容器
docker exec -it gcsj-backend bash
```

### 查看容器信息
```bash
# 查看容器配置
docker inspect gcsj-backend

# 查看容器网络
docker network inspect deploy_gcsj-net
```

## 🔧 环境变量配置

### Java 后端环境变量
在 `deploy/docker-compose.yml` 中的 `backend` 服务部分修改：

```yaml
environment:
  SPRING_PROFILES_ACTIVE: prod
  WEATHER_API_KEY: your-api-key-here  # 修改这里
```

### 其他配置
- PostgreSQL 密码：在 `postgres` 服务中修改 `POSTGRES_PASSWORD`
- GeoServer 密码：在 `geoserver` 服务中修改 `GEOSERVER_ADMIN_PASSWORD`
- Java 内存：修改 `JAVA_TOOL_OPTIONS` 中的 `-Xms` 和 `-Xmx`

## 🐛 故障排查

### 1. Docker 未运行
**错误**: `Docker 守护进程未运行！`
**解决**: 启动 Docker Desktop 或 Docker 服务

### 2. 端口被占用
**错误**: `bind: address already in use`
**解决**:
```bash
# 查找占用端口的进程
lsof -i :8085  # macOS/Linux
netstat -ano | findstr :8085  # Windows

# 杀死进程或更改端口
```

### 3. PostgreSQL 启动缓慢
这是正常的，第一次启动会初始化数据库和灌入数据。可能需要 30-60 秒。
查看日志确认：
```bash
docker compose -f deploy/docker-compose.yml logs -f gcsj-postgres
```

### 4. Backend 编译失败
Java 后端的构建可能较慢（2-5 分钟）。查看日志：
```bash
docker compose -f deploy/docker-compose.yml logs -f gcsj-backend
```

### 5. FastAPI 启动失败
检查 Python 依赖是否正确安装：
```bash
docker compose -f deploy/docker-compose.yml logs -f gcsj-fastapi
```

## 📊 性能建议

### 内存不足？
修改 Java 内存配置在 `docker-compose.yml`:
```yaml
JAVA_TOOL_OPTIONS: "-Xms128m -Xmx512m -Duser.timezone=UTC"
```

### 磁盘空间不足？
清空数据卷并重新启动：
```bash
docker compose -f deploy/docker-compose.yml down -v
./start.bat  # 或 ./start.sh
```

### 重建镜像（跳过缓存）
```bash
docker compose -f deploy/docker-compose.yml build --no-cache
docker compose -f deploy/docker-compose.yml up -d
```

## 🔐 安全建议

- **生产环境**: 修改所有默认密码
  - PostgreSQL: `gcsj123` → 强密码
  - GeoServer: `gcsj123` → 强密码

- **防火墙**: 限制外部访问敏感端口（5432, 8600）

- **环境变量**: 使用 `.env` 文件管理敏感信息：
  ```bash
  # .env 文件
  WEATHER_API_KEY=your-secret-key
  ```

## 📝 开发工作流

### 修改前端代码
不需要重启，前端容器使用 Vite 热更新。

### 修改 Java 后端代码
需要重启 backend 容器：
```bash
docker compose -f deploy/docker-compose.yml restart gcsj-backend
```

### 修改 FastAPI 代码
需要重启 fastapi 容器：
```bash
docker compose -f deploy/docker-compose.yml restart gcsj-fastapi
```

### 修改 Docker 配置
重建并启动所有服务：
```bash
docker compose -f deploy/docker-compose.yml down
docker compose -f deploy/docker-compose.yml up -d --build
```

## 🌍 本地开发模式

如果只想启动数据库，在本地用 IDE 运行后端代码：

```bash
# 仅启动数据库和 GeoServer
docker compose -f deploy/docker-compose.db.yml up -d

# 本地运行 Java 后端 (使用 IDE 或 Maven)
# 本地运行 FastAPI: uv run --project spatial_analyse uvicorn ...
# 本地运行前端: npm run dev
```

## 📚 更多信息

- Docker Compose 官方文档: https://docs.docker.com/compose/
- PostGIS 文档: https://postgis.net/
- Spring Boot 文档: https://spring.io/projects/spring-boot
- FastAPI 文档: https://fastapi.tiangolo.com/

---

**最后更新**: 2026-07-07
