#!/bin/bash
DIR="$(cd "$(dirname "$0")" && pwd)"
echo "食堂仿真系统启动..."
echo "  管理端: http://localhost:8081/"
echo "  用户端: http://localhost:8081/app.html"
cd "$DIR" && java -jar bin/canteen-backend-*.jar
