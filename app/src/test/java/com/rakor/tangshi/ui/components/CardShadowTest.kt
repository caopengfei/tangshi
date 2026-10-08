package com.rakor.tangshi.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CardShadowTest {
    @Test
    fun `CardShadow must stay fully opaque`() {
        // 历史回归：6 位 hex 被 Color(Int) 解析为 alpha=0，全 app 暖影透明（2026-10-06 引入、10-08 修复）
        assertEquals(1f, CardShadow.alpha, 0f)
    }
}
