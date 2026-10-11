package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.model.hanChars
import com.rakor.tangshi.data.repo.PoemRepository
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class PoemRepositoryTest {
    private val repo = PoemRepository(ApplicationProvider.getApplicationContext())

    @Test fun `加载全部并按 order 排序`() {
        val all = repo.loadAll()
        // Task 14 加固：从 10 首种子扩到 325 首后，size 下限应跟随数据规模
        assertTrue("poems.json 规模异常: ${all.size}", all.size >= 300)
        assertEquals(all.sortedBy { it.order }.map { it.id }, all.map { it.id })
    }
    @Test fun `order 与 id 唯一且格式合法`() {
        val all = repo.loadAll()
        val orders = all.map { it.order }
        assertEquals("order 重复", orders.size, orders.toSet().size)
        val ids = all.map { it.id }
        assertEquals("id 重复", ids.size, ids.toSet().size)
        ids.forEach { assertTrue("id 非 kebab-case: $it", it.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*"))) }
    }
    @Test fun `每首都有背景与释义`() {
        repo.loadAll().forEach { p ->
            assertTrue("《${p.title}》缺 background", p.background.isNotBlank())
            assertTrue("《${p.title}》缺 meaning", p.meaning.isNotBlank())
        }
    }
    @Test fun `按 id 查找`() {
        assertEquals("静夜思", repo.byId("jing-ye-si")!!.title)
    }
    @Test fun `拼音与汉字一一对应`() {
        repo.loadAll().forEach { p ->
            p.lines.forEach { line ->
                assertEquals("《${p.title}》『${line.text}』拼音数不符",
                    hanChars(line.text).size, line.chars.size)
                assertEquals("《${p.title}》『${line.text}』字符错位",
                    hanChars(line.text), line.chars.map { it.c })
                line.chars.forEach { cd ->
                    assertEquals(1, cd.c.codePointCount(0, cd.c.length))
                    assertTrue(cd.py.isNotBlank())
                }
            }
        }
    }
    @Test fun `hanChars 排除标点`() {
        assertEquals(listOf("床","前"), hanChars("床前，。"))
    }
}
