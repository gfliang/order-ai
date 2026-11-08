#!/usr/bin/env bash
# 启动前等待 MySQL 就绪：Render 各服务独立启动，无法像本地 compose 那样控制依赖顺序。
set -e

HOST="${MYSQL_HOST:-localhost}"
PORT="${MYSQL_PORT:-3306}"

echo "等待 MySQL ${HOST}:${PORT} 就绪 ..."
for _ in $(seq 1 60); do
  if (echo > "/dev/tcp/${HOST}/${PORT}") >/dev/null 2>&1; then
    echo "MySQL 已就绪，开始启动后端"
    exec java -jar /app/app.jar
  fi
  sleep 2
done

echo "等待 MySQL 超时（120 秒），直接启动后端" >&2
exec java -jar /app/app.jar
