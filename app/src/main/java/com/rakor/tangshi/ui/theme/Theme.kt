package com.rakor.tangshi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 风格D 童趣暖阳 —— 色板唯一来源，全部界面从这里取色
object T {
    val BgTop = Color(0xFFFFF6E8); val BgMid = Color(0xFFFDEFDD); val BgBottom = Color(0xFFFDE8D8)
    val Card = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF7A50); val OrangeLight = Color(0xFFFF9A76); val OrangeSoft = Color(0xFFFF8A65)
    val ChipBg = Color(0xFFFFF2EA); val BadgeBg = Color(0xFFFFE9DE)
    val Scrim = Color(0x73000000)   // 黑 45% 全屏遮罩（浮层/对话框背景，搜索浮层与 Task 11 设置共用）
    val TextTitle = Color(0xFF6B4F3A); val TextBody = Color(0xFF7A6450); val TextSubtle = Color(0xFFC0987E)
    val TextMuted = Color(0xFFC0A090); val Idle = Color(0xFFF5EFE6)
    val Amber = Color(0xFFFFB347)
    val ProgressBarBg = Color(0xFFF7ECDD)
}

private val scheme = darkColorScheme(  // TV 深色环境；实际颜色全走 T 对象
    primary = T.Orange, background = T.BgMid, surface = T.Card, onPrimary = Color.White
)

@Composable
fun TangshiTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 4.sp, color = T.TextTitle)
    ), content = content)
