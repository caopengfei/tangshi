package com.rakor.tangshi.data.search

import com.rakor.tangshi.data.model.Poem
import java.text.Normalizer

/**
 * 搜索匹配纯函数（规格 5.3）。
 *
 * 匹配范围（控制器裁定）：拼音数据只存在于诗句 chars（数据模型 title 无逐字拼音，
 * 运行时也无拼音库依赖），因此——
 * - 汉字命中：标题 + 全部诗句文本 contains 关键字（标题用汉字搜）；
 * - 全拼命中：诗句逐字拼音去声调连串 contains 关键字去声调形式（lǜ → lu，
 *   键盘 A-Z 无 ü，NFD 分解去变音符后自然对齐）。
 */
object SearchMatcher {

    /** 测试锚定的三参入口；VM 持拼音缓存过滤时走四参重载避免重复拼接 */
    fun matches(poem: Poem, poetFilter: String?, keyword: String): Boolean =
        matches(poem, poetFilter, keyword, pinyinKey(poem))

    /** [pinyin] 为调用方预构建的全拼键（见 [pinyinKey]），语义与三参版完全一致 */
    internal fun matches(poem: Poem, poetFilter: String?, keyword: String, pinyin: String): Boolean {
        if (!poetFilter.isNullOrBlank() && poem.poet != poetFilter) return false
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        val text = poem.title + poem.lines.joinToString("") { it.text }
        return text.contains(kw) || pinyin.contains(stripTone(kw))
    }

    /** 全诗拼音键：诗句逐字拼音去声调连写（如 chuangqianmingyueguang…），VM 初始化预构建一次 */
    internal fun pinyinKey(poem: Poem): String =
        stripTone(poem.lines.joinToString("") { line -> line.chars.joinToString("") { it.py } })

    /** 去声调 + 小写：NFD 分解去组合变音符（ǔ→u），供匹配与缓存共用 */
    internal fun stripTone(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
