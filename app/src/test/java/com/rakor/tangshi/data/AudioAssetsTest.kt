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
 * 用 Assume：assets 里还没有 audio 目录（Task 12b 才生成种子集）时跳过本测试；
 * 生成后无需改测试，自动全量校验。assumption 失败在测试报告中记为 skip 而非 fail。
 */
@RunWith(RobolectricTestRunner::class)
class AudioAssetsTest {
    @Test fun `每首诗每句音频齐全且合法`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // assets 无法直接 File 探测——用 assets.list("audio") 判断 audio 目录是否存在
        val hasAudio = runCatching { ctx.assets.list("audio")!!.isNotEmpty() }.getOrDefault(false)
        assumeTrue("audio 资产尚未生成（Task 12b）——跳过", hasAudio)
        val poems = PoemRepository(ctx).loadAll()
        assertTrue("poems.json 为空", poems.isNotEmpty())
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
