package com.rakor.tangshi.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 配色方案全槽位映射的回归测试。
 *
 * 历史 bug（2026-10-09 修复）：`TangshiColorScheme` 只覆盖 primary/background/surface/onPrimary
 * 四个槽位，其余落 `darkColorScheme` 默认值——其中 `surfaceContainerHigh` 是 M3 深灰 #2B2930。
 * 设置页「清除学习进度」/详情页「我会背了」对话框用的是 M3 AlertDialog，容器底色正是读这个槽位，
 * 于是弹出深灰框，与米色暖阳风格不符。
 *
 * 这里锁死修复所需的全部不变量，新增 M3 弹层/按钮/Surface 前先看本测试。
 */
class ThemeColorSchemeTest {

    @Test
    fun `AlertDialog 容器底色走白卡而非 M3 深灰默认值`() {
        // M3 1.3.1：AlertDialogDefaults.containerColor == colorScheme.surfaceContainerHigh
        assertEquals(T.Card, TangshiColorScheme.surfaceContainerHigh)
    }

    @Test
    fun `对话框标题与正文默认色取自 onSurface 与 onSurfaceVariant`() {
        // AlertDialogDefaults.titleContentColor == onSurface，textContentColor == onSurfaceVariant
        assertEquals(T.TextTitle, TangshiColorScheme.onSurface)
        assertEquals(T.TextBody, TangshiColorScheme.onSurfaceVariant)
    }

    @Test
    fun `对话框操作按钮文字保持主橙`() {
        // M3 TextButton 及对话框 ActionLabelText 均取 primary
        assertEquals(T.Orange, TangshiColorScheme.primary)
    }

    @Test
    fun `对话框遮罩必须用 T_Scrim 而非纯黑`() {
        // 搜索浮层与提示遮罩都用 45% 黑的 T.Scrim，M3 默认 scrim 是纯黑，观感不一致
        assertEquals(T.Scrim, TangshiColorScheme.scrim)
    }

    @Test
    fun `surface 不能等于 surfaceContainerHigh`() {
        // 关键约束：M3 applyTonalElevation 只在容器色 == surface 时才按 surfaceTint 调色，
        // 且 AlertDialog 自带 6dp 色调海拔。两者相等会把白底对话框掺成橙灰，
        // 因此 surface 留米色 BgMid，容器层单独指到白卡。
        assertEquals(T.BgMid, TangshiColorScheme.surface)
        assertNotEquals(TangshiColorScheme.surface, TangshiColorScheme.surfaceContainerHigh)
    }
}
