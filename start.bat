@echo off
REM ============================================================================
REM GCSJ 项目一键启动脚本 (CMD) - 纯 Docker 方案
REM ============================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo ============================================================================
echo                    GCSJ 项目启动中 (Docker)...
echo ============================================================================
echo.

echo [*] 检查 Docker 守护进程...
docker ps >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker 守护进程未运行! 请先启动 Docker Desktop
    exit /b 1
)
echo [OK] Docker 已就绪

echo.
echo [*] 构建和启动容器组...
docker compose -f deploy/docker-compose.yml up -d --build
if errorlevel 1 (
    echo [ERROR] 容器启动失败! 请检查: docker compose -f deploy/docker-compose.yml logs
    exit /b 1
)
echo [OK] 容器已启动

echo.
echo [*] 等待 PostgreSQL...
set "max=30"
set "i=0"
:wait_pg
docker exec gcsj-postgres pg_isready -U gcsj -d gcsj >nul 2>&1
if errorlevel 1 (
    set /a i+=1
    if !i! leq !max! (
        echo [.] PostgreSQL 初始化中... (!i!/!max!)
        timeout /t 1 /nobreak >nul
        goto wait_pg
    ) else (
        echo [ERROR] PostgreSQL 启动超时
        exit /b 1
    )
)
echo [OK] PostgreSQL 已就绪

echo [*] 等待其余服务启动 (GeoServer / 后端 / FastAPI / 前端)...
timeout /t 15 /nobreak >nul

echo.
echo ============================================================================
echo                    启动完成!
echo ============================================================================
echo.
echo 访问地址:
echo   - 前端 (主应用):  http://localhost
echo   - Java 后端:      http://localhost:8085
echo   - FastAPI:        http://localhost:8062
echo   - FastAPI 文档:   http://localhost:8062/docs
echo   - GeoServer:      http://localhost:8600/geoserver (admin/gcsj123)
echo   - PostgreSQL:     localhost:5432 (gcsj/gcsj123)
echo.
echo 常用命令:
echo   查看日志:   docker compose -f deploy/docker-compose.yml logs -f
echo   停止服务:   stop.bat   (保留数据)
echo.
echo ============================================================================
echo.
