#include "simulation/decision_client.h"

#include <algorithm>
#include <cctype>
#include <numeric>
#include <regex>
#include <sstream>
#include <string>

#if __has_include(<nlohmann/json.hpp>)
#include <nlohmann/json.hpp>
#define CAFETERIA_HAS_NLOHMANN_JSON 1
#endif

#ifdef CAFETERIA_USE_LIBCURL
#include <curl/curl.h>
#elif defined(_WIN32)
#include <windows.h>
#include <winhttp.h>
#endif

namespace cafeteria {

namespace {

constexpr const char* kSchemaVersion = "v1";

std::string trim(const std::string& value) {
    const auto first = std::find_if_not(value.begin(), value.end(), [](unsigned char c) { return std::isspace(c) != 0; });
    const auto last = std::find_if_not(value.rbegin(), value.rend(), [](unsigned char c) { return std::isspace(c) != 0; }).base();
    if (first >= last) {
        return "";
    }
    return std::string(first, last);
}

#if !defined(CAFETERIA_USE_LIBCURL) && defined(_WIN32)
struct ParsedHttpUrl {
    bool https = false;
    std::string host;
    unsigned short port = 80;
    std::string path;
};

std::optional<ParsedHttpUrl> parseHttpUrl(const std::string& url) {
    std::regex pattern(R"(^(https?)://([^/:?#]+)(?::(\d+))?([^?#]*)?(\?[^#]*)?$)", std::regex_constants::icase);
    std::smatch match;
    if (!std::regex_match(url, match, pattern)) {
        return std::nullopt;
    }

    ParsedHttpUrl parsed;
    const std::string scheme = match[1].str();
    parsed.https = scheme == "https" || scheme == "HTTPS" || scheme == "Https";
    parsed.host = match[2].str();

    if (match[3].matched) {
        try {
            const int portValue = std::stoi(match[3].str());
            if (portValue <= 0 || portValue > 65535) {
                return std::nullopt;
            }
            parsed.port = static_cast<unsigned short>(portValue);
        } catch (...) {
            return std::nullopt;
        }
    } else {
        parsed.port = parsed.https ? 443 : 80;
    }

    const std::string rawPath = match[4].matched ? match[4].str() : "/";
    const std::string rawQuery = match[5].matched ? match[5].str() : "";
    parsed.path = rawPath.empty() ? "/" : rawPath;
    parsed.path += rawQuery;
    return parsed;
}

std::wstring utf8ToWide(const std::string& value) {
    if (value.empty()) {
        return L"";
    }

    const int sizeNeeded = MultiByteToWideChar(CP_UTF8, 0, value.c_str(), -1, nullptr, 0);
    if (sizeNeeded <= 0) {
        return L"";
    }

    std::wstring wide(static_cast<std::size_t>(sizeNeeded), L'\0');
    const int converted = MultiByteToWideChar(CP_UTF8, 0, value.c_str(), -1, &wide[0], sizeNeeded);
    if (converted <= 0) {
        return L"";
    }

    if (!wide.empty() && wide.back() == L'\0') {
        wide.pop_back();
    }
    return wide;
}
#endif

#ifdef CAFETERIA_USE_LIBCURL
size_t writeCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    const std::size_t bytes = size * nmemb;
    auto* response = static_cast<std::string*>(userp);
    response->append(static_cast<char*>(contents), bytes);
    return bytes;
}
#endif

} // namespace

DecisionClient::DecisionClient(const SimulationConfig& config)
    : config_(config) {}

