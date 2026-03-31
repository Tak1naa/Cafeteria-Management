cmake_minimum_required(VERSION 3.16)
project(CafeteriaManagement LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(CAFETERIA_ENABLE_CURL "Enable libcurl for HTTP integration" ON)

add_executable(cafeteria_sim
    src/main.cpp
    src/version_generator.cpp
    src/window_queue.cpp
    src/table_matrix.cpp
    src/decision_client.cpp
    src/simulation_engine.cpp
)

target_include_directories(cafeteria_sim PRIVATE include)

if(WIN32)
    target_link_libraries(cafeteria_sim PRIVATE winhttp)
endif()

if(CAFETERIA_ENABLE_CURL)
    find_package(CURL REQUIRED)
    target_include_directories(cafeteria_sim PRIVATE ${CURL_INCLUDE_DIRS})
    target_link_libraries(cafeteria_sim PRIVATE ${CURL_LIBRARIES})
endif()
add_executable(spdlog-test test/spdlog-test.cpp)
find_package(spdlog CONFIG REQUIRED)
target_link_libraries(spdlog-test PRIVATE spdlog::spdlog)