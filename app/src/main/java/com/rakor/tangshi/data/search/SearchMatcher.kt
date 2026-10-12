package com.rakor.tangshi.data.search

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import java.text.Normalizer

/** 组合变音符正则：顶层只编译一次（此前 stripTone 内联 Regex，每次过滤每诗重编译） */
private val COMBINING_MARKS = Regex("\\p{M}+")

/**
 * 搜索匹配纯函数（规格 5.3，2026-10-12 改版）。
 *
 * 匹配范围：汉字关键字命中标题 + 全部诗句 + 诗人名（当前输入链路仅屏幕 A-Z 键，
 * 汉字匹配为数据能力预留——spec 实体键盘前提已勘误）；
 * 拼音命中走 [buildKeys] 预构建合并键——首字母（LB→李白、CQMYG→床前明月光）
 * 与全拼（chuangqian）双通道。拼音数据：诗句来自逐字 CharPy（人工校对，运行时算），
 * 标题/诗人来自 search_index.json（构建期 pypinyin 生成，缺条目降级空串段）。
 */
object SearchMatcher {

    /** 通用入口；VM 热路径走四参重载（keywordKey 预计算） */
    fun matches(poem: Poem, keyword: String, keys: String): Boolean =
        matches(poem, keyword, keys, stripTone(keyword.trim()))

    /**
     * 热路径重载：[keys] 为 [buildKeys] 预构建合并键，[keywordKey] 为
     * stripTone(keyword.trim())——循环不变量，调用方每个按键只算一次；
     * 每首诗只剩汉字文本 / 合并键两次 contains（汉字文本拼接沿旧行为每键重建，
     * 325 首开销与旧版持平）。
     */
    internal fun matches(
        poem: Poem,
        keyword: String,
        keys: String,
        keywordKey: String
    ): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        val text = poem.title + poem.lines.joinToString("") { it.text } + poem.poet
        return text.contains(kw) || keys.contains(keywordKey)
    }

    /**
     * 每诗合并键：五段 `/` 分隔——诗句全拼+首字母 / 标题全拼 / 标题首字母 /
     * 诗人全拼 / 诗人首字母。索引缺键段为空串（降级，诗句键仍可命中）；
     * 查询串只含 A-Z/汉字、不含 `/`，不会跨段拼出假命中。
     */
    fun buildKeys(poem: Poem, index: SearchIndex): String {
        val title = index.titles[poem.id]
        val poet = index.poets[poem.poet]
        return listOf(
            pinyinKey(poem) + lineInitKey(poem),
            title?.full.orEmpty(),
            title?.init.orEmpty(),
            poet?.full.orEmpty(),
            poet?.init.orEmpty()
        ).joinToString("/")
    }

    /** 全诗拼音键：诗句逐字拼音去声调连写（如 chuangqianmingyueguang…），VM 初始化预构建一次 */
    internal fun pinyinKey(poem: Poem): String =
        stripTone(poem.lines.joinToString("") { line -> line.chars.joinToString("") { it.py } })

    /**
     * 诗句首字母键：逐字拼音**去调后**取首字母小写连写（床前明月光 → cqmyg）。
     * 必须先 [stripTone]——带调首字（ā/é 等预组合字符）直接 first() 会把变音符号
     * 带进键（ééé ≠ eee），A-Z 查询跨该字失配；无拉丁首字（数据异常如 py=汉字
     * 𪭢）跳过。与全拼同源 CharPy。
     */
    internal fun lineInitKey(poem: Poem): String =
        poem.lines.joinToString("") { line ->
            line.chars.joinToString("") { c ->
                stripTone(c.py).firstOrNull { it in 'a'..'z' }?.toString() ?: ""
            }
        }

    /** 去声调 + 小写：NFD 分解去组合变音符（ǔ→u）+ v→u（lv→lu，与索引侧口径统一） */
    internal fun stripTone(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
            .lowercase().replace("v", "u")
}
