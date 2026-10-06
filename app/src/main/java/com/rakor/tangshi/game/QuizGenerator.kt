package com.rakor.tangshi.game

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.hanChars
import kotlin.random.Random

object QuizGenerator {
    private val FALLBACK_CHARS = listOf('春','风','雨','山','月','白','日','人','来','不','去','中','大','天','地','花','鸟')

    data class FillBlank(val poem: Poem, val lineIndex: Int, val lineText: String,
                         val charIndex: Int, val answer: Char, val options: List<Char>)
    data class Listen(val poem: Poem, val lineIndex: Int, val options: List<Poem>)

    fun fillBlank(pool: List<Poem>, rng: Random): FillBlank {
        val poem = pool.random(rng)
        val li = poem.lines.indices.random(rng)
        val line = poem.lines[li]
        val candidates = line.chars.mapIndexedNotNull { i, c -> if (hanChars(c.c).isNotEmpty()) i else null }
        val ci = candidates.filter { it > 0 }.ifEmpty { candidates }.random(rng)   // 避开首字
        val answer = line.chars[ci].c.first()
        val distractors = (poem.lines.flatMap { it.chars.map { c -> c.c.first() } }.filter { it != answer }
                + FALLBACK_CHARS).filter { it != answer }.distinct().shuffled(rng).take(3)  // 二次过滤: 答案在FALLBACK池中时防重复(评审修复)
        return FillBlank(poem, li, line.text, ci, answer, (distractors + answer).shuffled(rng))
    }
    fun listen(pool: List<Poem>, rng: Random): Listen {
        val poem = pool.random(rng)
        val li = poem.lines.indices.random(rng)
        val others = pool.filter { it.id != poem.id }.shuffled(rng).take(3)
        return Listen(poem, li, (others + poem).shuffled(rng))   // 池不足 4 首时选项数 = 池大小
    }
    fun stars(correct: Int) = when { correct >= 9 -> 3; correct >= 7 -> 2; correct >= 5 -> 1; else -> 0 }
    fun message(correct: Int, streak: Int) = when {
        correct == 10 -> "诗仙驾到！"
        correct >= 7 -> "太棒了！已连续学习 $streak 天啦"
        correct >= 5 -> "继续加油！"
        else -> "再试一次会更棒"
    }
}
