# FuMiVoice

FuMiVoice 是一个面向 Android 的本地 MIDI 播放器，使用 Jetpack Compose 构建，基于 BASS / BASSMIDI / BASS_FX 音频引擎，支持 SoundFont、音符瀑布、播放列表、音频导出与小窗画中画。

> Android MIDI player built with Jetpack Compose and the BASS audio stack.

当前版本 **1.1.0**（versionCode 13）。

## 功能特性

- **MIDI 播放**：支持 `.mid` / `.midi`，内置 BASS 与 BASSMIDI，支持 SoundFont 音色库
- **音符瀑布**：钢琴卷帘式瀑布流，随播放实时滚动；1.1.0 增加材质化音符（立体高光/投影/落点脉冲/发声键光柱/击键涟漪）、深靛星空舞台与更柔和的键盘高亮；可进入 **画中画小窗** 只保留瀑布播放
- **SoundFont 管理**：full 版内置 GeneralUser SoundFont；lite 版不内置，首次使用在「音色 → 在线下载」取 GeneralUser（29.8MB）。支持导入 / 下载 / 切换 SF2/SF3 音色库；与电脑端 FuFumidi 使用同一套音色命名与 .sf2/.sf3 导入规则，FuFumidi 使用中的音色文件可直接导入
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
  - 导出可取消，取消后清理半截文件
- **外观（1.1.0 升级）**：设置 → 外观
  - 材质：**原生 / 玻璃 / 亚克力 / 云母 / 磨砂 / 天鹅绒**六档，卡片、面板、顶栏与底栏一起换
  - 壁纸：**极光 / 星空 / 深海 / 星云 / 渐变网格 / 暮色**六张，外加「无（纯色）」
  - 全部由 Canvas 代码绘制，不打包任何位图；可调背景压暗，可选低帧率漂移
  - 选择立即生效、自动保存（SharedPreferences），重启后保留；默认玻璃 + 极光
  - 图标：直接使用 FuFumidi 当前立绘（mascot.png），生成传统/圆形/自适应/主题单色图标
- **桌面小组件**：锁屏 / 桌面播放控制与瀑布小部件
- **安全与体积**：云同步 token 使用 Android Keystore AES/GCM 加密、关闭 allowBackup；release 开启 R8，BASS JNI 类全量 keep
- **通知栏**：频道名、音色、播放/暂停等文案中英文资源化
- **现代化 UI**：Jetpack Compose + Material 3，全应用动效与手势切换
- **多语言**：应用内可切换中文 / 英文，全部页面文案成对翻译
- **横屏与沉浸**：横屏时瀑布独占左侧、控制栏收进右侧独立滚动；沉浸模式整屏只留瀑布
- **在线音色库**：可下载 GeneralUser GS / FluidR3 GM / SGM-V2.01 / **Allvoice（517 预设 / 480MB）** 等，
  自动走国内镜像并分段并发下载，分卷资源自动合并
- **设置页**：外观、语言切换、检查更新、开发者信息与下载地址
- **文件关联**：支持从文件管理器直接打开 MIDI 文件

## 下载

