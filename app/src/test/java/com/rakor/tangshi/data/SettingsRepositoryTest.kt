package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DataStore 在 Robolectric 下正常工作（testOptions isIncludeAndroidResources，文件落 Robolectric tmp）。
 * preferencesDataStore 委托按类加载器单例：本类两个用例共享同一 store 文件，"写入后读取"
 * 结尾把各项恢复为默认值，保证用例任意顺序执行都成立（等效每测重置）。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    @Test fun `默认值`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        val s = repo.settings.first()
        assertTrue(s.keyTone)
    }

    @Test fun `写入后读取`() = runTest {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        repo.setKeyTone(false)
        val s = repo.settings.first()
        assertFalse(s.keyTone)
        // 恢复默认：跨用例共享同一 DataStore 文件，避免污染"默认值"用例
        repo.setKeyTone(true)
    }
}