DecisionResult DecisionClient::decide(
    const SimulationState& state,
    int newArrivals,
    const std::vector<int>& fallbackAllocation,
    int simTimeSec) {

    DecisionResult result;
    result.allocation = fallbackAllocation;
    result.mode = DecisionMode::RuleFallback;
    result.reason = "fallback";

    if (!config_.aiEnabled) {
        result.reason = "ai_disabled";
        return result;
    }

    if (cache_.expireAtSimTimeSec >= simTimeSec && cache_.arrivals == newArrivals && !cache_.allocation.empty()) {
        result.allocation = cache_.allocation;
        result.mode = DecisionMode::Ai;
        result.fromCache = true;
        result.reason = "cache_hit";
        return result;
    }

    const std::string url = config_.backendBaseUrl + "/api/ai/decision";
    const std::string payload = buildDecisionRequestJson(state, newArrivals);
    const auto response = postJson(url, payload);
    if (!response.has_value()) {
        result.reason = "http_failed";
        return result;
    }

    const auto parsed = parseAllocation(response.value(), fallbackAllocation.size());
    if (!parsed.has_value()) {
        result.reason = "invalid_ai_response";
        return result;
    }

    const int allocated = std::accumulate(parsed->begin(), parsed->end(), 0);
    if (allocated != newArrivals) {
        result.reason = "ai_allocation_mismatch";
        return result;
    }

    cache_.arrivals = newArrivals;
    cache_.allocation = parsed.value();
    cache_.expireAtSimTimeSec = simTimeSec + config_.aiCacheSeconds;

    result.allocation = parsed.value();
    result.mode = DecisionMode::Ai;
    result.fromCache = false;
    result.reason = "ai_ok";
    return result;
}

bool DecisionClient::sendRealtimeState(const SimulationState& state) {
    const std::string url = config_.backendBaseUrl + "/api/simulation/data";
    const std::string payload = buildStateJson(state);
    const auto response = postJson(url, payload);
    return response.has_value();
}

std::optional<std::string> DecisionClient::postJson(const std::string& url, const std::string& payload) const {
#ifdef CAFETERIA_USE_LIBCURL
    CURL* curl = curl_easy_init();
    if (curl == nullptr) {
        return std::nullopt;
    }

    std::string responseBody;
    struct curl_slist* headers = nullptr;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    const std::string apiKeyHeader = "X-API-Key: " + config_.apiKey;
    headers = curl_slist_append(headers, apiKeyHeader.c_str());

    curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, payload.c_str());
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, payload.size());
    curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, 1500L);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeCallback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &responseBody);

    const CURLcode code = curl_easy_perform(curl);
    long httpCode = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &httpCode);

    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);

    if (code != CURLE_OK || httpCode < 200 || httpCode >= 300) {
        return std::nullopt;
    }

    return responseBody;
#elif defined(_WIN32)
    const auto parsedUrl = parseHttpUrl(url);
    if (!parsedUrl.has_value()) {
        return std::nullopt;
    }

    const std::wstring host = utf8ToWide(parsedUrl->host);
    const std::wstring path = utf8ToWide(parsedUrl->path);
    if (host.empty() || path.empty()) {
        return std::nullopt;
    }

    HINTERNET session = WinHttpOpen(L"CafeteriaSimulation/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
    if (session == nullptr) {
        return std::nullopt;
    }

    HINTERNET connect = WinHttpConnect(session, host.c_str(), parsedUrl->port, 0);
    if (connect == nullptr) {
        WinHttpCloseHandle(session);
        return std::nullopt;
    }

    const DWORD openRequestFlags = parsedUrl->https ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET request = WinHttpOpenRequest(connect, L"POST", path.c_str(), nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, openRequestFlags);
    if (request == nullptr) {
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return std::nullopt;
    }

    const std::string headerStr = "Content-Type: application/json\r\nX-API-Key: " + config_.apiKey + "\r\n";
    const std::wstring headersWide = utf8ToWide(headerStr);
    const DWORD payloadSize = static_cast<DWORD>(payload.size());
    const BOOL sent = WinHttpSendRequest(
        request,
        headersWide.c_str(),
        static_cast<DWORD>(-1L),
        const_cast<char*>(payload.data()),
        payloadSize,
        payloadSize,
        0);
    if (!sent) {
        WinHttpCloseHandle(request);
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return std::nullopt;
    }

    if (!WinHttpReceiveResponse(request, nullptr)) {
        WinHttpCloseHandle(request);
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return std::nullopt;
    }

    DWORD statusCode = 0;
    DWORD statusCodeSize = sizeof(statusCode);
    if (!WinHttpQueryHeaders(
            request,
            WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
            WINHTTP_HEADER_NAME_BY_INDEX,
            &statusCode,
            &statusCodeSize,
            WINHTTP_NO_HEADER_INDEX) ||
        statusCode < 200 ||
        statusCode >= 300) {
        WinHttpCloseHandle(request);
        WinHttpCloseHandle(connect);
        WinHttpCloseHandle(session);
        return std::nullopt;
    }

    std::string responseBody;
    while (true) {
        DWORD availableBytes = 0;
        if (!WinHttpQueryDataAvailable(request, &availableBytes)) {
            break;
        }
        if (availableBytes == 0) {
            break;
        }

        std::string buffer(static_cast<std::size_t>(availableBytes), '\0');
        DWORD readBytes = 0;
        if (!WinHttpReadData(request, &buffer[0], availableBytes, &readBytes)) {
            break;
        }
        buffer.resize(static_cast<std::size_t>(readBytes));
        responseBody += buffer;
    }

    WinHttpCloseHandle(request);
    WinHttpCloseHandle(connect);
    WinHttpCloseHandle(session);
    return responseBody;
#else
    (void)url;
    (void)payload;
    return std::nullopt;
#endif
}

