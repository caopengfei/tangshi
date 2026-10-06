package com.rakor.tangshi.game

import com.rakor.tangshi.data.TestFixtures.poems
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QuizGeneratorTest {
    @Test fun `填空题结构`() {
        val q = QuizGenerator.fillBlank(poems(), Random(42))
        assertEquals(4, q.options.size)
        assertEquals(1, q.options.count { it == q.answer })
        assertTrue(q.lineIndex in q.poem.lines.indices)
        assertTrue(q.charIndex in q.lineText.indices)
    }
    @Test fun `听音题选项含答案`() {
        val q = QuizGenerator.listen(poems(), Random(7))
        // 夹具仅 2 首诗：凑不齐 4 个互异选项，选项数 = min(4, 池大小)；真实题池 ≥5 首恒为 4
        assertEquals(minOf(4, poems().size), q.options.size)
        assertTrue(q.poem.id in q.options.map { it.id })
    }
    @Test fun `星级规则`() {
        assertEquals(3, QuizGenerator.stars(9)); assertEquals(3, QuizGenerator.stars(10))
        assertEquals(2, QuizGenerator.stars(7)); assertEquals(1, QuizGenerator.stars(5))
        assertEquals(0, QuizGenerator.stars(4))
    }
    @Test fun `结算文案`() {
        assertEquals("诗仙驾到！", QuizGenerator.message(10, streak = 5))
        assertEquals("太棒了！已连续学习 5 天啦", QuizGenerator.message(8, streak = 5))
        assertEquals("继续加油！", QuizGenerator.message(6, streak = 5))
        assertEquals("再试一次会更棒", QuizGenerator.message(3, streak = 5))
    }
    @Test fun `填空题答案恰出现一次（多种子回归，防FALLBACK池污染）`() {
        repeat(100) { seed ->
            val q = QuizGenerator.fillBlank(poems(), Random(seed))
            assertEquals(4, q.options.size)
            assertEquals(1, q.options.count { it == q.answer })
        }
    }
}
