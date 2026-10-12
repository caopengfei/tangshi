package com.rakor.tangshi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Typography as TvTypography
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

// 风格D 童趣暖阳 —— 色板唯一来源，全部界面从这里取色
object T {
    val BgTop = Color(0xFFFFF6E8); val BgMid = Color(0xFFFDEFDD); val BgBottom = Color(0xFFFDE8D8)
    val Card = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF7A50); val OrangeLight = Color(0xFFFF9A76); val OrangeSoft = Color(0xFFFF8A65)
    val ChipBg = Color(0xFFFFF2EA); val BadgeBg = Color(0xFFFFE9DE)
    val Scrim = Color(0x73000000)   // 黑 45% 全屏遮罩（设置对话框遮罩用）
    val TextTitle = Color(0xFF6B4F3A); val TextBody = Color(0xFF7A6450); val TextSubtle = Color(0xFFC0987E)
    val TextMuted = Color(0xFFC0A090); val Idle = Color(0xFFF5EFE6)
    val Amber = Color(0xFFFFB347)
    val Correct = Color(0xFF66BB6A)   // 答题反馈绿（背诗小游戏答对高亮）
    val Wrong = Color(0xFFE57373)     // 答题反馈红（背诗小游戏答错描边）
    val ProgressBarBg = Color(0xFFF7ECDD)
}

/**
 * 应用 Material3 配色：**全槽位显式映射到 T 色板**，不给 M3 默认值留漏口。
 *
 * 为什么必须全量：M3 组件（AlertDialog / TextButton …）的底色与默认文字色不是自绘的，
 * 而是读 `colorScheme` 的槽位——AlertDialog 容器读 `surfaceContainerHigh`、标题读 `onSurface`、
 * 正文读 `onSurfaceVariant`、按钮文字读 `primary`。早先只覆盖 primary/background/surface/onPrimary
 * 四个槽，其余落到 `darkColorScheme` 默认值（surfaceContainerHigh = M3 深灰 #2B2930），
 * 于是设置页「清除学习进度」、详情页「我会背了」两个对话框弹出深灰底，与米色暖阳风格不符。
 *
 * 两条硬约束（改前先看，见 ThemeColorSchemeTest 回归测试）：
 *  1. `surface` 留米色（BgMid）而 **不要** 赋成 Card：`applyTonalElevation` 仅在容器色 == `surface`
 *     时才按 surfaceTint 调色，且 AlertDialog 自带 6dp 色调海拔。若两者相等，白底会被掺成橙灰。
 *  2. `scrim` 必须显式给：默认是纯黑，与浮层自绘的 45% T.Scrim 观感不一致。
 */
internal val TangshiColorScheme = darkColorScheme(
    // 品牌主色（M3 主按钮/选中文字）
    primary = T.Orange,
    onPrimary = Color.White,
    primaryContainer = T.BadgeBg,
    onPrimaryContainer = T.TextTitle,
    inversePrimary = T.OrangeLight,
    // 次级色（AlertDialog 图标默认色走 secondary）
    secondary = T.OrangeSoft,
    onSecondary = Color.White,
    secondaryContainer = T.ChipBg,
    onSecondaryContainer = T.TextBody,
    // 第三色（当前无组件使用，补全防漏）
    tertiary = T.Amber,
    onTertiary = Color.White,
    tertiaryContainer = T.BadgeBg,
    onTertiaryContainer = T.TextTitle,
    // 全局背景
    background = T.BgMid,
    onBackground = T.TextBody,
    // 普通表面：注意与 surfaceContainerHigh 保持不同色（见上方约束 1）
    surface = T.BgMid,
    onSurface = T.TextTitle,          // AlertDialog 标题默认色
    surfaceVariant = T.Idle,
    onSurfaceVariant = T.TextBody,    // AlertDialog 正文默认色
    surfaceTint = T.Orange,
    inverseSurface = T.TextTitle,
    inverseOnSurface = T.Card,
    // 错误态（T.Wrong 已有，补全防漏）
    error = T.Wrong,
    onError = Color.White,
    errorContainer = T.BadgeBg,
    onErrorContainer = T.TextTitle,
    // 描边与遮罩
    outline = T.TextSubtle,
    outlineVariant = T.Idle,
    scrim = T.Scrim,
    // 表面容器层：AlertDialog / M3 Surface 的默认底色全部指到白卡或米色系
    surfaceBright = T.Card,
    surfaceDim = T.BgBottom,
    surfaceContainer = T.Card,
    surfaceContainerHigh = T.Card,    // AlertDialog 容器底色（修复深灰问题的落点）
    surfaceContainerHighest = T.ChipBg,
    surfaceContainerLow = T.Card,
    surfaceContainerLowest = T.Card,
)

