#include <iostream>
#include <string>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include <curl/curl.h>

using json = nlohmann::json;

// curl 回调函数，用于接收 HTTP 响应
static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    ((std::string*)userp)->append((char*)contents, size * nmemb);
    return size * nmemb;
}

int main() {
    // 设置控制台输出为 UTF-8（避免中文乱码）
    SetConsoleOutputCP(CP_UTF8);

    // ========== 1. 测试 spdlog ==========
    spdlog::info("✅ spdlog 日志测试成功！");

    // ========== 2. 测试 nlohmann/json ==========
    json data;
    data["name"] = "C同学";
    data["status"] = "环境配置完成";
    data["libraries"] = {"spdlog", "nlohmann/json", "curl"};
    std::cout << "✅ nlohmann/json 输出:\n" << data.dump(4) << std::endl;

    // ========== 3. 测试 curl（发送 HTTP GET 请求）==========
    CURL* curl = curl_easy_init();
    if (curl) {
        std::string response;
        curl_easy_setopt(curl, CURLOPT_URL, "http://httpbin.org/get");
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, WriteCallback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 5L);  // 5秒超时

        CURLcode res = curl_easy_perform(curl);
        if (res == CURLE_OK) {
            std::cout << "✅ curl 请求成功！响应内容长度: " << response.length() << " 字节" << std::endl;
            // 如果你想看完整响应，取消下面注释
            // std::cout << "响应内容:\n" << response << std::endl;
        } else {
            std::cout << "❌ curl 请求失败: " << curl_easy_strerror(res) << std::endl;
        }
        curl_easy_cleanup(curl);
    } else {
        std::cout << "❌ curl 初始化失败" << std::endl;
    }

    std::cout << "\n🎉 所有测试完成！" << std::endl;
    return 0;
}