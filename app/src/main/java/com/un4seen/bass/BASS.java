package com.un4seen.bass;

import android.content.res.AssetManager;

import java.nio.ByteBuffer;

/**
 * un4seen BASS 的官方 Android Java 绑定（2.4）。
 *
 * 这里只声明本项目真正会调用的 native 方法，但**内部类必须与原版完全一致** ——
 * libbass.so 只导出短名符号（没有重载长名），native 端是通过 FindClass +
 * GetFieldID 按名字反射读写这些内部类的字段来交换数据的，
 * 字段名或类型对不上就会在运行时抛 NoSuchFieldError。
 *
 * 原版字段清单由 MIDI Clef 9.0.0 的 dex 反汇编取得，未做任何猜测。
 */
public class BASS {

    static {
        System.loadLibrary("bass");
    }

    // ---------------------------------------------------------------
    // 版本
    // ---------------------------------------------------------------
    public static final int BASSVERSION = 0x204;

    // ---------------------------------------------------------------
    // 通道状态
    // ---------------------------------------------------------------
    public static final int BASS_ACTIVE_STOPPED = 0;
    public static final int BASS_ACTIVE_PLAYING = 1;
    public static final int BASS_ACTIVE_STALLED = 2;
    public static final int BASS_ACTIVE_PAUSED = 3;
    public static final int BASS_ACTIVE_PAUSED_DEVICE = 4;

    // ---------------------------------------------------------------
    // 通道属性
    // ---------------------------------------------------------------
    public static final int BASS_ATTRIB_FREQ = 0x1;
    public static final int BASS_ATTRIB_VOL = 0x2;
    public static final int BASS_ATTRIB_PAN = 0x3;
    public static final int BASS_ATTRIB_EAXMIX = 0x4;
    public static final int BASS_ATTRIB_NOBUFFER = 0x5;
    public static final int BASS_ATTRIB_VBR = 0x6;
    public static final int BASS_ATTRIB_CPU = 0x7;
    public static final int BASS_ATTRIB_SRC = 0x8;
    public static final int BASS_ATTRIB_NET_RESUME = 0x9;
    public static final int BASS_ATTRIB_SCANINFO = 0xa;
    public static final int BASS_ATTRIB_NORAMP = 0xb;
    public static final int BASS_ATTRIB_BITRATE = 0xc;
    public static final int BASS_ATTRIB_BUFFER = 0xd;
    public static final int BASS_ATTRIB_GRANULE = 0xe;
    public static final int BASS_ATTRIB_USER = 0xf;
    public static final int BASS_ATTRIB_TAIL = 0x10;
    public static final int BASS_ATTRIB_PUSH_LIMIT = 0x11;
    public static final int BASS_ATTRIB_DOWNLOADPROC = 0x12;
    public static final int BASS_ATTRIB_VOLDSP = 0x13;
    public static final int BASS_ATTRIB_VOLDSP_PRIORITY = 0x14;

    // BASS_FX 的 tempo / 变调属性（作用在 BASS_FX_TempoCreate 返回的通道上）
    public static final int BASS_ATTRIB_TEMPO = 0x10000;
    public static final int BASS_ATTRIB_TEMPO_PITCH = 0x10001;
    public static final int BASS_ATTRIB_TEMPO_FREQ = 0x10002;
    public static final int BASS_ATTRIB_TEMPO_OPTION_USE_AA_FILTER = 0x10010;
    public static final int BASS_ATTRIB_TEMPO_OPTION_AA_FILTER_LENGTH = 0x10011;
    public static final int BASS_ATTRIB_TEMPO_OPTION_USE_QUICKALGO = 0x10012;
    public static final int BASS_ATTRIB_TEMPO_OPTION_SEQUENCE_MS = 0x10013;
    public static final int BASS_ATTRIB_TEMPO_OPTION_SEEKWINDOW_MS = 0x10014;
    public static final int BASS_ATTRIB_TEMPO_OPTION_OVERLAP_MS = 0x10015;
    public static final int BASS_ATTRIB_TEMPO_OPTION_PREVENT_CLICK = 0x10016;
    public static final int BASS_ATTRIB_REVERSE_DIR = 0x11000;

    // MIDI 专用属性（0x12000 段）
    public static final int BASS_ATTRIB_MIDI_PPQN = 0x12000;
    public static final int BASS_ATTRIB_MIDI_CPU = 0x12001;
    public static final int BASS_ATTRIB_MIDI_CHANS = 0x12002;
    public static final int BASS_ATTRIB_MIDI_VOICES = 0x12003;
    public static final int BASS_ATTRIB_MIDI_VOICES_ACTIVE = 0x12004;
    public static final int BASS_ATTRIB_MIDI_STATE = 0x12005;
    public static final int BASS_ATTRIB_MIDI_SRC = 0x12006;
    public static final int BASS_ATTRIB_MIDI_KILL = 0x12007;
    public static final int BASS_ATTRIB_MIDI_SPEED = 0x12008;
    public static final int BASS_ATTRIB_MIDI_REVERB = 0x12009;
    public static final int BASS_ATTRIB_MIDI_VOL = 0x1200a;
    public static final int BASS_ATTRIB_MIDI_TRACK_VOL = 0x12100;

