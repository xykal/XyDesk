cmake_minimum_required(VERSION 3.22)
project(streamxy CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

# libstreamxy.so — inti native XyDesk (C++): protokol input, telemetri latensi,
# dan nantinya jalur decode/transport khusus game. Bebas dari Java/Kotlin
# kecuali file jni.cpp, supaya kode intinya bisa dipakai host/desktop juga.
add_library(streamxy SHARED
  streamxy/input.cpp
  streamxy/stats.cpp
  streamxy/jni.cpp)

target_include_directories(streamxy PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(streamxy PRIVATE -O2 -fno-exceptions -fno-rtti -Wall -Wextra)
target_link_libraries(streamxy log)
