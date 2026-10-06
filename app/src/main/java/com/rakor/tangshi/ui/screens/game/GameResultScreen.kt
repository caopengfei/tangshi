package com.rakor.tangshi.ui.screens.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakor.tangshi.di.AppGraph
import com.rakor.tangshi.game.QuizGenerator
import com.rakor.tangshi.ui.components.CardShadow
import com.rakor.tangshi.ui.components.CreamBackground
import com.rakor.tangshi.ui.components.FocusablePillButton
import com.rakor.tangshi.ui.components.KeyHintBar
import com.rakor.tangshi.ui.theme.T

/**
 * 背诗小游戏 · 结算页（规格 5.7）。纯展示：correct/streak 从导航参数读入，
 * 星级与鼓励语用 QuizGenerator 静态方法（stars/message）。
 *
 * 两枚按钮：再来一轮（filled，初始焦点）= 重新进入同 mode+scope 的题面路由（由导航层
 * popUpTo 玩法选择页，避免旧题面残留在返回栈）；返回首页 = popBackStack 到 HOME。
 * B 键 = 返回首页（与"返回首页"语义一致，避免退回已结束的题面）。
 */
@Composable
fun GameResultScreen(correct: Int, streak: Int, onAgain: () -> Unit, onHome: () -> Unit) {
    val g = AppGraph.instance

    BackHandler(onBack = onHome)

    // 初始焦点落"再来一轮"；进屏自动落焦的那次不响提示音（Task 6 模式）
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
    }
    val firstFocus = remember { mutableStateOf(true) }
    val tone: (Boolean) -> Unit = { focused ->
        if (focused) {
            if (firstFocus.value) firstFocus.value = false else g.keyTone.focusMove()
        }
    }

    val starCount = QuizGenerator.stars(correct)

    CreamBackground {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 中央：三星 + 答对数 + 鼓励语
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ResultStar(reached = starCount >= 1, size = 34)
                        ResultStar(reached = starCount >= 2, size = 44)
                        ResultStar(reached = starCount >= 3, size = 34)
                    }
                    Text(
                        "答对 $correct / ${GameViewModel.ROUND_LEN} 题！",
                        fontSize = 22.sp, letterSpacing = 4.sp,
                        color = T.TextTitle, fontWeight = FontWeight.SemiBold
                    )
                    Text(QuizGenerator.message(correct, streak), color = T.TextBody, fontSize = 13.sp)
                }
            }

            // 按钮行
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                FocusablePillButton(
                    "再来一轮", Icons.Filled.PlayArrow, filled = true,
                    onClick = onAgain, onFocusChange = tone,
                    modifier = Modifier.focusRequester(focusRequester)
                )
                FocusablePillButton(
                    "返回首页", Icons.Filled.Home, filled = false,
                    onClick = onHome, onFocusChange = tone
                )
            }

            KeyHintBar("◀ ▶ 选择　OK 确认")
        }
    }
}

/** 结算星：达标 Amber+暖影光环（与全应用焦点投影同色），未达标 Idle 无影 */
@Composable
private fun ResultStar(reached: Boolean, size: Int) {
    Box(
        Modifier.then(
            if (reached) Modifier.shadow(10.dp, CircleShape, ambientColor = CardShadow, spotColor = CardShadow)
            else Modifier
        )
    ) {
        Icon(
            Icons.Filled.Star, null,
            Modifier.size(size.dp),
            tint = if (reached) T.Amber else T.Idle
        )
    }
}
