#include <iostream>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include <windows.h>
using json = nlohmann::json;
int main() {
     SetConsoleOutputCP(CP_UTF8);
    json data;
    data["name"] = "C同学";
    data["status"] = "环境搞定！";

    std::cout << data.dump(4) << std::endl;
    spdlog::info("日志测试成功");

    return 0;
}