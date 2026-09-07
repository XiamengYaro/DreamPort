#!/usr/bin/env bash
# DreamPort 全量构建：前端 → 同步进 server static → 打包 JAR（含插件）→ BlessingSkin 插件 zip
# 用法: ./scripts/build.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "[1/4] 构建前端..."
(cd frontend && npm ci && npm run build)

echo "[2/4] 同步前端产物到 server 静态资源..."
rm -rf dreamport-server/src/main/resources/static
mkdir -p dreamport-server/src/main/resources/static
cp -R frontend/dist/* dreamport-server/src/main/resources/static/

echo "[3/4] 打包后端与插件..."
mvn clean package -DskipTests

echo "[4/4] 打包 BlessingSkin 插件..."
# 防静默失败:关键注入代码必须存在,否则插件"安装成功但功能缺失"
grep -q "RenderingHeader" bs-plugin-dreamport/bootstrap.php || { echo "✗ bootstrap.php 缺少 RenderingHeader 注入"; exit 1; }
grep -q "dp-theme-css" bs-plugin-dreamport/views/theme-sync.blade.php || { echo "✗ theme-sync 视图缺失"; exit 1; }
grep -q "dp_theme_sync" bs-plugin-dreamport/src/Configuration.php || { echo "✗ 配置页缺少主题同步开关"; exit 1; }
# BS 上传安装是原样解压到 plugins/,zip 内必须含一层插件目录(PluginManager 只扫描一级子目录)
ZIP_NAME="dreamport-oauth-$(sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' bs-plugin-dreamport/package.json | head -1).zip"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/dreamport-oauth"
cp -R bs-plugin-dreamport/bootstrap.php bs-plugin-dreamport/package.json bs-plugin-dreamport/src bs-plugin-dreamport/views "$STAGE/dreamport-oauth/"
rm -f "bs-plugin-dreamport/$ZIP_NAME"
(cd "$STAGE" && zip -qr "$OLDPWD/bs-plugin-dreamport/$ZIP_NAME" dreamport-oauth)

echo "完成:"
ls -lh dreamport-server/target/dreamport-server-*.jar dreamport-plugin/target/dreamport-plugin-*.jar "bs-plugin-dreamport/$ZIP_NAME"
