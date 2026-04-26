cmake_minimum_required(VERSION 3.16)
project(CafeteriaManagement LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(CAFETERIA_ENABLE_CURL "Enable libcurl for HTTP integration" ON)
find_package(spdlog CONFIG REQUIRED)
find_package(fmt CONFIG REQUIRED)
if(CAFETERIA_ENABLE_CURL)
    find_package(CURL REQUIRED)
endif()
add_executable(cafeteria_sim
    src/main.cpp
    src/window_queue.cpp
    src/table_matrix.cpp
    src/decision_client.cpp
    src/simulation_engine.cpp
    src/data_recorder.cpp
)

target_include_directories(cafeteria_sim PRIVATE include)

target_link_libraries(cafeteria_sim PRIVATE spdlog::spdlog fmt::fmt)

if(CAFETERIA_ENABLE_CURL)
    target_link_libraries(cafeteria_sim PRIVATE CURL::libcurl)
endif()
if(WIN32)
    target_link_libraries(cafeteria_sim PRIVATE winhttp)
endif()