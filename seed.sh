#!/bin/bash
# ============================================================================
# GCSJ 数据灌注脚本 (Linux/macOS/Git Bash)
# ============================================================================
# 用途: 数据库为空时 (全新部署 / down -v 之后) 一键恢复全部业务数据。
#   日常 start/stop 不需要跑这个 —— 数据在 gcsj-pgdata 卷里, down 不删卷。
#
# 前置条件:
#   1. 已 ./start.sh, 且 gcsj-postgres 容器健康 (schema 已由 *.sql 建好)
#   2. 已装 uv, 且能联网 (边界数据需在线拉取)
#
# 三个脚本连 localhost:5432 (compose 已映射), 顺序有依赖, 均幂等可重复跑。
# ============================================================================

set -e
cd "$(dirname "$0")"

echo ""
echo "============================================================================"
echo "                    GCSJ 数据灌注中..."
echo "============================================================================"
echo ""

# 检查 postgres 容器是否就绪
echo "[*] 检查 PostgreSQL 是否就绪..."
if ! docker exec gcsj-postgres pg_isready -U gcsj -d gcsj > /dev/null 2>&1; then
    echo "[ERROR] gcsj-postgres 未就绪, 请先运行 ./start.sh"
    exit 1
fi
echo "[OK] PostgreSQL 已就绪"

# 1. 行政边界 (在线拉取)
echo ""
echo "[1/3] 灌注行政边界 (load_sichuan_boundary.py)..."
uv run --project spatial_analyse python spatial_analyse/load_sichuan_boundary.py

# 2. 气象站点 + 日观测 (读 data/*.csv, 依赖边界)
echo ""
echo "[2/3] 灌注气象数据 (load_weather_stations.py)..."
uv run --project spatial_analyse python spatial_analyse/load_weather_stations.py

# 3. 灾害评估 (依赖日观测)
echo ""
echo "[3/3] 计算灾害评估 (merged_disaster_eval.py)..."
uv run --project spatial_analyse python spatial_analyse/merged_disaster_eval.py

echo ""
echo "============================================================================"
echo "                    数据灌注完成!"
echo "============================================================================"
echo ""
