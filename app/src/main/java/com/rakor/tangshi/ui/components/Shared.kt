package com.rakor.tangshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import com.rakor.tangshi.ui.theme.T

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

// ===== 官方组件样式助手（tv-material + T 调色板；规格 2026-10-11 §组件映射）=====

/** 焦点音效挂载点：官方组件没有 onFocusChange 参数，统一挂 modifier 链首转发（各屏 tone lambda 的归宿） */
fun Modifier.focusTone(tone: (Boolean) -> Unit): Modifier =
    onFocusChanged { tone(it.isFocused) }

/** 白卡静态悬浮影（6dp，非焦点视觉；焦点视觉全走官方 border/scale）。
 *  clip 必须为 false：shadow 默认 clip=true 会在本 modifier 处裁剪链内内容，
 *  把官方组件的焦点 scale（graphicsLayer 缩放）与 3dp focusedBorder 裁在 rest 轮廓外，焦点描边整圈不可见。 */
fun Modifier.cardRestingShadow(corner: Int): Modifier =
    shadow(6.dp, RoundedCornerShape(corner.dp), clip = false)

/** 普通药丸：rest 白底 2dp 浅橙描边棕字；focused 官方反色语义 → 主橙底白字、无描边
 *  （focusedBorder 显式 None：官方默认 focusedBorder=border 会在橙底上叠一圈 2dp 浅橙，旧观感焦点无描边） */
@Composable
fun normalPillColors() = ButtonDefaults.colors(
    containerColor = T.Card,
    contentColor = T.TextBody,
    focusedContainerColor = T.Orange,
    focusedContentColor = Color.White,
    disabledContainerColor = T.Card.copy(alpha = 0.45f),
    disabledContentColor = T.TextBody.copy(alpha = 0.45f),
)

@Composable
fun normalPillBorder() = ButtonDefaults.border(
    border = Border(BorderStroke(2.dp, T.OrangeSoft), shape = CircleShape),
    focusedBorder = Border.None
)

/** 强调药丸（主行动按钮 / 选中常驻强调）：rest ChipBg 底 3dp 橙描边橙字；focused 同普通档反色、无描边 */
@Composable
fun highlightPillColors() = ButtonDefaults.colors(
    containerColor = T.ChipBg,
    contentColor = T.Orange,
    focusedContainerColor = T.Orange,
    focusedContentColor = Color.White,
)

@Composable
fun highlightPillBorder() = ButtonDefaults.border(
    border = Border(BorderStroke(3.dp, T.Orange), shape = CircleShape),
    focusedBorder = Border.None
)

/** 分段组药丸：焦点不反色（规格 §1 分段例外），focused 升 4dp 主橙描边；rest 选中=强调档本体 */
@Composable
fun segmentPillColors(selected: Boolean) = ButtonDefaults.colors(
    containerColor = if (selected) T.ChipBg else T.Card,
    contentColor = if (selected) T.Orange else T.TextBody,
    focusedContainerColor = if (selected) T.ChipBg else T.Card,
    focusedContentColor = if (selected) T.Orange else T.TextBody,
)

@Composable
fun segmentPillBorder(selected: Boolean) = ButtonDefaults.border(
    border = Border(
        BorderStroke(if (selected) 3.dp else 2.dp, if (selected) T.Orange else T.OrangeSoft),
        shape = CircleShape
    ),
    focusedBorder = Border(BorderStroke(4.dp, T.Orange), shape = CircleShape)
)

/** 官方卡片形状 / 配色 / 焦点描边（3dp colorScheme.border=主橙，官方默认），圆角跟随卡片。
 *  cardBorder 取色走 TangshiTheme 的 tv colorScheme.border 槽（TvColorSchemeTest 守护）——
 *  其余助手直读 T；在 TangshiTheme 之外使用 cardBorder 会落到 tv 默认色。 */
@Composable
fun cardShape(corner: Int) = CardDefaults.shape(shape = RoundedCornerShape(corner.dp))

@Composable
fun cardColors() = CardDefaults.colors(containerColor = T.Card, contentColor = T.TextTitle)

@Composable
fun cardBorder(corner: Int) = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
        shape = RoundedCornerShape(corner.dp)
    )
)