/**
 * tv-material 配色：T 调色板 → tv ColorScheme 全槽位映射（规格 2026-10-11 §阶段1）。
 *
 * 两条关键映射：
 *  1. border = T.Orange —— 官方 Card 的 focusedBorder 默认从 colorScheme.border 取色（Button 默认无描边，其焦点反色走 focusedContainerColor=onSurface，见下），
 *     这是"官方默认焦点描边"在本应用呈现主橙的落点（TvColorSchemeTest 守护）。
 *  2. surface = BgMid（米色）而非 Card —— 与内层 m3 方案同值，保证页面底与对话框底跨层一致。
 *
 * 注意：colorScheme.border 在 tv 1.1.0 并非焦点专用——AssistChipDefaults 用它画静态描边；本设计不引入 Chip，若日后引入需注意静态橙边。
 *
 * tv ColorScheme 没有 surfaceContainer* 层级（对话框不走它），
 * m3 的 [TangshiColorScheme] 继续保留，仅供 AlertDialog/TextButton 使用。
 */
internal val TangshiTvColorScheme = tvDarkColorScheme(
    primary = T.Orange,
    onPrimary = Color.White,
    primaryContainer = T.BadgeBg,
    onPrimaryContainer = T.TextTitle,
    inversePrimary = T.OrangeLight,
    secondary = T.OrangeSoft,
    onSecondary = Color.White,
    secondaryContainer = T.ChipBg,
    onSecondaryContainer = T.TextBody,
    tertiary = T.Amber,
    onTertiary = Color.White,
    tertiaryContainer = T.BadgeBg,
    onTertiaryContainer = T.TextTitle,
    background = T.BgMid,
    onBackground = T.TextBody,
    surface = T.BgMid,
    onSurface = T.TextTitle,
    surfaceVariant = T.Idle,
    onSurfaceVariant = T.TextBody,
    surfaceTint = T.Orange,
    inverseSurface = T.TextTitle,
    inverseOnSurface = T.Card,
    error = T.Wrong,
    onError = Color.White,
    errorContainer = T.BadgeBg,
    onErrorContainer = T.TextTitle,
    border = T.Orange,
    borderVariant = T.TextSubtle,
    scrim = T.Scrim,
)

// 两层主题（外层 tv、内层 m3 对话框）共用的 titleLarge——单一来源，避免两处构造漂移
private val TangshiTitleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 4.sp, color = T.TextTitle)

@Composable
fun TangshiTheme(content: @Composable () -> Unit) =
    TvMaterialTheme(
        colorScheme = TangshiTvColorScheme,
        typography = TvTypography(titleLarge = TangshiTitleLarge)
    ) {
        // 对话框仍走 material3（tv-material 无 Dialog 组件）；同一 T 色板全槽位映射，
        // 约束见上方 TangshiColorScheme 注释与 ThemeColorSchemeTest
        MaterialTheme(
            colorScheme = TangshiColorScheme,
            typography = Typography(titleLarge = TangshiTitleLarge),
            content = content
        )
    }
