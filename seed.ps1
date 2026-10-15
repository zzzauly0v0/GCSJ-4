# ============================================================================
# GCSJ 数据灌注脚本 (PowerShell)
# ============================================================================
# 用途: 数据库为空时 (全新部署 / down -v 之后) 一键恢复全部业务数据。
#   日常 start/stop 不需要跑这个 —— 数据在 gcsj-pgdata 卷里, down 不删卷。
#
# 前置条件:
#   1. 已 .\start.ps1, 且 gcsj-postgres 容器健康 (schema 已由 *.sql 建好)
#   2. 宿主机已装 uv, 且能联网 (边界数据需在线拉取)
#
# 三个脚本连 localhost:5432 (compose 已映射), 顺序有依赖, 均幂等可重复跑。
# ============================================================================

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "                    GCSJ 数据灌注中..." -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

# 检查 postgres 容器是否就绪
Write-Host "[*] 检查 PostgreSQL 是否就绪..."
docker exec gcsj-postgres pg_isready -U gcsj -d gcsj 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] gcsj-postgres 未就绪, 请先运行 .\start.ps1" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] PostgreSQL 已就绪" -ForegroundColor Green

# 1. 行政边界 (在线拉取)
Write-Host ""
Write-Host "[1/3] 灌注行政边界 (load_sichuan_boundary.py)..." -ForegroundColor Yellow
uv run --project spatial_analyse python spatial_analyse/load_sichuan_boundary.py
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] 边界灌注失败" -ForegroundColor Red; exit 1 }

# 2. 气象站点 + 日观测 (读 data/*.csv, 依赖边界)
Write-Host ""
Write-Host "[2/3] 灌注气象数据 (load_weather_stations.py)..." -ForegroundColor Yellow
uv run --project spatial_analyse python spatial_analyse/load_weather_stations.py
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] 气象数据灌注失败" -ForegroundColor Red; exit 1 }

# 3. 灾害评估 (依赖日观测)
Write-Host ""
Write-Host "[3/3] 计算灾害评估 (merged_disaster_eval.py)..." -ForegroundColor Yellow
uv run --project spatial_analyse python spatial_analyse/merged_disaster_eval.py
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] 灾害评估失败" -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Green
Write-Host "                    数据灌注完成!" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor Green
Write-Host ""
