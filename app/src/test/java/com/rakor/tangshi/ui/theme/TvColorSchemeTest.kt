package com.rakor.tangshi.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * T 调色板 → tv ColorScheme（tv-material）映射的回归测试。
 * 与 ThemeColorSchemeTest（守护 m3 对话框方案）互补：本测试守护官方组件主题。
 */
class TvColorSchemeTest {

    @Test
    fun `官方焦点描边取 border 槽位 映射为主橙`() {
        // tv Card 的 focusedBorder 默认读 colorScheme.border（3dp），
        // 这是"官方默认焦点描边"在本应用呈现主橙的唯一落点
        // （Button 默认无描边，其焦点反色走 focusedContainerColor=onSurface，见「反色语义槽位映射暖色」）
        assertEquals(T.Orange, TangshiTvColorScheme.border)
    }

    @Test
    fun `背景与表面保持暖米色`() {
        assertEquals(T.BgMid, TangshiTvColorScheme.background)
        assertEquals(T.BgMid, TangshiTvColorScheme.surface)
        assertNotEquals(TangshiTvColorScheme.surface, TangshiTvColorScheme.surfaceVariant)
    }

    @Test
    fun `反色语义槽位映射暖色`() {
        // Button 默认 focusedContainerColor = onSurface、inverseOnSurface 供反色文字
        assertEquals(T.TextTitle, TangshiTvColorScheme.onSurface)
        assertEquals(T.Card, TangshiTvColorScheme.inverseOnSurface)
        assertEquals(T.Orange, TangshiTvColorScheme.primary)
    }

    @Test
    fun `遮罩与错误色映射固定`() {
        // 遮罩与错误色映射固定（m3 方案对同槽位另有 ThemeColorSchemeTest 独立断言，两者各自钉死 T 值）
        assertEquals(T.Scrim, TangshiTvColorScheme.scrim)
        assertEquals(T.Wrong, TangshiTvColorScheme.error)
    }
}