    // ---------------------------------------------------------------
    // 设备 / 初始化标志
    // ---------------------------------------------------------------
    public static final int BASS_DEVICE_8BITS = 0x1;
    public static final int BASS_DEVICE_MONO = 0x2;
    public static final int BASS_DEVICE_3D = 0x4;
    public static final int BASS_DEVICE_16BITS = 0x8;
    public static final int BASS_DEVICE_LATENCY = 0x100;
    public static final int BASS_DEVICE_SPEAKERS = 0x800;
    public static final int BASS_DEVICE_NOSPEAKER = 0x1000;
    public static final int BASS_DEVICE_FREQ = 0x4000;
    public static final int BASS_DEVICE_AUDIOTRACK = 0x20000;

    // ---------------------------------------------------------------
    // 全局配置
    // ---------------------------------------------------------------
    public static final int BASS_CONFIG_BUFFER = 0x0;
    public static final int BASS_CONFIG_UPDATEPERIOD = 0x1;
    public static final int BASS_CONFIG_DEV_BUFFER = 0x1b;
    public static final int BASS_CONFIG_DEV_PERIOD = 0x35;
    public static final int BASS_CONFIG_FLOATDSP = 0x9;
    public static final int BASS_CONFIG_FLOAT = 0x36;
    public static final int BASS_CONFIG_SRC = 0x2b;
    public static final int BASS_CONFIG_SRC_SAMPLE = 0x2c;
    public static final int BASS_CONFIG_ANDROID_AAUDIO = 0x43;
    public static final int BASS_CONFIG_ANDROID_SESSIONID = 0x3e;
    public static final int BASS_CONFIG_HANDLES = 0x29;
    public static final int BASS_CONFIG_VERIFY = 0x17;
    public static final int BASS_CONFIG_DEV_NONSTOP = 0x32;

    // ---------------------------------------------------------------
    // 播放模式 / 位置
    // ---------------------------------------------------------------
    public static final int BASS_SAMPLE_8BITS = 0x1;
    public static final int BASS_SAMPLE_MONO = 0x2;
    public static final int BASS_SAMPLE_LOOP = 0x4;
    public static final int BASS_SAMPLE_3D = 0x8;
    public static final int BASS_SAMPLE_SOFTWARE = 0x10;
    public static final int BASS_SAMPLE_FLOAT = 0x100;
    public static final int BASS_SAMPLE_FX = 0x80;

    public static final int BASS_STREAM_PRESCAN = 0x20000;
    public static final int BASS_STREAM_AUTOFREE = 0x40000;
    public static final int BASS_STREAM_RESTRATE = 0x80000;
    public static final int BASS_STREAM_BLOCK = 0x100000;
    public static final int BASS_STREAM_DECODE = 0x200000;
    public static final int BASS_STREAM_STATUS = 0x800000;

    public static final int BASS_POS_BYTE = 0x0;
    public static final int BASS_POS_MUSIC_ORDER = 0x1;
    public static final int BASS_POS_OGG = 0x3;
    public static final int BASS_POS_END = 0x10;
    public static final int BASS_POS_RESET = 0x2000000;
    public static final int BASS_POS_RELATIVE = 0x4000000;
    public static final int BASS_POS_INEXACT = 0x8000000;
    public static final int BASS_POS_DECODE = 0x10000000;
    public static final int BASS_POS_DECODETO = 0x20000000;

    public static final int BASS_FILEPOS_CURRENT = 0x0;
    public static final int BASS_FILEPOS_DOWNLOAD = 0x1;
    public static final int BASS_FILEPOS_END = 0x2;
    public static final int BASS_FILEPOS_START = 0x3;
    public static final int BASS_FILEPOS_BUFFER = 0x5;
    public static final int BASS_FILEPOS_SIZE = 0x8;

