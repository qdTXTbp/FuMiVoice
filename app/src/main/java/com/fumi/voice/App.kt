package com.fumi.voice

import android.app.Application
import android.util.Log
import com.fumi.voice.player.MidiPlayerManager
import com.fumi.voice.soundfont.SoundFontDownloader
import com.fumi.voice.soundfont.SoundFontManager

class App : Application() {
    lateinit var player: MidiPlayerManager
        private set
    lateinit var soundFonts: SoundFontManager
        private set
    lateinit var soundFontDownloader: SoundFontDownloader
        private set

    override fun onCreate() {
        super.onCreate()
        player = MidiPlayerManager(this)
        soundFonts = SoundFontManager(this)
        soundFontDownloader = SoundFontDownloader(this, soundFonts)
        player.init()

        // 音色库约 30MB，放到后台线程解压并加载，避免阻塞启动
        Thread {
            try {
                val bundled = soundFonts.ensureBundledExtracted()
                    ?.let { soundFonts.find(SoundFontManager.BUNDLED_NAME) }
                val selected = soundFonts.selectedFileName?.let { soundFonts.find(it) }

                // 优先用上次选中的音色库，加载失败则回退到内置音色
                if (selected != null && player.loadSoundFont(selected.path)) {
                    soundFonts.select(selected)
                } else if (bundled != null && player.loadSoundFont(bundled.path)) {
                    soundFonts.select(bundled)
                } else {
                    Log.e("App", "没有可用的音色库")
                }
            } catch (e: Exception) {
                Log.e("App", "加载音色库失败", e)
            }
        }.start()
    }
}
