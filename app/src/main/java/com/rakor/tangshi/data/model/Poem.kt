package com.rakor.tangshi.data.model

import kotlinx.serialization.Serializable

@Serializable data class Poem(val id: String, val order: Int, val title: String,
                              val poet: String, val dynasty: String,
                              val lines: List<Line>, val background: String)
@Serializable data class Line(val text: String, val chars: List<CharPy>)
@Serializable data class CharPy(val c: String, val py: String)

/** 汉字字符（排除标点/空格）——拼音对齐与出题都用它 */
fun hanChars(text: String): List<Char> =
    text.filter { Character.getType(it) == Character.OTHER_LETTER.toInt() }.toList()
