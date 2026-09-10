package com.fumi.voice.export

import android.media.MediaCodecList

/** 查询设备上有哪些编码器可用。 */
internal object MediaCodecListCompat {

    /** 返回所有支持 [mime] 的**编码器**名称，优先顺序与系统一致。 */
    fun encodersFor(mime: String): List<String> {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return list.codecInfos
            .asSequence()
            .filter { it.isEncoder }
            .filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
            .map { it.name }
            .toList()
    }
}
