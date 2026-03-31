#include <iostream>
#include <spdlog/spdlog.h>     // 用vcpkg装的日志库

int main() {
    spdlog::info("程序启动成功！");
    spdlog::warn("这是一个警告信息。");
    spdlog::error("出错了！");

    return 0;
}