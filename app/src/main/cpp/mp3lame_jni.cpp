#include <jni.h>
#include <android/log.h>

#include "lame.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "Mp3LameJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Mp3LameJNI", __VA_ARGS__)

/*
 * LAME 的最小 JNI 封装。
 *
 * 只暴露四个动作：初始化、编码一块 PCM、冲刷、释放。
 * 接口与 Kotlin 侧 com.fumi.voice.export.Mp3Encoder 的 native 声明一一对应，
 * 输入输出都用直接 ByteBuffer，避免在 JNI 层复制数据。
 */

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fumi_voice_export_Mp3Encoder_nativeInit(
        JNIEnv *env, jobject thiz,
        jint sampleRate, jint channels, jint bitRateKbps, jint quality) {
    lame_t gfp = lame_init();
    if (gfp == nullptr) {
        LOGE("lame_init 返回空");
        return 0;
    }

    lame_set_in_samplerate(gfp, sampleRate);
    lame_set_num_channels(gfp, channels);
    lame_set_brate(gfp, bitRateKbps);
    lame_set_quality(gfp, quality);
    lame_set_mode(gfp, channels == 1 ? MONO : JOINT_STEREO);

    if (lame_init_params(gfp) < 0) {
        LOGE("lame_init_params 失败");
        lame_close(gfp);
        return 0;
    }

    LOGI("LAME 就绪: %d Hz, %d 声道, %d kbps, 版本=%s",
         sampleRate, channels, bitRateKbps, get_lame_version());
    return reinterpret_cast<jlong>(gfp);
}

JNIEXPORT jint JNICALL
Java_com_fumi_voice_export_Mp3Encoder_nativeEncode(
        JNIEnv *env, jobject thiz,
        jlong handle, jobject input, jint length, jobject out) {
    auto *gfp = reinterpret_cast<lame_t>(handle);
    if (gfp == nullptr || input == nullptr || out == nullptr) return 0;

    auto *pcm = static_cast<unsigned char *>(env->GetDirectBufferAddress(input));
    auto *mp3 = static_cast<unsigned char *>(env->GetDirectBufferAddress(out));
    if (pcm == nullptr || mp3 == nullptr) return 0;

    jlong capacity = env->GetDirectBufferCapacity(out);
    if (capacity <= 0) return 0;

    const int channels = lame_get_num_channels(gfp);
    const int samplesPerChannel = length / static_cast<int>(sizeof(short)) / channels;
    if (samplesPerChannel <= 0) return 0;

    auto *samples = reinterpret_cast<short *>(pcm);
    int written;
    if (channels == 1) {
        // 单声道：LAME 约定左右指针传同一块缓冲
        written = lame_encode_buffer(gfp, samples, samples, samplesPerChannel,
                                     mp3, static_cast<int>(capacity));
    } else {
        written = lame_encode_buffer_interleaved(gfp, samples, samplesPerChannel,
                                                 mp3, static_cast<int>(capacity));
    }

    if (written < 0) {
        LOGE("lame_encode 返回 %d", written);
        return 0;
    }
    return written;
}

JNIEXPORT jint JNICALL
Java_com_fumi_voice_export_Mp3Encoder_nativeFlush(
        JNIEnv *env, jobject thiz, jlong handle, jobject out) {
    auto *gfp = reinterpret_cast<lame_t>(handle);
    if (gfp == nullptr || out == nullptr) return 0;

    auto *mp3 = static_cast<unsigned char *>(env->GetDirectBufferAddress(out));
    if (mp3 == nullptr) return 0;

    jlong capacity = env->GetDirectBufferCapacity(out);
    if (capacity <= 0) return 0;

    int written = lame_encode_flush(gfp, mp3, static_cast<int>(capacity));
    if (written < 0) return 0;
    return written;
}

JNIEXPORT void JNICALL
Java_com_fumi_voice_export_Mp3Encoder_nativeClose(
        JNIEnv *env, jobject thiz, jlong handle) {
    auto *gfp = reinterpret_cast<lame_t>(handle);
    if (gfp != nullptr) {
        lame_close(gfp);
    }
}

} // extern "C"
