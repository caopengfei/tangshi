package com.rakor.tangshi.data.model

import kotlinx.serialization.Serializable

/** 搜索索引条目：全拼（去调小写）+ 首字母连写；缺字段降级为空串 */
@Serializable
data class PyKey(val full: String = "", val init: String = "")

/** search_index.json 模型：诗人名→键、诗 id→标题键；文件缺失/解析失败时双空表 */
@Serializable
data class SearchIndex(
    val poets: Map<String, PyKey> = emptyMap(),
    val titles: Map<String, PyKey> = emptyMap()
)
