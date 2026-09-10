package com.un4seen.bass;

/**
 * un4seen BASS_FX 插件绑定（变速 / 变调 / 倒放）。
 *
 * 注意 libbass_fx.so 并不提供均衡器 —— 项目里的 10 段 EQ 是自写 DSP，
 * 这样能保持既有听感不变。
 *
 * 这里刻意只声明 Tempo / Reverse 这几个函数：BASS_FX 的 BFX 系列效果需要
 * 配套的参数容器类（BASS_BFX_*），而那些类的字段名必须与 libbass_fx.so 的
 * native 端严格一致，本项目没有从参考实现里取到它们的字段清单，
 * 因此不去声明，避免运行期 NoSuchFieldError。
 * 需要混响/合唱时用 BASS 内建的 DX8 效果即可（见 {@link BASS#BASS_FX_DX8_REVERB}）。
 */
public class BASS_FX {

    static {
        System.loadLibrary("bass_fx");
    }

    public static final int BASS_FX_FREESOURCE = 0x10000;

    // TempoCreate 算法标志
    public static final int BASS_FX_TEMPO_ALGO_LINEAR = 0x200;
    public static final int BASS_FX_TEMPO_ALGO_CUBIC = 0x400;
    public static final int BASS_FX_TEMPO_ALGO_SHANNON = 0x800;

    // 倒放方向
    public static final int BASS_FX_RVS_FORWARD = 0x1;
    public static final int BASS_FX_RVS_REVERSE = -0x1;

    public static native int BASS_FX_GetVersion();

    public static native int BASS_FX_TempoCreate(int chan, int flags);

    public static native int BASS_FX_TempoGetSource(int chan);

    public static native float BASS_FX_TempoGetRateRatio(int chan);

    public static native int BASS_FX_ReverseCreate(int chan, float decay, int flags);

    public static native int BASS_FX_ReverseGetSource(int chan);

    public static native boolean BASS_FX_BPM_Free(int handle);
}
