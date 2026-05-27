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
