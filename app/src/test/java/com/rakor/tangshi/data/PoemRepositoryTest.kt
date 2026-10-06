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
        assertTrue(all.size >= 2)
        assertEquals(all.sortedBy { it.order }.map { it.id }, all.map { it.id })
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
                    hanChars(line.text), line.chars.map { it.c.first() })
                line.chars.forEach { cd ->
                    assertEquals(1, cd.c.length)
                    assertTrue(cd.py.isNotBlank())
                }
            }
        }
    }
    @Test fun `hanChars 排除标点`() {
        assertEquals(listOf('床','前'), hanChars("床前，。"))
    }
}
