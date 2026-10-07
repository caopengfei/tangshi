package com.rakor.tangshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakor.tangshi.data.model.Line
import com.rakor.tangshi.ui.theme.KaiTi
import com.rakor.tangshi.ui.theme.T

/**
 * 单句诗行：逐字拼音（上小拼音下大字）；当前句 ChipBg 高亮 + "◀ 正在读" 指示；
 * cursor=true 时行首绘 4dp 橙色圆点，作为连读模式下"开始位置"光标（调用方保证与 highlighted 互斥）。
 * 自 DetailScreen 提出的 internal 共享组件（详情页/背诗小游戏题面复用）；诗字默认楷体（规格第 4 节）。
 */
@Composable
internal fun PoemLine(
    line: Line,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    font: FontFamily = KaiTi,
    cursor: Boolean = false
) {
    Row(
        modifier
            .background(if (highlighted) T.ChipBg else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (highlighted) Text("◀ 正在读", color = T.Orange, fontSize = 12.sp)
        if (cursor) {
            Box(
                Modifier
                    .padding(start = 2.dp, end = 6.dp, bottom = 11.dp)
                    .size(4.dp)
                    .background(T.Orange, CircleShape)
            )
        }
        line.chars.forEach { cd ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(3.dp)) {
                Text(cd.py, fontSize = 11.sp, color = T.OrangeSoft)
                Text(cd.c, fontSize = 24.sp, color = T.TextTitle, fontFamily = font)
            }
        }
    }
}
