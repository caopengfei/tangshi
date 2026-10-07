package com.rakor.tangshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakor.tangshi.data.model.Line
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T

/**
 * 单句诗行：逐字拼音（上小拼音下大字）；当前句 ChipBg 高亮。
 * 自 DetailScreen 提出的 internal 共享组件（详情页/背诗小游戏题面复用）；诗字默认楷体（规格第 4 节）。
 * "◀ 正在读"/连读起始光标等标记由调用方悬挂在行外固定槽位，保证诗字始终左对齐。
 */
@Composable
internal fun PoemLine(
    line: Line,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    font: FontFamily = KaiTi
) {
    Row(
        modifier
            .background(if (highlighted) T.ChipBg else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        line.chars.forEach { cd -> PoemCharCell(cd.py, cd.c, font) }
    }
}

/**
 * 单字格：固定宽度、拼音与字在格内居中。宽度取最长拼音音节（shuāng ≈ 37dp @11sp），
 * 各行按同一节距排字——首字拼音宽窄不再影响该行诗字的横向起点。
 */
internal val POEM_CHAR_CELL: Dp = 40.dp

@Composable
internal fun PoemCharCell(py: String, c: String, font: FontFamily = KaiTi) {
    Column(Modifier.width(POEM_CHAR_CELL), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(py, fontSize = 11.sp, color = T.OrangeSoft, maxLines = 1)
        Text(c, fontSize = 24.sp, color = T.TextTitle, fontFamily = font)
    }
}
