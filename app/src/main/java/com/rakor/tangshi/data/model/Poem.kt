package com.rakor.tangshi.data.model

import kotlinx.serialization.Serializable

@Serializable data class Poem(val id: String, val order: Int, val title: String,
                              val poet: String, val dynasty: String,
                              val lines: List<Line>, val background: String,
                              val meaning: String = "")
@Serializable data class Line(val text: String, val chars: List<CharPy>)
@Serializable data class CharPy(val c: String, val py: String)

/** 汉字字符串（排除标点/空格）——拼音对齐与出题都用它
 *
 * 按码位段判定而不是 category：诗中会出现扩展区汉字
 * （《丹青引》𡒄 U+21484、《燕歌行》𪭢 U+2AB62），
 * 若只覆盖基本区会把它们漏掉，导致与数据条的 chars 数不符。
 * 返回 String 是因为扩展区汉字在 UTF-16 中是一对 surrogate，单 Char 装不下。
 *
 * 逐码位手写遍历而不用 `String.codePoints()`：后者是 API 24 起才有的方法，
 * 而 minSdk 23（小米盒子 Android 6.0.1）在这个方法上只能靠 AGP 的合成转换类兜底，
 * 没有实机验证过；`Character.codePointAt/charCount` 是 API 1 就有的等价写法。
 */
private val HAN_RANGES = listOf(
    0x4E00..0x9FFF,    // CJK 统一表意文字（基本区）
    0x3400..0x4DBF,    // 扩展 A
    0x20000..0x2A6DF,  // 扩展 B
    0x2A700..0x2EBEF,  // 扩展 C/D/E/F
    0x2F800..0x2FA1F,  // 兼容表意文字补充
    0xF900..0xFAFF     // 兼容表意文字
)

fun hanChars(text: String): List<String> = buildList {
    var i = 0
    while (i < text.length) {
        val cp = Character.codePointAt(text, i)
        if (HAN_RANGES.any { cp in it }) add(String(Character.toChars(cp)))
        i += Character.charCount(cp)
    }
}
