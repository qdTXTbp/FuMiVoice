package com.un4seen.bass;

/**
 * un4seen BASSMIDI 的官方 Android Java 绑定。
 *
 * 与 BASS.java 同理：内部类字段名必须与原版一致，native 端靠反射读写。
 * 事件常量在原版里没有 BASS_ 前缀（就是 MIDI_EVENT_*），保持一致以免调用点混淆。
 */
public class BASSMIDI {

    static {
        System.loadLibrary("bassmidi");
    }

    // ---------------------------------------------------------------
    // StreamCreate / FontInit 标志
    // ---------------------------------------------------------------
    public static final int BASS_MIDI_DECAYEND = 0x1000;
    public static final int BASS_MIDI_NOFX = 0x2000;
    public static final int BASS_MIDI_DECAYSEEK = 0x4000;
    public static final int BASS_MIDI_NOCROP = 0x8000;
    public static final int BASS_MIDI_NOTEOFF1 = 0x10000;
    public static final int BASS_MIDI_SINCINTER = 0x800000;
    public static final int BASS_MIDI_NODRUMPARAMUSER = 0x200;
    public static final int BASS_MIDI_NODRUMPARAM = 0x400;
    public static final int BASS_MIDI_NOSYSRESET = 0x800;

    public static final int BASS_MIDI_FONT_MMAP = 0x20000;
    public static final int BASS_MIDI_FONT_XGDRUMS = 0x40000;
    public static final int BASS_MIDI_FONT_NOFX = 0x80000;
    public static final int BASS_MIDI_FONT_LINATTMOD = 0x100000;
    public static final int BASS_MIDI_FONT_LINDECVOL = 0x200000;
    public static final int BASS_MIDI_FONT_NORAMPIN = 0x400000;
    public static final int BASS_MIDI_FONT_NOLIMITS = 0x800000;
    public static final int BASS_MIDI_FONT_MINFX = 0x1000000;
    public static final int BASS_MIDI_FONT_SBLIMITS = 0x2000000;

    public static final int BASS_MIDI_FONTLOAD_COMPACT = 0x2;
    public static final int BASS_MIDI_FONTLOAD_NOLOAD = 0x4;
    public static final int BASS_MIDI_FONTLOAD_KEEPDEC = 0x10;

    // ---------------------------------------------------------------
    // 事件类型（控制单个通道）
    // ---------------------------------------------------------------
    public static final int MIDI_EVENT_END = 0x0;
    public static final int MIDI_EVENT_NOTE = 0x1;
    public static final int MIDI_EVENT_PROGRAM = 0x2;
    public static final int MIDI_EVENT_CHANPRES = 0x3;
    public static final int MIDI_EVENT_PITCH = 0x4;
    public static final int MIDI_EVENT_PITCHRANGE = 0x5;
    public static final int MIDI_EVENT_DRUMS = 0x6;
    public static final int MIDI_EVENT_FINETUNE = 0x7;
    public static final int MIDI_EVENT_COARSETUNE = 0x8;
    public static final int MIDI_EVENT_MASTERVOL = 0x9;
    public static final int MIDI_EVENT_BANK = 0xa;
    public static final int MIDI_EVENT_MODULATION = 0xb;
    public static final int MIDI_EVENT_VOLUME = 0xc;
    public static final int MIDI_EVENT_PAN = 0xd;
    public static final int MIDI_EVENT_EXPRESSION = 0xe;
    public static final int MIDI_EVENT_SUSTAIN = 0xf;
    public static final int MIDI_EVENT_SOUNDOFF = 0x10;
    public static final int MIDI_EVENT_RESET = 0x11;
    public static final int MIDI_EVENT_NOTESOFF = 0x12;
    public static final int MIDI_EVENT_PORTAMENTO = 0x13;
    public static final int MIDI_EVENT_PORTATIME = 0x14;
    public static final int MIDI_EVENT_PORTANOTE = 0x15;
    public static final int MIDI_EVENT_MODE = 0x16;
    public static final int MIDI_EVENT_REVERB = 0x17;
    public static final int MIDI_EVENT_CHORUS = 0x18;
    public static final int MIDI_EVENT_CUTOFF = 0x19;
    public static final int MIDI_EVENT_RESONANCE = 0x1a;
    public static final int MIDI_EVENT_RELEASE = 0x1b;
    public static final int MIDI_EVENT_ATTACK = 0x1c;
    public static final int MIDI_EVENT_DECAY = 0x1d;
    public static final int MIDI_EVENT_REVERB_MACRO = 0x1e;
    public static final int MIDI_EVENT_CHORUS_MACRO = 0x1f;
    public static final int MIDI_EVENT_REVERB_TIME = 0x20;
    public static final int MIDI_EVENT_REVERB_DELAY = 0x21;
    public static final int MIDI_EVENT_REVERB_LOCUTOFF = 0x22;
    public static final int MIDI_EVENT_REVERB_HICUTOFF = 0x23;
    public static final int MIDI_EVENT_REVERB_LEVEL = 0x24;
    public static final int MIDI_EVENT_CHORUS_DELAY = 0x25;
    public static final int MIDI_EVENT_CHORUS_DEPTH = 0x26;
    public static final int MIDI_EVENT_CHORUS_RATE = 0x27;
    public static final int MIDI_EVENT_CHORUS_FEEDBACK = 0x28;
    public static final int MIDI_EVENT_CHORUS_LEVEL = 0x29;
    public static final int MIDI_EVENT_CHORUS_REVERB = 0x2a;
    public static final int MIDI_EVENT_USERFX = 0x2b;
    public static final int MIDI_EVENT_USERFX_LEVEL = 0x2c;
    public static final int MIDI_EVENT_USERFX_REVERB = 0x2d;
    public static final int MIDI_EVENT_USERFX_CHORUS = 0x2e;
    public static final int MIDI_EVENT_SCALETUNING = 0x3f;
    public static final int MIDI_EVENT_CONTROL = 0x40;
    public static final int MIDI_EVENT_CHANPRES_VIBRATO = 0x41;
    public static final int MIDI_EVENT_MODRANGE = 0x45;