- Release APK：**[FuMiVoice-1.1.0-release.apk](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.1.0/FuMiVoice-1.1.0-release.apk)**
- Release AAB（用于应用商店）：**[FuMiVoice-1.1.0-release.aab](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.1.0/FuMiVoice-1.1.0-release.aab)**
- lite APK（无内置 GeneralUser，3.6MB）：**[FuMiVoice-1.1.0-lite.apk](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.1.0/FuMiVoice-1.1.0-lite.apk)**
- lite AAB：**[FuMiVoice-1.1.0-lite.aab](https://github.com/qdTXTbp/FuMiVoice/releases/download/v1.1.0/FuMiVoice-1.1.0-lite.aab)**
- Release 页面：https://github.com/qdTXTbp/FuMiVoice/releases

> full 版 31MB、lite 版 3.6MB；均仅提供 `arm64-v8a`，最低支持 Android 7.0（API 24），targetSdk 36。

> ⚠️ **仍在使用 1.0.5 及更早版本**：1.0.6 起更换了签名密钥，无法覆盖安装，
> 会提示「签名不一致」。请先卸载旧版再安装；卸载前建议用云同步把曲库与歌单备份到云端。
> 1.0.6 及之后的版本可以正常覆盖升级。

## 技术栈

- Kotlin 1.9.24 + Jetpack Compose（BOM 2024.06）+ Material 3
- AndroidX Navigation / Lifecycle / Media
- BASS / BASSMIDI / BASS_FX / BASSFLAC / BASSWV（官方 Java 绑定 + arm64-v8a 动态库）
- LAME（MP3 编码，NDK/CMake）
- Gradle 8.9 + Android Gradle Plugin 8.5（wrapper 已固定 8.9）

## 构建

环境要求：

- Android Studio / Android SDK，需安装 **platforms;android-36** 与对应 build-tools
- JDK 17
- Android NDK + CMake（仅从源码重建 LAME 时需要；本仓库也接受预编译 libmp3lame_jni.so）
- 可选：`keystore.properties` 用于 release 签名（该文件不会提交到仓库）

```bash
git clone https://github.com/qdTXTbp/FuMiVoice.git
cd FuMiVoice
./gradlew :app:assembleDebug
```

Release 包：

```bash
# full：内置 GeneralUser.sf2，约 31MB
./gradlew :app:assembleFullRelease
# lite：无内置音色库，约 3.6MB，独立包名 .lite
./gradlew :app:assembleLiteRelease
```

产物位于 `app/build/outputs/apk/full/release/` 与 `app/build/outputs/apk/lite/release/`。

单测 / CI：

```bash
./gradlew :app:testFullReleaseUnitTest
```

仓库带 `.github/workflows/android-ci.yml`，push/PR 会跑单测并构建 full/lite release。

### 构建注意（实测踩过的两个坑）

1. **Gradle 版本**：wrapper 已固定 `gradle-8.9-bin.zip`（AGP 8.5.0 只支持 Gradle 8.x）。
   若用系统 Gradle 构建，请确保是 8.9，不要用 Gradle 9。
2. **compileSdk 36 与 AGP 8.5**：AGP 8.5 不认识 API 36，靠 `gradle.properties` 里的
   `android.suppressUnsupportedCompileSdk=36` 放行。条件允许时应升级到认识 36 的 AGP。
   另外 `app/build.gradle.kts` 里 `lint.checkReleaseBuilds = false` 是为了绕开本机
   `lintVital` 崩溃（AGP 8.5 + 该环境的 MessageBus 问题），换正常环境后建议改回 `true`。

### 签名（release）

仓库里**不包含**任何证书。发布签名走 `keystore.properties`，模板见
[`keystore.properties.example`](keystore.properties.example)：

```bash
# 1) 生成自己的密钥（只需一次；KEYS 目录不要提交）
keytool -genkeypair -v \
  -keystore keystore/fumivoice-release.jks \
  -alias fumivoice -keyalg RSA -keysize 4096 -validity 10000

# 2) 复制模板并填写四项
cp keystore.properties.example keystore.properties
#    storeFile / storePassword / keyAlias / keyPassword

# 3) 出包
./gradlew :app:assembleFullRelease

# 4) 核对签名方案（应为 V1/V2/V3，以 V2/V3 为准）
apksigner verify --print-certs app/build/outputs/apk/full/release/app-full-release.apk
```

- `storeFile` 相对**仓库根目录**解析（不是 `app/`）；
- `keystore.properties` 与 `*.jks` 都在 `.gitignore` 里，**不要提交**；
- 没有 `keystore.properties` 时 `assembleFullRelease` / `assembleLiteRelease` 仍能构建，只是产出未签名包；
- 1.0.6 起换过密钥：更早的版本无法覆盖安装，升级前需卸载。

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
│  │  ├─ ui/
│  │  │  ├─ theme/       # 配色、字号、间距、动效、**外观（材质/壁纸）**
│  │  │  ├─ wallpaper/   # 程序化壁纸（极光/星空/深海/星云/网格/暮色）
│  │  │  ├─ screens/     # 曲库 / 播放 / 音色 / 云同步 / 设置 / 外观
│  │  │  └─ components/  # 通用卡片、列表行、提示条、转场
│  │  └─ widget/         # 桌面小组件
│  ├─ src/main/cpp/      # LAME / MP3 JNI
│  ├─ src/main/jniLibs/  # BASS arm64-v8a 动态库
│  ├─ src/full/assets/    # full 版内置 GeneralUser.sf2（lite 不包含）
│  └─ src/main/res/      # 图标（含自适应图标）、字体、文案
├─ keystore.properties.example  # 签名配置模板（不含密钥）
└─ build.gradle.kts
```

## 图标

启动器图标由电脑端 `FuFumidi-Source/frontend/src/assets/mascot.png`（当前立绘）生成：

- `mipmap-*` 下的 `ic_launcher.png` / `ic_launcher_round.png`：Android 7.x 用的传统图标；
- `mipmap-anydpi-v26/` 下的自适应图标：背景为深靛径向渐变（`drawable/ic_launcher_background.xml`），
  前景为 `ic_launcher_foreground.png`（108dp 画布，主体控制在 66/108 安全区内）；
- 生成脚本不随仓库分发，图标本身随 APK 打包，改动只需替换上面这些资源。

## 外观系统（1.0.11）

| 层 | 位置 | 说明 |
|---|---|---|
| 偏好模型 | `ui/theme/Appearance.kt` | `SurfaceMaterial` / `WallpaperKind` / `Appearance`，SharedPreferences 落盘 |
| 材质 | `ui/theme/AppSurface.kt` | `Modifier.appSurface()`：底色 + 发丝边 + 高光/颗粒 |
| 壁纸 | `ui/wallpaper/WallpaperArt.kt` | 六张 Canvas 壁纸 + `AppWallpaperBackdrop`（舞台 + 压暗 + 底部渐隐） |
| 入口 | 设置 → 外观 | 与电脑端 `AppearancePanel` 同构：即时预览、自动保存 |

Compose 没有 CSS 的 `backdrop-filter`，所以"玻璃"靠半透明底色 + 斜向高光 + 亮边模拟，
而不是真的模糊背景；这也是低端机上不额外掉帧的前提。

## 第三方组件与许可

- BASS / BASSMIDI / BASS_FX / BASSFLAC / BASSWV：版权归 Un4seen Developments 所有，使用需遵守其许可条款
- LAME：LGPL
- GeneralUser GS SoundFont：遵循其原始许可
- 其余 Android / Kotlin 组件遵循各自开源许可

---

Made with ❤️ for MIDI on Android.