    // ---------------------------------------------------------------
    // 同步
    // ---------------------------------------------------------------
    public static final int BASS_SYNC_POS = 0x0;
    public static final int BASS_SYNC_MUSICINST = 0x1;
    public static final int BASS_SYNC_END = 0x2;
    public static final int BASS_SYNC_META = 0x4;
    public static final int BASS_SYNC_SLIDE = 0x5;
    public static final int BASS_SYNC_STALL = 0x6;
    public static final int BASS_SYNC_DOWNLOAD = 0x7;
    public static final int BASS_SYNC_FREE = 0x8;
    public static final int BASS_SYNC_MUSICPOS = 0xa;
    public static final int BASS_SYNC_SETPOS = 0xb;
    public static final int BASS_SYNC_MIDI_MARK = 0x10000;
    public static final int BASS_SYNC_MIDI_EVENT = 0x10004;
    public static final int BASS_SYNC_MIDI_TICK = 0x10005;
    public static final int BASS_SYNC_ONETIME = 0x80000000;
    public static final int BASS_SYNC_MIXTIME = 0x40000000;
    public static final int BASS_SYNC_THREAD = 0x20000000;

    // ---------------------------------------------------------------
    // 内置 FX（DX8 / BASS_FX 系列）
    // ---------------------------------------------------------------
    public static final int BASS_FX_DX8_CHORUS = 0x0;
    public static final int BASS_FX_DX8_COMPRESSOR = 0x1;
    public static final int BASS_FX_DX8_DISTORTION = 0x2;
    public static final int BASS_FX_DX8_ECHO = 0x3;
    public static final int BASS_FX_DX8_FLANGER = 0x4;
    public static final int BASS_FX_DX8_GARGLE = 0x5;
    public static final int BASS_FX_DX8_I3DL2REVERB = 0x6;
    public static final int BASS_FX_DX8_PARAMEQ = 0x7;
    public static final int BASS_FX_DX8_REVERB = 0x8;

    // ---------------------------------------------------------------
    // 内部类：native 端按名字反射读写，字段名/类型不可改
    // ---------------------------------------------------------------

    /** 用于返回单个 float 的容器。 */
    public static class FloatValue {
        public float value;
    }

    public static class BASS_CHANNELINFO {
        public int freq;
        public int chans;
        public int flags;
        public int ctype;
        public int origres;
        public int plugin;
        public int sample;
        public String filename;
    }

    public static class BASS_DEVICEINFO {
        public String name;
        public String driver;
        public int flags;
    }

    public static class BASS_INFO {
        public int flags;
        public int hwsize;
        public int hwfree;
        public int freesam;
        public int free3d;
        public int minrate;
        public int maxrate;
        public int eax;
        public int minbuf;
        public int dsver;
        public int latency;
        public int initflags;
        public int speakers;
        public int freq;
    }

    public static class BASS_SAMPLE {
        public int freq;
        public float volume;
        public float pan;
        public int flags;
        public int length;
        public int max;
        public int origres;
        public int chans;
        public int mingap;
        public int mode3d;
        public float mindist;
        public float maxdist;
        public int iangle;
        public int oangle;
        public float outvol;
        public int vam;
        public int priority;
    }

    public static class BASS_RECORDINFO {
        public int flags;
        public int formats;
        public int inputs;
        public boolean singlein;
        public int freq;
    }

    public static class BASS_3DVECTOR {
        public float x;
        public float y;
        public float z;
    }

    public static class BASS_PLUGINFORM {
        public int ctype;
        public String name;
        public String exts;
    }

    public static class BASS_PLUGININFO {
        public int version;
        public int formatc;
        public BASS_PLUGINFORM[] formats;
    }

    public static class BASS_DX8_CHORUS {
        public float fWetDryMix;
        public float fDepth;
        public float fFeedback;
        public float fFrequency;
        public int lWaveform;
        public float fDelay;
        public int lPhase;
    }

    public static class BASS_DX8_FLANGER {
        public float fWetDryMix;
        public float fDepth;
        public float fFeedback;
        public float fFrequency;
        public int lWaveform;
        public float fDelay;
        public int lPhase;
    }

    public static class BASS_DX8_ECHO {
        public float fWetDryMix;
        public float fFeedback;
        public float fLeftDelay;
        public float fRightDelay;
        public boolean lPanDelay;
    }

    public static class BASS_DX8_DISTORTION {
        public float fGain;
        public float fEdge;
        public float fPostEQCenterFrequency;
        public float fPostEQBandwidth;
        public float fPreLowpassCutoff;
    }

    public static class BASS_DX8_REVERB {
        public float fInGain;
        public float fReverbMix;
        public float fReverbTime;
        public float fHighFreqRTRatio;
    }

    public static class BASS_DX8_PARAMEQ {
        public float fCenter;
        public float fBandwidth;
        public float fGain;
    }

    public static class BASS_FX_VOLUME_PARAM {
        public float fTarget;
        public float fCurrent;
        public float fTime;
        public int lCurve;
    }

    public static class TAG_ID3 {
        public String id;
        public String title;
        public String artist;
        public String album;
        public String year;
        public String comment;
        public byte genre;
        public byte track;
    }

    public static class TAG_APE_BINARY {
        public String key;
        public ByteBuffer data;
        public int length;
    }

