package com.un4seen.bass;

import java.nio.ByteBuffer;

/**
 * un4seen BASSWV 插件绑定：WavPack 解码。
 *
 * 需要先把 libbasswv.so 打进 jniLibs；类初始化时加载。
 */
public class BASSWV {

    static {
        System.loadLibrary("basswv");
    }

    public static native int BASS_WV_StreamCreateFile(String file, long offset, long length, int flags);

    public static native int BASS_WV_StreamCreateFile(ByteBuffer buffer, long offset, long length, int flags);

    public static native int BASS_WV_StreamCreateFileUser(
            int system, int flags, BASS.BASS_FILEPROCS procs, Object user);

    public static native int BASS_WV_StreamCreateFileUserEx(
            int system, int flags, BASS.BASS_FILEPROCS procs, Object user, Object user2);

    public static native int BASS_WV_StreamCreateURL(
            String url, int offset, int flags, BASS.DOWNLOADPROC proc, Object user);
}
