# ============================================================================
# GCSJ 项目一键启动脚本 (PowerShell) - 纯 Docker 方案
# ============================================================================
# 功能: 构建并启动全部容器 (PostgreSQL / GeoServer / Java 后端 / FastAPI / 前端)
#       并做健康检查, 最后打印访问地址。
# ============================================================================

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "                    GCSJ 项目启动中 (Docker)..." -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker
Write-Host "[*] 检查 Docker 守护进程..."
docker ps 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker 守护进程未运行! 请先启动 Docker Desktop" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Docker 已就绪" -ForegroundColor Green

Write-Host ""
Write-Host "[*] 构建和启动容器组..."
docker compose -f deploy/docker-compose.yml up -d --build
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] 容器启动失败! 请检查: docker compose -f deploy/docker-compose.yml logs" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] 容器已启动" -ForegroundColor Green

Write-Host ""
Write-Host "[*] 等待服务就绪..." -ForegroundColor Yellow
Write-Host ""

# --- PostgreSQL ---
$max = 30; $i = 0; $ok = $false
Write-Host "[*] 等待 PostgreSQL..."
while ($i -lt $max) {
    docker exec gcsj-postgres pg_isready -U gcsj -d gcsj 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) { $ok = $true; break }
    $i++; Write-Host "[.] PostgreSQL 初始化中... ($i/$max)"; Start-Sleep -Seconds 1
}
if ($ok) { Write-Host "[OK] PostgreSQL 已就绪" -ForegroundColor Green }
else { Write-Host "[ERROR] PostgreSQL 启动超时" -ForegroundColor Red; exit 1 }

# --- 通用 HTTP 探活函数 ---
function Wait-Http($name, $url, $max) {
    $i = 0
    while ($i -lt $max) {
        curl.exe -s -o $null $url 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host "[OK] $name 已就绪" -ForegroundColor Green; return }
        $i++
        if ($i % 5 -eq 0) { Write-Host "[.] $name 初始化中... ($i/$max)" }
        Start-Sleep -Seconds 1
    }
    Write-Host "[WARN] $name 启动超时, 可能仍在初始化" -ForegroundColor Yellow
}

Write-Host "[*] 等待 GeoServer..."; Start-Sleep -Seconds 5
Wait-Http "GeoServer" "http://localhost:8600/geoserver" 60
Write-Host "[*] 等待 Java 后端..."
Wait-Http "Java 后端" "http://localhost:8085/actuator/health" 60
Write-Host "[*] 等待 FastAPI..."
Wait-Http "FastAPI" "http://localhost:8062/ai/health" 30
Write-Host "[*] 等待 前端..."; Start-Sleep -Seconds 3
Wait-Http "前端" "http://localhost" 30

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Green
Write-Host "                    启动完成!" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "访问地址:" -ForegroundColor Cyan
Write-Host "  - 前端 (主应用):  http://localhost"
Write-Host "  - Java 后端:      http://localhost:8085"
Write-Host "  - FastAPI:        http://localhost:8062"
Write-Host "  - FastAPI 文档:   http://localhost:8062/docs"
Write-Host "  - GeoServer:      http://localhost:8600/geoserver (admin/gcsj123)"
Write-Host "  - PostgreSQL:     localhost:5432 (gcsj/gcsj123)"
Write-Host ""
Write-Host "常用命令:" -ForegroundColor Cyan
Write-Host "  查看日志:   docker compose -f deploy/docker-compose.yml logs -f"
Write-Host "  停止服务:   .\stop.ps1   (保留数据)"
Write-Host "  灌注数据:   .\seed.ps1   (仅数据库为空时)"
Write-Host ""
Write-Host "============================================================================" -ForegroundColor Green
Write-Host ""
