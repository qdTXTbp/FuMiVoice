package com.un4seen.bass;

import java.nio.ByteBuffer;

/**
 * un4seen BASSFLAC 插件绑定：FLAC 解码。
 *
 * 需要先把 libbassflac.so 打进 jniLibs；类初始化时加载。
 */
public class BASSFLAC {

    static {
        System.loadLibrary("bassflac");
    }

    public static native int BASS_FLAC_StreamCreateFile(String file, long offset, long length, int flags);

    public static native int BASS_FLAC_StreamCreateFile(ByteBuffer buffer, long offset, long length, int flags);

    public static native int BASS_FLAC_StreamCreateFileUser(
            int system, int flags, BASS.BASS_FILEPROCS procs, Object user);

    public static native int BASS_FLAC_StreamCreateURL(
            String url, int offset, int flags, BASS.DOWNLOADPROC proc, Object user);
}
