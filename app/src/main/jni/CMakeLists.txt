cmake_minimum_required(VERSION 3.13)

project(stitch)

include(ExternalProject)

ExternalProject_Add(opencv_lib
        PREFIX "opencv_lib"
        SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/opencv"
        CMAKE_ARGS
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID=${ANDROID}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_NDK=${ANDROID_NDK}
        -DCMAKE_BUILD_TYPE=Release
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DOPENCV_ENABLE_NONFREE=ON
        -DOPENCV_EXTRA_MODULES_PATH=${CMAKE_CURRENT_LIST_DIR}/opencv_contrib/modules/
        -DANDROID_STL=c++_static
        -DBUILD_ANDROID_PROJECTS=OFF
        -DBUILD_EXAMPLES=OFF
        -DBUILD_DOCS=OFF
        -DBUILD_PERF_TESTS=OFF
        -DBUILD_TESTS=OFF
        -DBUILD_LIST=calib3d,xfeatures2d,imgproc
        -DCMAKE_INSTALL_PREFIX=${CMAKE_CURRENT_BINARY_DIR}/opencv
        )

add_library(stitch SHARED ${CMAKE_CURRENT_LIST_DIR}/stitch.cpp)
add_dependencies(stitch opencv_lib)

set(OpenCV_DIR "${CMAKE_CURRENT_BINARY_DIR}/opencv/sdk/native")

target_link_directories(stitch PRIVATE
        "${OpenCV_DIR}/3rdparty/libs/${ANDROID_ABI}"
        "${OpenCV_DIR}/staticlibs/${ANDROID_ABI}")
target_include_directories(stitch PRIVATE "${OpenCV_DIR}/jni/include")

find_library(log-lib log)
if (${ANDROID_ABI} MATCHES "armeabi-v7a|arm64-v8a")
    set(HAL_LIBS tegra_hal)
else()
    set(HAL_LIBS ippicv ippiw)
endif ()

target_link_libraries(stitch ${log-lib} ${HAL_LIBS} jnigraphics z cpufeatures ittnotify
        opencv_xfeatures2d opencv_imgproc opencv_flann opencv_features2d opencv_core opencv_calib3d)