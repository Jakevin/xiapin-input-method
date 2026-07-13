cmake_minimum_required(VERSION 3.22.1)
project(xiapin_rime_jni CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 由 app/build.gradle.kts 傳入
# - ANDROID_ABI（Gradle/NDK 自動提供）
# - DRIME_BUILD_DIR（librime 靜態庫構建輸出根目錄）

if(NOT RIME_BUILD_DIR)
  set(RIME_BUILD_DIR "/Volumes/1Tb/projects/android-rime-build")
endif()

set(RIME_OUT "${RIME_BUILD_DIR}/out/${ANDROID_ABI}")

# 蒐集該 ABI 下所有已編好的靜態庫 .a
file(GLOB_RECURSE RIME_STATIC_LIBS "${RIME_OUT}/*.a")

if(NOT RIME_STATIC_LIBS)
  message(FATAL_ERROR "找不到 librime 靜態庫，請先執行 librime NDK 編譯。RIME_OUT=${RIME_OUT}")
endif()

message(STATUS "Rime static libs found: ${RIME_STATIC_LIBS}")

add_library(xiapin_rime SHARED
  rime_jni.cc)

# include librime 標頭（rime_api.h 等）
target_include_directories(xiapin_rime PRIVATE
  "${RIME_BUILD_DIR}/librime/include"
  "${RIME_BUILD_DIR}/librime/src")

# 連結 librime + 所有依賴靜態庫（用 --start-group/--end-group 解決靜態庫循環依賴順序）
target_link_libraries(xiapin_rime
  -Wl,--start-group
  ${RIME_STATIC_LIBS}
  -Wl,--end-group
  log
  android
  z
  dl)

# 讓 linker 保留所有符號（靜態庫可能有多餘未用符號）
target_link_options(xiapin_rime PRIVATE
  "-Wl,--allow-multiple-definition")
