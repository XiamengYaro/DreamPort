#!/usr/bin/env bash
# DreamPort 全量构建：前端 → 同步进 server static → 打包 JAR（含插件）
# 用法: ./scripts/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[1/3] 构建前端..."
(cd frontend && npm ci && npm run build)

echo "[2/3] 同步前端产物到 server 静态资源..."
rm -rf dreamport-server/src/main/resources/static
mkdir -p dreamport-server/src/main/resources/static
cp -R frontend/dist/* dreamport-server/src/main/resources/static/

echo "[3/3] 打包后端与插件..."
mvn clean package -DskipTests

echo "完成:"
ls -lh dreamport-server/target/dreamport-server-*.jar dreamport-plugin/target/dreamport-plugin-*.jar
