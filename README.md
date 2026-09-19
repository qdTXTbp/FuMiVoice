# FuMiVoice

FuMiVoice 是一个面向 Android 的本地 MIDI 播放器，使用 Jetpack Compose 构建，基于 BASS / BASSMIDI / BASS_FX 音频引擎，支持 SoundFont、音符瀑布、播放列表、音频导出与小窗画中画。

> Android MIDI player built with Jetpack Compose and the BASS audio stack.

## 功能特性

- **MIDI 播放**：支持 `.mid` / `.midi`，内置 BASS 与 BASSMIDI，支持 SoundFont 音色库
- **音符瀑布**：钢琴卷帘式瀑布流，随播放实时滚动；可进入 **画中画小窗** 只保留瀑布播放
- **SoundFont 管理**：内置 GeneralUser SoundFont，支持导入 / 下载 / 切换 SF2 音色库
- **播放列表**：本地曲库、歌单管理、M3U 导入导出、播放历史
  - 曲库批量管理：长按或「选择」进入多选，支持全选、批量加入歌单、批量移除
  - 歌单批量导入：可从曲库多选添加，也可直接导入多个音频 / MIDI 文件
  - 曲目排序：文件名 / 曲名 / 时长 / 文件大小 / 音符数 / 播放次数，可切升降序并记忆选择
- **混音与音效**：通道混音台（音量 / 声像 / 静音 / 独奏）、均衡器、速度 / 音调、节拍器
- **音频导出**：
  - WAV
  - AAC / M4A
  - FLAC（MediaCodec + 原生 FLAC 容器）
  - MP3（LAME NDK 构建）
- **桌面小组件**：锁屏 / 桌面播放控制与瀑布小部件
- **现代化 UI**：Jetpack Compose + Material 3，全应用动效与手势切换
- **多语言**：应用内可切换中文 / 英文，全部页面文案成对翻译
- **横屏与沉浸**：横屏时瀑布独占左侧、控制栏收进右侧独立滚动；沉浸模式整屏只留瀑布
- **在线音色库**：可下载 GeneralUser GS / FluidR3 GM / SGM-V2.01 等，
  自动走国内镜像并分段并发下载，分卷资源自动合并
- **设置页**：语言切换、检查更新、开发者信息与下载地址
- **文件关联**：支持从文件管理器直接打开 MIDI 文件

## 下载

- Release APK：**[FuMiVoice-1.0.6-release.apk](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.0.6/FuMiVoice-1.0.6-release.apk)**
- Release AAB（用于应用商店）：**[FuMiVoice-1.0.6-release.aab](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.0.6/FuMiVoice-1.0.6-release.aab)**
- Release 页面：https://github.com/qdTXTbp/FuMiVoice/releases

> 当前仅提供 `arm64-v8a` 版本，最低支持 Android 7.0（API 24）。

> ⚠️ **从 1.0.5 及更早版本升级**：1.0.6 更换了签名密钥，无法覆盖安装，
> 会提示「签名不一致」。请先卸载旧版再安装；卸载前建议用云同步把曲库与歌单备份到云端。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- AndroidX Navigation / Lifecycle / Media
- BASS / BASSMIDI / BASS_FX / BASSFLAC / BASSWV（官方 Java 绑定 + arm64-v8a 动态库）
- LAME（MP3 编码，NDK/CMake）
- Gradle 8.x + Android Gradle Plugin 8.5

## 构建

环境要求：

- Android Studio / Android SDK
- JDK 17
- Android NDK + CMake
- 可选：`keystore.properties` 用于 release 签名（该文件不会提交到仓库）

```bash
git clone https://github.com/qdTXTbp/FuMiVoice.git
cd FuMiVoice
./gradlew :app:assembleDebug
```

Release 包：

```bash
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/`。

## 项目结构

```text
FuMiVoice/
├─ app/
│  ├─ src/main/java/com/fumi/voice/
│  │  ├─ audio/          # 均衡器等音频处理
│  │  ├─ export/         # WAV / AAC / FLAC / MP3 导出
│  │  ├─ library/        # 曲库、歌单、M3U、历史
│  │  ├─ midi/           # MIDI 解析
│  │  ├─ player/         # BASS 播放内核与后台服务
│  │  ├─ soundfont/      # SoundFont 管理 / 下载 / GM 映射
│  │  ├─ ui/             # Compose UI、瀑布、主题、动效
│  │  └─ widget/         # 桌面小组件
│  ├─ src/main/cpp/      # LAME / MP3 JNI
│  ├─ src/main/jniLibs/  # BASS arm64-v8a 动态库
│  └─ src/main/assets/   # 内置 SoundFont
├─ dist/                 # 发布产物（APK / AAB）
└─ build.gradle.kts
```

## 第三方组件与许可

- BASS / BASSMIDI / BASS_FX / BASSFLAC / BASSWV：版权归 Un4seen Developments 所有，使用需遵守其许可条款
- LAME：LGPL
- GeneralUser GS SoundFont：遵循其原始许可
- 其余 Android / Kotlin 组件遵循各自开源许可

---

Made with ❤️ for MIDI on Android.
