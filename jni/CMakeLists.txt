cmake_minimum_required(VERSION 3.18)
set(PROJECT_NAME "nettleweb")
set(PROJECT_VERSION "0.1.0")
set(CMAKE_BUILD_TYPE, "Release")
project(${PROJECT_NAME} VERSION ${PROJECT_VERSION})

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -std=c17 -O3 -march=native -mtune=native")
set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED true)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -O3 -march=native -mtune=native")
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if (NOT DEFINED JAVA_HOME)
	set(JAVA_HOME "/usr/lib/jvm/java-17-openjdk-amd64");
endif()

include_directories("${JAVA_HOME}/include" "${JAVA_HOME}/include/linux")

file(GLOB_RECURSE SOURCE_FILE "src/*.cpp" "src/*.h" "src/*.c")
add_library(${PROJECT_NAME} SHARED ${SOURCE_FILE})
target_link_libraries(${PROJECT_NAME} PUBLIC)
