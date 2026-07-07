"""共享数据库连接: 统一 DSN 与连接创建, 避免各工具重复。"""
import os

import psycopg2

# 优先读环境变量 DB_DSN (Docker 内指向 postgres 服务), 否则回退本地默认值。
DB_DSN = os.getenv(
    "DB_DSN",
    "host=localhost port=5432 dbname=gcsj user=gcsj password=gcsj123",
)


def connect(dsn: str = DB_DSN):
    """创建 psycopg2 连接 (调用方负责 close)。"""
    return psycopg2.connect(dsn)