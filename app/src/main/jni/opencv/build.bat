set NDK_ROOT="D:\Apps\AndroidSdk\ndk\22.1.7171670"
set CMAKE="D:\Apps\AndroidSdk\cmake\3.18.1\bin\cmake.exe"

for %%a in ("armeabi-v7a" "arm64-v8a") do (
    %CMAKE% -G "Unix Makefiles" ^
            -DCMAKE_MAKE_PROGRAM="%NDK_ROOT%/prebuilt/windows-x86_64/bin/make.exe" ^
            -DCMAKE_TOOLCHAIN_FILE="%NDK_ROOT%/build/cmake/android.toolchain.cmake" ^
            -DANDROID_NDK="%NDK_ROOT%" ^
            -DOPENCV_ENABLE_NONFREE=ON ^
            -DANDROID_ABI="%%a" ^
            -DOPENCV_EXTRA_MODULES_PATH="%~dp0/opencv_contrib/modules/" ^
            -DANDROID_STL=c++_static ^
            -DWITH_CUDA=OFF ^
            -DWITH_MATLAB=OFF ^
            -DBUILD_ANDROID_PROJECTS=OFF ^
            -DBUILD_ANDROID_EXAMPLES=OFF ^
            -DBUILD_DOCS=OFF ^
            -DBUILD_OPENCV_JAVA=OFF ^
            -DBUILD_PERF_TESTS=OFF ^
            -DBUILD_TESTS=OFF ^
            -DCMAKE_INSTALL_PREFIX="%~dp0/build" ^
            -S ./opencv -B ./build/%%a

    %CMAKE% --build ./build/%%a
    %CMAKE% --install ./build/%%a
)

