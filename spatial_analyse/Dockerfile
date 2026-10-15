# ====== Build stage ======
FROM python:3.12-slim AS builder

WORKDIR /build

# 安装 uv
RUN pip install --no-cache-dir uv

# 复制依赖描述, 生成 requirements.txt (利用缓存)
COPY pyproject.toml uv.lock* ./
RUN uv pip compile pyproject.toml -o requirements.txt

# ====== Runtime stage ======
FROM python:3.12-slim

# 系统时区
ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1

WORKDIR /app

# 安装依赖
COPY --from=builder /build/requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# 代码放在 /app/spatial_analyse, 使 `spatial_analyse` 包可导入
COPY . /app/spatial_analyse

EXPOSE 8062

# 健康检查 (使用应用自带 /ai/health)
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8062/ai/health')" || exit 1

# 从 /app 运行, spatial_analyse 作为顶层包被导入
CMD ["uvicorn", "spatial_analyse.api_server:app", "--host", "0.0.0.0", "--port", "8062"]
