cmake_minimum_required(VERSION 3.22.1)

project("mp3lame_jni")

# ---------------------------------------------------------------------
# LAME 的 MP3 编码器
#
# 源文件清单照抄上游 libmp3lame/Makefile.am，只去掉 mpglib_interface.c ——
# 它属于解码侧（lame_decode*），本工程只做编码，去掉后没有任何未定义符号。
#
# LAME 源码不引用 autotools 生成的 config.h / lame_config.h，
# 因此这里不需要额外生成配置头，直接编即可。
# ---------------------------------------------------------------------
add_library(mp3lame_jni SHARED
    mp3lame_jni.cpp

    lame/VbrTag.c
    lame/bitstream.c
    lame/encoder.c
    lame/fft.c
    lame/gain_analysis.c
    lame/id3tag.c
    lame/lame.c
    lame/newmdct.c
    lame/presets.c
    lame/psymodel.c
    lame/quantize.c
    lame/quantize_pvt.c
    lame/reservoir.c
    lame/set_get.c
    lame/tables.c
    lame/takehiro.c
    lame/util.c
    lame/vbrquantize.c
    lame/version.c
)

target_include_directories(mp3lame_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/lame
)

# 让 LAME 的 #ifdef HAVE_CONFIG_H 分支生效，从而包含上面的 lame/config.h
target_compile_definitions(mp3lame_jni PRIVATE
    HAVE_CONFIG_H=1
)

target_compile_options(mp3lame_jni PRIVATE
    -O2
    -ffunction-sections
    -fdata-sections
)

target_link_options(mp3lame_jni PRIVATE
    -Wl,--gc-sections
)

# log 用于日志，m 是 LAME 的数学库依赖
target_link_libraries(mp3lame_jni
    log
    m
)
