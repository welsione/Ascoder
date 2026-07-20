#!/usr/bin/env bash
# Ascoder 自动打包工具
# 在开发机上执行，构建并打包为可分发 zip
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
STAGING_DIR="$ROOT_DIR/build/staging"
OUTPUT_DIR="$ROOT_DIR/build/output"

echo "=========================================="
echo "  Ascoder 自动打包工具"
echo "=========================================="
echo ""

# --- 读取版本号 ---
VERSION=$(grep '<version>' "$ROOT_DIR/pom.xml" | head -1 | sed 's/.*<version>\(.*\)<\/version>.*/\1/')
ZIP_NAME="ascoder-${VERSION}.zip"
echo "版本: $VERSION"
echo ""

# --- [1] 检测构建工具 ---
echo "[1/5] 检测构建工具 ..."
if ! command -v mvn >/dev/null 2>&1 && [ ! -f "$ROOT_DIR/backend/mvnw" ]; then
  echo "ERROR: Maven not found. Install Maven or use ./mvnw" >&2
  exit 1
fi

MVN="mvn"
if [ -f "$ROOT_DIR/mvnw" ]; then
  MVN="$ROOT_DIR/mvnw"
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "ERROR: npm not found. Install Node.js first." >&2
  exit 1
fi

echo "  Maven: $MVN"
echo "  npm: $(which npm)"
echo ""

# --- [2] 构建 JAR ---
echo "[2/5] 构建 JAR ..."
(cd "$ROOT_DIR" && $MVN -pl backend -am package -DskipTests -q)
JAR_FILE="$ROOT_DIR/backend/target/ascoder-backend-${VERSION}.jar"
if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: JAR not found at $JAR_FILE" >&2
  exit 1
fi
echo "  JAR: $JAR_FILE"
echo ""

# --- [3] 构建前端 ---
echo "[3/5] 构建前端 ..."
(cd "$ROOT_DIR/frontend/web" && npm install --quiet && npm run build)
DIST_DIR="$ROOT_DIR/frontend/web/dist"
if [ ! -d "$DIST_DIR" ]; then
  echo "ERROR: Frontend dist not found at $DIST_DIR" >&2
  exit 1
fi
echo "  Dist: $DIST_DIR"
echo ""

# --- [4] 组装临时目录 ---
echo "[4/5] 组装打包目录 ..."
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR/ascoder"

# 复制安装脚本
cp "$ROOT_DIR/installer/install.sh" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/uninstall.sh" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/install.ps1" "$STAGING_DIR/ascoder/"
cp "$ROOT_DIR/installer/uninstall.ps1" "$STAGING_DIR/ascoder/"

# 复制 JAR（重命名为 ascoder.jar），并嵌入前端静态资源到 BOOT-INF/classes/static/
cp "$JAR_FILE" "$STAGING_DIR/ascoder/ascoder.jar"
echo "  Embedding frontend into JAR (BOOT-INF/classes/static/) ..."
STATIC_TMP=$(mktemp -d)
mkdir -p "$STATIC_TMP/BOOT-INF/classes/static"
cp -r "$DIST_DIR"/* "$STATIC_TMP/BOOT-INF/classes/static/"
(cd "$STATIC_TMP" && jar uf "$STAGING_DIR/ascoder/ascoder.jar" BOOT-INF/classes/static/)
rm -rf "$STATIC_TMP"
echo "  Frontend embedded."

# 复制启停脚本
mkdir -p "$STAGING_DIR/ascoder/bin"
cp "$ROOT_DIR/installer/bin/start.sh" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/stop.sh" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/start.ps1" "$STAGING_DIR/ascoder/bin/"
cp "$ROOT_DIR/installer/bin/stop.ps1" "$STAGING_DIR/ascoder/bin/"

# 复制配置模板
mkdir -p "$STAGING_DIR/ascoder/conf"
cp "$ROOT_DIR/installer/conf/application.yml" "$STAGING_DIR/ascoder/conf/"

# 复制 README
cp "$ROOT_DIR/installer/README.txt" "$STAGING_DIR/ascoder/"

echo "  组装完成。"
echo ""

# --- [5] 打包 ---
echo "[5/5] 打包 zip ..."
mkdir -p "$OUTPUT_DIR"
rm -f "$OUTPUT_DIR/$ZIP_NAME"
(cd "$STAGING_DIR" && zip -r -q "$OUTPUT_DIR/$ZIP_NAME" ascoder/)

ZIP_SIZE=$(du -h "$OUTPUT_DIR/$ZIP_NAME" | cut -f1)
echo "  输出: $OUTPUT_DIR/$ZIP_NAME ($ZIP_SIZE)"
echo ""

# --- 清理 ---
echo "清理临时文件 ..."
rm -rf "$STAGING_DIR"
echo ""

echo "=========================================="
echo "  ✓ 打包完成！"
echo "=========================================="
echo ""
echo "  文件: $OUTPUT_DIR/$ZIP_NAME ($ZIP_SIZE)"
echo "  安装: 解压后运行 install.sh (Unix) 或 install.ps1 (Windows)"
echo ""