    /** 用于 BASS_MIDI_StreamEvent 的特殊通道号。 */
    public static final int BASS_MIDI_CHAN_CHORUS = -1;
    public static final int BASS_MIDI_CHAN_REVERB = -2;
    public static final int BASS_MIDI_CHAN_USERFX = -3;

    /** 查询类事件：不是发事件，而是取回统计值。 */
    public static final int MIDI_EVENT_MIXLEVEL = 0x10000;
    public static final int MIDI_EVENT_TRANSPOSE = 0x10001;
    public static final int MIDI_EVENT_SYSTEMEX = 0x10002;
    public static final int MIDI_EVENT_END_TRACK = 0x10003;
    public static final int MIDI_EVENT_SPEED = 0x10004;
    public static final int MIDI_EVENT_DEFDRUMS = 0x10006;
    public static final int MIDI_EVENT_NOTES = 0x20000;
    public static final int MIDI_EVENT_VOICES = 0x20001;

    // 标记类型
    public static final int BASS_MIDI_MARK_MARKER = 0x0;
    public static final int BASS_MIDI_MARK_CUE = 0x1;
    public static final int BASS_MIDI_MARK_LYRIC = 0x2;
    public static final int BASS_MIDI_MARK_TEXT = 0x3;
    public static final int BASS_MIDI_MARK_TIMESIG = 0x4;
    public static final int BASS_MIDI_MARK_KEYSIG = 0x5;
    public static final int BASS_MIDI_MARK_COPY = 0x6;
    public static final int BASS_MIDI_MARK_TRACK = 0x7;
    public static final int BASS_MIDI_MARK_INST = 0x8;
    public static final int BASS_MIDI_MARK_TRACKSTART = 0x9;
    public static final int BASS_MIDI_MARK_TICK = 0x10000;

    // ---------------------------------------------------------------
    // 内部类
    // ---------------------------------------------------------------

    public static class BASS_MIDI_FONT {
        public int font;
        public int preset;
        public int bank;
    }

    public static class BASS_MIDI_FONTEX {
        public int font;
        public int spreset;
        public int sbank;
        public int dpreset;
        public int dbank;
        public int dbanklsb;
    }

    public static class BASS_MIDI_FONTEX2 extends BASS_MIDI_FONTEX {
        public int numchan;
        public int minchan;
    }

    public static class BASS_MIDI_FONTINFO {
        public String name;
        public String copyright;
        public String comment;
        public int presets;
        public int samsize;
        public int samload;
        public int samtype;
    }

    public static class BASS_MIDI_MARK {
        public int track;
        public int pos;
        public String text;
    }

    public static class BASS_MIDI_MARKB {
        public int track;
        public int pos;
        public byte[] text;
    }

    public static class BASS_MIDI_EVENT {
        public int event;
        public int param;
        public int chan;
        public int tick;
        public int pos;
    }

    public interface MIDIFILTERPROC {
        boolean MIDIFILTERPROC(int track, int event, int param, int pos, int chan, Object user);
    }

    // ---------------------------------------------------------------
    // native 方法
    // ---------------------------------------------------------------

    public static native int BASS_MIDI_GetVersion();

    public static native int BASS_MIDI_StreamCreateFile(String file, long offset, long length, int flags, int freq);

    public static native boolean BASS_MIDI_StreamEvent(int handle, int chan, int event, int param);

    public static native int BASS_MIDI_StreamGetEvent(int handle, int chan, int event);

    public static native int BASS_MIDI_StreamGetChannel(int handle, int chan);

    public static native boolean BASS_MIDI_StreamSetFonts(int handle, BASS_MIDI_FONT[] fonts, int count);

    public static native int BASS_MIDI_StreamGetFonts(int handle, BASS_MIDI_FONT[] fonts, int count);

    public static native boolean BASS_MIDI_StreamGetPreset(int handle, int chan, BASS_MIDI_FONT font);

    public static native boolean BASS_MIDI_StreamLoadSamples(int handle);

    public static native boolean BASS_MIDI_StreamGetMark(int handle, int type, int index, BASS_MIDI_MARK mark);

    public static native int BASS_MIDI_StreamGetMarks(int handle, int track, int type, BASS_MIDI_MARK[] marks);

    public static native int BASS_MIDI_FontInit(String file, int flags);

    public static native boolean BASS_MIDI_FontFree(int handle);

    public static native boolean BASS_MIDI_FontLoad(int handle, int preset, int bank);

    public static native boolean BASS_MIDI_FontUnload(int handle, int preset, int bank);

    public static native boolean BASS_MIDI_FontGetInfo(int handle, BASS_MIDI_FONTINFO info);

    public static native boolean BASS_MIDI_FontGetPresets(int handle, int[] presets);

    public static native String BASS_MIDI_FontGetPreset(int handle, int preset, int bank);

    public static native float BASS_MIDI_FontGetVolume(int handle);

    public static native boolean BASS_MIDI_FontSetVolume(int handle, float volume);
}
