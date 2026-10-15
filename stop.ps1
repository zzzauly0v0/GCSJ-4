# ============================================================================
# GCSJ 项目停止脚本 (PowerShell)
# ============================================================================
# down 不删数据卷 -> 数据保留。如需彻底清库: down -v (数据会丢, 需重跑 seed.ps1)
# ============================================================================

Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host "                    GCSJ 项目停止中..." -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

docker compose -f deploy/docker-compose.yml down

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Green
Write-Host "                    已停止所有容器 (保留数据卷)" -ForegroundColor Green
Write-Host "============================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "如需清空所有数据 (谨慎!): docker compose -f deploy/docker-compose.yml down -v" -ForegroundColor Yellow
Write-Host ""