std::string DecisionClient::buildStateJson(const SimulationState& state) const {
#ifdef CAFETERIA_HAS_NLOHMANN_JSON
    nlohmann::json j;
    j["schemaVersion"] = kSchemaVersion;
    j["simTime"] = state.simTime;
    j["queueLengths"] = state.queueLengths;
    j["windowCount"] = static_cast<int>(state.queueLengths.size());
    j["availableSeats"] = state.availableSeats;
    j["waitingForSeat"] = state.waitingForSeat;
    j["totalQueueWaitSec"] = state.totalQueueWaitSec;
    j["avgQueueWaitSec"] = state.avgQueueWaitSec;
    j["maxQueueWaitSec"] = state.maxQueueWaitSec;
    j["p50QueueWaitSec"] = state.p50QueueWaitSec;
    j["p90QueueWaitSec"] = state.p90QueueWaitSec;
    j["p99QueueWaitSec"] = state.p99QueueWaitSec;
    j["totalArrived"] = state.totalArrived;
    j["totalServed"] = state.totalServed;
    j["totalSeated"] = state.totalSeated;
    j["totalFinishedDining"] = state.totalFinishedDining;
    j["newArrivals"] = state.newArrivals;
    return j.dump();
#else
    std::ostringstream oss;
    oss << "{";
    oss << "\"schemaVersion\":\"" << kSchemaVersion << "\",";
    oss << "\"simTime\":" << state.simTime << ",";
    oss << "\"queueLengths\":[";
    for (std::size_t i = 0; i < state.queueLengths.size(); ++i) {
        if (i > 0) {
            oss << ",";
        }
        oss << state.queueLengths[i];
    }
    oss << "],";
    oss << "\"windowCount\":" << static_cast<int>(state.queueLengths.size()) << ",";
    oss << "\"availableSeats\":" << state.availableSeats << ",";
    oss << "\"waitingForSeat\":" << state.waitingForSeat << ",";
    oss << "\"totalQueueWaitSec\":" << state.totalQueueWaitSec << ",";
    oss << "\"avgQueueWaitSec\":" << state.avgQueueWaitSec << ",";
    oss << "\"maxQueueWaitSec\":" << state.maxQueueWaitSec << ",";
    oss << "\"p50QueueWaitSec\":" << state.p50QueueWaitSec << ",";
    oss << "\"p90QueueWaitSec\":" << state.p90QueueWaitSec << ",";
    oss << "\"p99QueueWaitSec\":" << state.p99QueueWaitSec << ",";
    oss << "\"totalArrived\":" << state.totalArrived << ",";
    oss << "\"totalServed\":" << state.totalServed << ",";
    oss << "\"totalSeated\":" << state.totalSeated << ",";
    oss << "\"totalFinishedDining\":" << state.totalFinishedDining << ",";
    oss << "\"newArrivals\":" << state.newArrivals;
    oss << "}";
    return oss.str();
#endif
}

