package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.CharPy
import com.rakor.tangshi.data.model.Line
import com.rakor.tangshi.data.model.Poem

/** 测试夹具：与 assets/content/poems.json 同构的最小诗库（静夜思/咏鹅，拼音取自真实数据） */
object TestFixtures {

    private fun line(text: String, vararg py: String): Line =
        Line(text, text.filter { Character.getType(it) == Character.OTHER_LETTER.toInt() }
            .mapIndexed { i, c -> CharPy(c.toString(), py[i]) })

    fun poems(): List<Poem> = listOf(
        Poem(
            id = "jing-ye-si", order = 1, title = "静夜思", poet = "李白", dynasty = "唐",
            background = "",
            lines = listOf(
                line("床前明月光，", "chuáng", "qián", "míng", "yuè", "guāng"),
                line("疑是地上霜。", "yí", "shì", "dì", "shàng", "shuāng"),
                line("举头望明月，", "jǔ", "tóu", "wàng", "míng", "yuè"),
                line("低头思故乡。", "dī", "tóu", "sī", "gù", "xiāng")
            )
        ),
        Poem(
            id = "yong-e", order = 2, title = "咏鹅", poet = "骆宾王", dynasty = "唐",
            background = "",
            lines = listOf(
                line("鹅，鹅，鹅，", "é", "é", "é"),
                line("曲项向天歌。", "qū", "xiàng", "xiàng", "tiān", "gē"),
                line("白毛浮绿水，", "bái", "máo", "fú", "lǜ", "shuǐ"),
                line("红掌拨清波。", "hóng", "zhǎng", "bō", "qīng", "bō")
            )
        )
    )
}
