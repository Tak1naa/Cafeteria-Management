#include <iostream>
#include <string>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include <windows.h>   // 用于 SetConsoleOutputCP

using json = nlohmann::json;

int main() {
    // 设置控制台输出为 UTF-8（避免中文乱码）
    SetConsoleOutputCP(CP_UTF8);

    // ========== 1. 测试 spdlog ==========
    spdlog::info("spdlog 日志测试成功！");

    // ========== 2. 测试 nlohmann/json ==========
    json data;
    data["name"] = "C同学";
    data["status"] = "环境配置完成";
    data["libraries"] = {"spdlog", "nlohmann/json"};
    std::cout << "nlohmann/json 输出:\n" << data.dump(4) << std::endl;

    std::cout << "\n所有测试完成！" << std::endl;
    std::cout << "按回车键退出...";
    std::cin.get();
    return 0;
}