std::string DecisionClient::buildDecisionRequestJson(const SimulationState& state, int newArrivals) const {
#ifdef CAFETERIA_HAS_NLOHMANN_JSON
    nlohmann::json j;
    j["schemaVersion"] = kSchemaVersion;
    j["simTime"] = state.simTime;
    j["queueLengths"] = state.queueLengths;
    j["windowCount"] = static_cast<int>(state.queueLengths.size());
    j["availableSeats"] = state.availableSeats;
    j["waitingForSeat"] = state.waitingForSeat;
    j["totalQueueWaitSec"] = state.totalQueueWaitSec;
    j["avgQueueWaitSec"] = state.avgQueueWaitSec;
    j["maxQueueWaitSec"] = state.maxQueueWaitSec;
    j["p50QueueWaitSec"] = state.p50QueueWaitSec;
    j["p90QueueWaitSec"] = state.p90QueueWaitSec;
    j["p99QueueWaitSec"] = state.p99QueueWaitSec;
    j["newArrivals"] = newArrivals;
    return j.dump();
#else
    std::ostringstream oss;
    oss << "{";
    oss << "\"schemaVersion\":\"" << kSchemaVersion << "\",";
    oss << "\"simTime\":" << state.simTime << ",";
    oss << "\"queueLengths\":[";
    for (std::size_t i = 0; i < state.queueLengths.size(); ++i) {
        if (i > 0) {
            oss << ",";
        }
        oss << state.queueLengths[i];
    }
    oss << "],";
    oss << "\"windowCount\":" << static_cast<int>(state.queueLengths.size()) << ",";
    oss << "\"availableSeats\":" << state.availableSeats << ",";
    oss << "\"waitingForSeat\":" << state.waitingForSeat << ",";
    oss << "\"totalQueueWaitSec\":" << state.totalQueueWaitSec << ",";
    oss << "\"avgQueueWaitSec\":" << state.avgQueueWaitSec << ",";
    oss << "\"maxQueueWaitSec\":" << state.maxQueueWaitSec << ",";
    oss << "\"p50QueueWaitSec\":" << state.p50QueueWaitSec << ",";
    oss << "\"p90QueueWaitSec\":" << state.p90QueueWaitSec << ",";
    oss << "\"p99QueueWaitSec\":" << state.p99QueueWaitSec << ",";
    oss << "\"newArrivals\":" << newArrivals;
    oss << "}";
    return oss.str();
#endif
}

std::optional<std::vector<int>> DecisionClient::parseAllocation(const std::string& responseBody, std::size_t expectedWindowCount) const {
#ifdef CAFETERIA_HAS_NLOHMANN_JSON
    try {
        const auto parsed = nlohmann::json::parse(responseBody);
        if (!parsed.contains("allocation") || !parsed["allocation"].is_array()) {
            return std::nullopt;
        }
        std::vector<int> result;
        for (const auto& value : parsed["allocation"]) {
            result.push_back(value.get<int>());
        }
        if (result.size() != expectedWindowCount) {
            return std::nullopt;
        }
        return result;
    } catch (...) {
        return std::nullopt;
    }
#else
    std::regex allocRegex("\\\"allocation\\\"\\s*:\\s*\\[([^\\]]*)\\]");
    std::smatch match;
    if (!std::regex_search(responseBody, match, allocRegex) || match.size() < 2) {
        return std::nullopt;
    }

    std::vector<int> values;
    std::stringstream ss(match[1].str());
    std::string token;
    while (std::getline(ss, token, ',')) {
        const std::string cleaned = trim(token);
        if (cleaned.empty()) {
            continue;
        }
        try {
            values.push_back(std::stoi(cleaned));
        } catch (...) {
            return std::nullopt;
        }
    }

    if (values.size() != expectedWindowCount) {
        return std::nullopt;
    }
    return values;
#endif
}

} // namespace cafeteria
