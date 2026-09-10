/*
 * LAME 在 Android（bionic + arm64）上的 config.h。
 *
 * LAME 上游用 autotools 生成这个文件，把「这台机器有哪些头文件、有哪些函数」
 * 以宏的形式喂给源码。本工程直接编上游源码、不跑 autotools，因此手写一份等价物。
 *
 * 这些宏不是可有可无的优化开关，而是决定源码走哪条分支：
 * 例如 id3tag.c 在缺少 STDC_HEADERS 时会把 strchr 宏定义成 BSD 的 index()，
 * 而 bionic 的 libc 并不导出 index，最终会在链接期报未定义符号。
 */
#ifndef LAME_ANDROID_CONFIG_H
#define LAME_ANDROID_CONFIG_H

/*
 * bionic 的 <strings.h> 提供 bcopy；LAME 的 gain_analysis.c 用到它却没有
 * 自己包含该头文件（上游依赖 config.h 间接引入），这里补上。
 */
#include <strings.h>

/* 标准 C 头文件齐全 */
#define STDC_HEADERS 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STDLIB_H 1
#define HAVE_MEMORY_H 1
#define HAVE_MATH_H 1
#define HAVE_LIMITS_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_UNISTD_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_CTYPE_H 1

/* 这些函数 bionic 都有 */
#define HAVE_STRCHR 1
#define HAVE_STRRCHR 1
#define HAVE_MEMCPY 1
#define HAVE_MEMMOVE 1
#define HAVE_MEMSET 1
#define HAVE_STRTOL 1
#define HAVE_STRERROR 1

/*
 * Android 没有 <ieeefp.h>。
 * LAME 只在 fast_log2（util.c）里用这个类型，做法是把 32 位浮点当整数取指数位，
 * 别名成 float 与原意一致；arm64 的 float 就是 IEEE754 单精度。
 */
typedef float ieee754_float32_t;

#endif /* LAME_ANDROID_CONFIG_H */
