#!/bin/bash
# 食堂仿真系统 — 一键打包脚本
# 使用: ./package.sh

set -e
RELEASE_DIR="release"
VERSION="1.0.0"
PACKAGE_NAME="cafeteria-management-${VERSION}"

echo "=== 1. 编译 C++ 仿真引擎 ==="
rm -rf build && mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release && make -j$(nproc)
cd ..

echo "=== 2. 打包 Java 后端 ==="
cd backend
mvn package -DskipTests -q
cd ..

echo "=== 3. 组装发布包 ==="
rm -rf "${RELEASE_DIR}/${PACKAGE_NAME}"
mkdir -p "${RELEASE_DIR}/${PACKAGE_NAME}"/{bin,config,docs}

# C++ 二进制
cp build/cafeteria_sim "${RELEASE_DIR}/${PACKAGE_NAME}/bin/"

# Java JAR
cp backend/target/canteen-backend-${VERSION}.jar "${RELEASE_DIR}/${PACKAGE_NAME}/bin/"

# 默认配置
cp config/default_config.json "${RELEASE_DIR}/${PACKAGE_NAME}/config/"

# 文档
cp README.md docs/*.md "${RELEASE_DIR}/${PACKAGE_NAME}/docs/" 2>/dev/null

# 启动脚本
cat > "${RELEASE_DIR}/${PACKAGE_NAME}/start.sh" << 'STARTEOF'
#!/bin/bash
# 食堂仿真系统 启动脚本

DIR="$(cd "$(dirname "$0")" && pwd)"

# 检查 Java
if ! command -v java &>/dev/null; then
    echo "错误: 未找到 Java，请安装 JDK 21+"
    exit 1
fi

echo "启动食堂仿真系统..."
echo "  管理端: http://localhost:8081/"
echo "  用户端: http://localhost:8081/app.html"
echo ""

cd "$DIR"
java -jar bin/canteen-backend-*.jar
STARTEOF
chmod +x "${RELEASE_DIR}/${PACKAGE_NAME}/start.sh"

# README
cat > "${RELEASE_DIR}/${PACKAGE_NAME}/README.txt" << 'EOF'
食堂仿真系统 — 发布包
========================

前置条件:
  - JDK 21+
  - MySQL 8.4 (数据库 canteen_sim, 用户 canteen/canteen123)
  - Redis (localhost:6379)
  - RabbitMQ (localhost:5672)

快速启动:
  1. 确保 MySQL/Redis/RabbitMQ 运行中
  2. (可选) export DEEPSEEK_API_KEY=sk-xxx
  3. (可选) export DEEPSEEK_API_ENABLED=true
  4. ./start.sh

访问:
  管理端: http://localhost:8081/
  用户端: http://localhost:8081/app.html

配置:
  编辑 config/default_config.json 调整仿真参数
  或通过用户端"仿真控制"面板实时调节

详情见 docs/ 目录
EOF

echo ""
echo "=== 打包完成 ==="
echo "发布包: ${RELEASE_DIR}/${PACKAGE_NAME}/"
echo "启动命令: cd ${RELEASE_DIR}/${PACKAGE_NAME} && ./start.sh"
