package com.rakor.tangshi.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rakor.tangshi.ui.theme.T

/** 暖棕投影色（焦点规范 rgba(210,140,90)，仅用于 shadow 的 ambient/spot 着色，非界面用色）；
 *  internal 供同模块自绘焦点组件（如游戏题面的选项块）复用同一暖影 */
internal val CardShadow = Color(0xD28C5A)

/** 焦点放大倍数（统一焦点视觉，自聚焦组件与外部布局共用） */
val FocusScale = 1.06f

/**
 * TV 焦点封装：onClick 为 null 时仅使其可聚焦；否则用 clickable（自带焦点获取，
 * 聚焦后遥控 OK / 键盘 Enter 触发 onClick）。
 *
 * indication 必须置 null：默认 ripple 的"焦点状态层"会在聚焦节点背后画一块矩形灰底，
 * 与自绘的描边+放大焦点视觉叠在一起非常脏；本应用纯遥控交互，不需要 ripple。
 *
 * 注意不要写成 focusable().clickable()：clickable 内部已委托一个 FocusableNode
 * （自带焦点目标），再叠加 focusable 会产生两个焦点停靠点，遥控导航需要按两次方向键。
 */
@Composable
fun Modifier.tvFocusable(onClick: (() -> Unit)? = null): Modifier {
    val interactions = remember { MutableInteractionSource() }
    return if (onClick == null) this.focusable()
    else this.clickable(interactionSource = interactions, indication = null, onClick = onClick)
}

/** 白色悬浮卡；focused 由外部控制视觉（供 LazyGrid 单焦点控制等复用，放大由外部处理） */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    focused: Boolean,
    corner: Int = 20,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(
                if (focused) 14.dp else 6.dp,
                RoundedCornerShape(corner.dp),
                ambientColor = CardShadow,
                spotColor = CardShadow
            )
            .background(T.Card, RoundedCornerShape(corner.dp))
            .then(
                if (focused) Modifier.border(3.dp, T.OrangeSoft, RoundedCornerShape(corner.dp))
                else Modifier
            ),
        content = content
    )
}

/**
 * 可聚焦卡：自管焦点视觉（放大/描边/阴影动效）。
 * OK 触发 onClick（为 null 时仅可聚焦）；焦点变化回调 onFocusChange（提示音挂钩用）。
 * scaleFactor：聚焦放大倍数，大尺寸卡（如首页主卡）可调小避免挤压邻卡。
 */
@Composable
fun FocusableCard(
    modifier: Modifier = Modifier,
    corner: Int = 20,
    scaleFactor: Float = FocusScale,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner.dp)
    val scale by animateFloatAsState(if (focused) scaleFactor else 1f, label = "cardFocusScale")
    val borderWidth by animateDpAsState(if (focused) 3.dp else 0.dp, label = "cardFocusBorder")
    Column(
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            .shadow(
                if (focused) 14.dp else 6.dp,
                shape,
                ambientColor = CardShadow,
                spotColor = CardShadow
            )
            .background(T.Card, shape)
            .border(borderWidth, T.OrangeSoft, shape)
            .tvFocusable(onClick),
        content = content
    )
}

/**
 * 可聚焦胶囊按钮：内部自管焦点视觉（放大/描边/反色填充动效）。
 * 统一描边款本体（规格 2026-10-08 焦点样式）：
 *  - 未选中：白底 2dp 浅橙描边棕字；highlight 档：ChipBg 底 3dp 橙描边橙字（常驻强调/分段选中指示）
 *  - 选中（B 反色）：橙渐变填充 + 白字白图标 + 1.06 放大 + 暖橙投影——焦点即最强信号
 * OK 触发 onClick（为 null 时仅可聚焦）；焦点变化回调 onFocusChange（提示音挂钩用）。
 */
@Composable
fun FocusablePillButton(
    text: String,
    icon: ImageVector? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "pillFocusScale")
    val borderWidth by animateDpAsState(
        if (focused) 0.dp else if (highlight) 3.dp else 2.dp,
        label = "pillFocusBorder"
    )
    // 底色在静态底与橙渐变间连续过渡：两端同色即纯色，两端异色即渐变（B 反色）
    val restBg = if (highlight) T.ChipBg else T.Card
    val bgStart by animateColorAsState(if (focused) T.OrangeLight else restBg, label = "pillBgStart")
    val bgEnd by animateColorAsState(if (focused) T.Orange else restBg, label = "pillBgEnd")
    val textColor by animateColorAsState(
        if (focused) Color.White else if (highlight) T.Orange else T.TextBody,
        label = "pillTextColor"
    )
    Row(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            // 禁用：半透明灰显，不可聚焦（焦点遍历跳过）、不响应点击
            .then(if (enabled) Modifier else Modifier.alpha(0.45f).focusProperties { canFocus = false })
            .then(
                if (focused) Modifier.shadow(14.dp, shape, ambientColor = CardShadow, spotColor = CardShadow)
                else Modifier
            )
            .background(Brush.horizontalGradient(listOf(bgStart, bgEnd)), shape)
            .border(borderWidth, if (highlight) T.Orange else T.OrangeSoft, shape)
            .then(if (enabled) Modifier.tvFocusable(onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (focused) Color.White else T.Orange)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = textColor)
    }
}

/**
 * 状态徽章：NOT_STARTED / LEARNING+percent / MASTERED，贴底居中（位置由外部布局控制）。
 * 状态字符串与 data 层 ProgressStatus 常量对应，此处不 import data 内部类型。
 * 已会背徽章统一 thumb_up 实心图标（规格 §4），LEARNING/未学习为纯文字。
 */
@Composable
fun StatusBadge(status: String, progress: Int) {
    val (bg, fg, label) = when (status) {
        "MASTERED" -> Triple(T.BadgeBg, T.Orange, "已会背")
        "LEARNING" -> Triple(T.ChipBg, T.OrangeSoft, "学习中 $progress%")
        else -> Triple(T.Idle, T.TextMuted, "未学习")
    }
    val container = Modifier
        .background(bg, RoundedCornerShape(10.dp))
        .padding(horizontal = 10.dp, vertical = 2.dp)
    if (status == "MASTERED") {
        Row(container, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ThumbUp, null, Modifier.size(12.dp), tint = fg)
            Spacer(Modifier.width(3.dp))
            Text(label, color = fg)
        }
    } else {
        Text(label, container, color = fg)
    }
}

/** 底部按键提示条 */
@Composable
fun KeyHintBar(left: String, right: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(left, color = T.TextSubtle)
        if (right != null) Text(right, color = T.TextSubtle)
    }
}

/** 全屏奶油渐变背景 */
@Composable
fun CreamBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(T.BgTop, T.BgMid, T.BgBottom))),
        content = content
    )
}
