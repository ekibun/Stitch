cmake_minimum_required(VERSION 3.6)

add_library(stitch SHARED stitch.cpp)

set(OpenCV_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../opencv/sdk/native/jni)
find_package(OpenCV REQUIRED)
target_link_libraries(stitch ${OpenCV_LIBS} jnigraphics)