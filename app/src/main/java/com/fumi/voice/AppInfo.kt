package com.fumi.voice

/**
 * 应用的身份与对外地址。
 *
 * 集中放在一处有两个好处：
 * 1. 设置页里的「开发者 / 项目主页 / 下载地址」和更新器指向的是同一个仓库，
 *    分散写死容易出现「更新从 A 拿、下载按钮却指向 B」这种对不上的情况；
 * 2. 地址只有这里一份，将来迁移仓库只需要改这一个文件。
 */
object AppInfo {

    const val DISPLAY_NAME = "FuMiVoice"

    /** 开发者（GitHub 账号）。 */
    const val DEVELOPER = "qdTXTbp"

    const val DEVELOPER_URL = "https://github.com/qdTXTbp"

    const val REPO_URL = "https://github.com/qdTXTbp/FuMiVoice"

    /** 永远指向最新一次发布，不需要随版本号改。 */
    const val RELEASES_URL = "https://github.com/qdTXTbp/FuMiVoice/releases/latest"

    /** 与 [com.fumi.voice.update.AppUpdater] 约定的固定资产名。 */
    const val LATEST_APK_URL =
        "https://github.com/qdTXTbp/FuMiVoice/releases/latest/download/FuMiVoice-latest.apk"
}