    /** 用来在 Java 侧直接读写资源包里的资产。 */
    public static class Asset {
        public AssetManager manager;
        public String file;

        public Asset() {
        }

        public Asset(AssetManager m, String f) {
            manager = m;
            file = f;
        }
    }

    /** 位运算小工具（与原版保持一致，供 native 调用）。 */
    public static class Utils {
        public static int LOWORD(int i) {
            return i & 0xffff;
        }

        public static int HIWORD(int i) {
            return (i >> 16) & 0xffff;
        }

        public static int LOBYTE(int i) {
            return i & 0xff;
        }

        public static int HIBYTE(int i) {
            return (i >> 8) & 0xff;
        }

        public static int MAKEWORD(int lo, int hi) {
            return (lo & 0xff) | ((hi & 0xff) << 8);
        }

        public static int MAKELONG(int lo, int hi) {
            return (lo & 0xffff) | (hi << 16);
        }
    }

    public interface SYNCPROC {
        void SYNCPROC(int handle, int channel, int data, Object user);
    }

    public interface DSPPROC {
        void DSPPROC(int handle, int channel, ByteBuffer buffer, int length, Object user);
    }

    public interface DOWNLOADPROC {
        void DOWNLOADPROC(ByteBuffer buffer, int length, Object user);
    }

    public interface STREAMPROC {
        int STREAMPROC(int handle, ByteBuffer buffer, int length, Object user);
    }

    public interface RECORDPROC {
        boolean RECORDPROC(int handle, ByteBuffer buffer, int length, Object user);
    }

    public interface BASS_FILEPROCS {
        void FILECLOSEPROC(Object user);

        long FILELENPROC(Object user);

        int FILEREADPROC(ByteBuffer buffer, int length, Object user);

        boolean FILESEEKPROC(long offset, Object user);
    }

    // ---------------------------------------------------------------
    // native 方法
    // ---------------------------------------------------------------

    public static native boolean BASS_Init(int device, int freq, int flags);

    public static native boolean BASS_Free();

    public static native int BASS_ErrorGetCode();

    public static native int BASS_GetVersion();

    public static native boolean BASS_SetConfig(int option, int value);

    public static native int BASS_GetConfig(int option);

    public static native boolean BASS_Start();

    public static native boolean BASS_Stop();

    public static native boolean BASS_Pause();

    public static native boolean BASS_Update(int length);

    public static native float BASS_GetVolume();

    public static native boolean BASS_SetVolume(float volume);

    public static native int BASS_GetDevice();

    public static native boolean BASS_SetDevice(int device);

    public static native boolean BASS_GetInfo(BASS_INFO info);

    public static native int BASS_GetCPU();

    public static native boolean BASS_ChannelPlay(int handle, boolean restart);

    public static native boolean BASS_ChannelPause(int handle);

    public static native boolean BASS_ChannelStop(int handle);

    public static native int BASS_ChannelIsActive(int handle);

    public static native boolean BASS_ChannelSetAttribute(int handle, int attrib, float value);

    public static native boolean BASS_ChannelGetAttribute(int handle, int attrib, FloatValue value);

    public static native boolean BASS_ChannelGetInfo(int handle, BASS_CHANNELINFO info);

    public static native long BASS_ChannelGetLength(int handle, int mode);

    public static native long BASS_ChannelGetPosition(int handle, int mode);

    public static native boolean BASS_ChannelSetPosition(int handle, long pos, int mode);

    public static native double BASS_ChannelBytes2Seconds(int handle, long pos);

    public static native long BASS_ChannelSeconds2Bytes(int handle, double pos);

    public static native int BASS_ChannelGetData(int handle, ByteBuffer buffer, int length);

    public static native int BASS_ChannelGetLevel(int handle);

    public static native boolean BASS_ChannelGetLevelEx(int handle, float[] levels, float length, int flags);

    public static native int BASS_ChannelSetSync(int handle, int type, long param, SYNCPROC proc, Object user);

    public static native boolean BASS_ChannelRemoveSync(int handle, int sync);

    public static native int BASS_ChannelSetDSP(int handle, DSPPROC proc, Object user, int priority);

    public static native boolean BASS_ChannelRemoveDSP(int handle, int dsp);

    public static native int BASS_ChannelSetFX(int handle, int type, int priority);

    public static native boolean BASS_ChannelRemoveFX(int handle, int fx);

    public static native boolean BASS_FXSetParameters(int handle, Object params);

    public static native boolean BASS_FXGetParameters(int handle, Object params);

    public static native boolean BASS_FXReset(int handle);

    public static native boolean BASS_StreamFree(int handle);

    public static native int BASS_StreamCreateFile(String file, long offset, long length, int flags);

    public static native int BASS_PluginLoad(String file, int flags);

    public static native boolean BASS_PluginFree(int handle);
}
