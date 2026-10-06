package com.rakor.tangshi.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rakor.tangshi.ui.theme.T

/** 暖棕投影色（焦点规范 rgba(210,140,90)，仅用于 shadow 的 ambient/spot 着色，非界面用色） */
private val CardShadow = Color(0xD28C5A)

/** 焦点放大倍数（统一焦点视觉，自聚焦组件与外部布局共用） */
val FocusScale = 1.06f

/**
 * TV 焦点封装：onClick 为 null 时仅使其可聚焦；否则用 clickable（自带焦点获取，
 * 聚焦后遥控 OK / 键盘 Enter 触发 onClick）。
 *
 * 注意不要写成 focusable().clickable()：clickable 内部已委托一个 FocusableNode
 * （自带焦点目标），再叠加 focusable 会产生两个焦点停靠点，遥控导航需要按两次方向键。
 */
fun Modifier.tvFocusable(onClick: (() -> Unit)? = null): Modifier =
    if (onClick == null) this.focusable()
    else this.clickable(onClick = onClick)

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
 */
@Composable
fun FocusableCard(
    modifier: Modifier = Modifier,
    corner: Int = 20,
    onFocusChange: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner.dp)
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "cardFocusScale")
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

/** 渐变胶囊按钮；focused 时描边+放大（focused 由外部控制） */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector? = null,
    filled: Boolean = true,
    focused: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .scale(if (focused) FocusScale else 1f)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(T.OrangeLight, T.Orange)), shape)
                else Modifier.background(T.Card, shape)
            )
            .border(
                if (focused) 3.dp else if (filled) 0.dp else 2.dp,
                if (filled && focused) Color.White else T.OrangeSoft,
                shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Color.White else T.Orange)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (filled) Color.White else T.TextBody)
    }
}

/**
 * 可聚焦胶囊按钮：内部自管焦点视觉（放大/描边动效），其余同 [PillButton]。
 * OK 触发 onClick（为 null 时仅可聚焦）；焦点变化回调 onFocusChange（提示音挂钩用）。
 */
@Composable
fun FocusablePillButton(
    text: String,
    icon: ImageVector? = null,
    filled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, label = "pillFocusScale")
    val borderWidth by animateDpAsState(
        if (focused) 3.dp else if (filled) 0.dp else 2.dp,
        label = "pillFocusBorder"
    )
    Row(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .scale(scale)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(T.OrangeLight, T.Orange)), shape)
                else Modifier.background(T.Card, shape)
            )
            .border(borderWidth, if (filled && focused) Color.White else T.OrangeSoft, shape)
            .tvFocusable(onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Color.White else T.Orange)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (filled) Color.White else T.TextBody)
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
