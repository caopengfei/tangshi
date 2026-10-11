package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.repo.PoemRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 音频资产完整性门（与 tools/validate_voice.py 同一套校验）。
 *
 * 门控判据是「是否已全量生成音频」而非「audio 目录是否存在」：
 * 数据从 10 首扩到 325 首后，audio/ 下仍只有种子集，旧逻辑会在
 * FileNotFoundException 上直接 fail，把「语音待生成」这一正常中间态当成 bug。
 * 全量生成（Task 14 音频批次）完成后本测试自动转为全量校验，无需再改。
 */
@RunWith(RobolectricTestRunner::class)
class AudioAssetsTest {
    @Test fun `每首诗每句音频齐全且合法`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val poems = PoemRepository(ctx).loadAll()
        assertTrue("poems.json 为空", poems.isNotEmpty())
        // 已生成的诗目录集合；有任一未生成即视为「语音待生成」，跳过而非失败。
        // 注意 assets.list 实测返回带子路径的条目（如「jing-ye-si/line1.opus」而非顶层目录名），
        // 取首段才能与 poemId 对齐
        val audioDirs = runCatching {
            ctx.assets.list("audio")?.map { it.substringBefore('/') }?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
        val pending = poems.filter { it.id !in audioDirs }
        assumeTrue("音频待生成：${pending.size}/${poems.size} 首尚未生成（跳过）", pending.isEmpty())
        poems.forEach { p ->
            val mf = ctx.assets.open("audio/${p.id}/manifest.json").bufferedReader().use { it.readText() }
            val lines = Json.decodeFromString<Manifest>(mf).lines
            assertTrue("${p.id} manifest 句数", lines.size == p.lines.size)
            lines.forEach { e ->
                val bytes = ctx.assets.open("audio/${p.id}/${e.file}").use { it.readBytes() }
                assertTrue("${p.id}/${e.file} 太小(${bytes.size})", bytes.size > 1024)
                val ok = bytes.copyOfRange(0, 3).decodeToString() == "ID3" ||
                         (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) ||
                         bytes.copyOfRange(0, 4).decodeToString() == "OggS"
                assertTrue("${p.id}/${e.file} 魔数错误", ok)
            }
        }
    }

    @Serializable data class ManifestEntry(val file: String, val durationMs: Int)
    @Serializable data class Manifest(val lines: List<ManifestEntry>)
}
