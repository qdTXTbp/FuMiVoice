package com.fumi.voice.util

import java.util.Locale

/**
 * 当前是否中文界面。
 *
 * 读进程级的 [Locale.getDefault]：切语言时会重建 Activity 并调用 `Locale.setDefault`
 * （见 [AppLanguage]），所以每次重建后这里取到的都是新值。
 * 数据层（GM 音色表、音色库目录）里的字符串不参与 Compose 重组，
 * 必须靠这个判断来给出对应语言的那一份。
 */
val isChineseUi: Boolean
    get() = Locale.getDefault().language.startsWith("zh")

/**
 * 中英并列的数据取当前语言那一侧。
 *
 * 用于 GM 音色名、音色库描述这类「本来就同时存了中文和英文」的数据，
 * 比给每一条都建一个 string 资源要省事得多，也不会让数据表变得不可读。
 */
fun localizedText(chinese: String, english: String): String =
    if (isChineseUi) chinese else english
