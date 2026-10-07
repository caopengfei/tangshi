package com.rakor.tangshi.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.rakor.tangshi.R

/** 诗体内容字体（规格第 4 节「楷体系」）：霞鹜文楷 Regular（OFL 1.1 开源，见 app/THIRD-PARTY-NOTICES.md）。
 *  全量内嵌于 res/font/wenkai.ttf，不依赖机型字库，各设备渲染一致。仅用于诗句/诗名，界面常规文案仍用系统无衬线。 */
val KaiTi = FontFamily(Font(R.font.wenkai))
