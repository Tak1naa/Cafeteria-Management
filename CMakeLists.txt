cmake_minimum_required(VERSION 3.16)
project(CafeteriaManagement LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(CAFETERIA_ENABLE_CURL "Enable libcurl for HTTP integration" ON)

add_executable(cafeteria_sim
    src/main.cpp
    src/person_generator.cpp
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
    find_package(CURL)
    if(CURL_FOUND)
        target_link_libraries(cafeteria_sim PRIVATE CURL::libcurl)
        target_compile_definitions(cafeteria_sim PRIVATE CAFETERIA_USE_LIBCURL=1)
    endif()
endif()
