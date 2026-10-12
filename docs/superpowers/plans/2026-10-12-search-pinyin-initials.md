# 搜索页改版实施计划（去诗人选择 + 拼音首字母搜索 + 键帽文字居中）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搜索页删除诗人筛选、关键字框同时搜诗人/诗名/诗句（首字母 + 全拼双通道）、键帽文字居中。

**Architecture:** 构建期 `tools/build_search_index.py` 生成诗人/标题拼音索引资产；运行时 `SearchMatcher` 把每诗五段键（诗句全拼+首字母/标题全拼/标题首字母/诗人全拼/诗人首字母）拼成 `/` 分隔合并键，每按键一次 contains 过滤；`SearchScreen` 删浮层简化焦点状态机。

**Tech Stack:** Kotlin / Compose for TV（tv-material 1.1.0）、kotlinx.serialization、pypinyin 0.55（仅构建期）、uiautomator 数值断言。

**规格：** `docs/superpowers/specs/2026-10-12-search-pinyin-initials-design.md`（用户已批准）

**环境备注：**
- adb 不在 PATH，用全路径 `D:/develop/androidsdk/platform-tools/adb.exe`；模拟器 `emulator-5554`（szj_tv）。
- gradle 用 `cd D:/develop/src/rakor/tangshi && ./gradlew ...`（Git Bash）。
- 实机验证脚本 `tools/verify_search.py` 已随本计划提交（导航/入口/居中/计数断言已在旧构建上验证过失败行为符合预期）。

**执行勘误（Task 1 实施中裁定）：**
1. **多音字 override 非空**：pypinyin 实测 16 处误读（重送/重别/重帏/几重 zhong→chong、长干行/长相思 zhang→chang、鹿柴 chai→zhai、泊×3 po→bo、天姥 lao→mu、给事 gei→ji、岑参 cencan→censhen），已按 Step 4 规则写入 override——计划"预期空表"作废，Step 4 清单中的"预跑已核对 ✓"标注不可当 ground truth。
2. **ü 形式统一为 u**：pypinyin NORMAL 输出 v 形（lv/nv），运行时 CharPy 是 ü 形（strip 后 lu/nu）——工具 `strip_tone` 与运行时 `stripTone` 均加 `.replace("v","u")`（pinyin 中 v 只作 ü 替身），用户敲 v 也能命中。
3. **Task 4 质量追加**：`AppGraph.warmUp()` 补 `appScope.launch { poems.loadSearchIndex() }` 预热（导航当帧不再撞 37KB 解析；`buildKeys` 纯 CPU 仍于 VM 初始化主线程一次性执行，记为取舍、Task 6 实测卡顿再议）；Theme.kt Scrim 注释去「搜索浮层」引用。Step 4 注释措辞同步（后注册→BackHandler 先于系统拦截）。
4. **Task 6 验收回归**：①空结果 B 焦点回退失效（BACK 事件期间单次 requestFocus 不生效）——`exitKeyboard()` 改 `scope.launch` + `requestFocusWhenReady` 帧重试；②`verify_search.py` 打字断言 `word.index(ch)` 遇重复字母算错期望——改枚举位 `word[:i+1]`，None 计数显示「空结果」；③Step 3 用词 XYZ→ABC（XYZ 是 15 首合法命中）；④观察项（非回归、计划外）：详情页返回后搜索关键字被清空——nav 重建 composable 丢失 remember 状态，改版前后同行为，规格未要求保留，留待用户裁定。

---

## 文件结构

| 动作 | 文件 | 职责 |
|---|---|---|
| 新建 | `tools/search_index_overrides.json` | 多音字人工裁定表 |
| 新建 | `tools/build_search_index.py` | 生成索引资产（pypinyin + 校验） |
| 新建 | `app/src/main/assets/content/search_index.json` | 诗人/标题全拼+首字母（生成物） |
| 新建 | `app/src/main/java/com/rakor/tangshi/data/model/SearchIndex.kt` | 索引数据类 |
| 修改 | `app/src/main/java/com/rakor/tangshi/data/repo/PoemRepository.kt` | 加载索引（缺文件降级） |
| 新建 | `app/src/test/java/com/rakor/tangshi/data/SearchIndexTest.kt` | 索引解析测试 |
| 修改 | `app/src/test/java/com/rakor/tangshi/data/TestFixtures.kt` | 夹具索引条目 |
| 修改 | `app/src/test/java/com/rakor/tangshi/data/SearchMatcherTest.kt` | 匹配器测试重写 |
| 新建 | `app/src/test/java/com/rakor/tangshi/data/SearchIntegrationTest.kt` | 真实资产回归（规格示例直测） |
| 修改 | `app/src/main/java/com/rakor/tangshi/data/search/SearchMatcher.kt` | 匹配语义改造 |
| 修改 | `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt` | 删诗人选择/焦点/文案/居中修复 |

---

### Task 1: 索引生成工具与资产

**Files:**
- Create: `tools/search_index_overrides.json`
- Create: `tools/build_search_index.py`
- Create: `app/src/main/assets/content/search_index.json`（工具生成）

- [ ] **Step 1: 写多音字 override 表（初始为空）**

`tools/search_index_overrides.json`：

```json
{
  "poets": {},
  "titles": {}
}
```

格式说明：key 为诗人名（poets）或诗 id（titles），value 为**音节列表**（pypinyin NORMAL 口径，无声调）。例：`"titles": {"some-id": ["qiang", "jin", "jiu"]}`。

- [ ] **Step 2: 写生成工具**

> ⚠️ 下方代码块为初始规格；**以 `tools/build_search_index.py` 现文件为准**（后续质量修复——v→u 归一、override 形状校验、`_readme`、死代码清理——已回写工具但未逐 hunk 回写本块，机械照抄会复活旧缺陷）。

`tools/build_search_index.py`：

```python
#!/usr/bin/env python3
"""生成 app/src/main/assets/content/search_index.json：诗人/标题的全拼+首字母。

TV 搜索页（2026-10-12 规格）按首字母搜索：LB→李白、CQMYG→床前明月光。
运行时无拼音库，标题/诗人的拼音在此构建期生成；诗句键由运行时从 CharPy 计算
（人工校对的权威数据，不进本文件）。

多音字：pypinyin 词组级上下文 + tools/search_index_overrides.json 人工裁定。
校验：重复诗 id、过期 override、空键——任一非零退出，防 poems.json 更新后
索引未重跑或残留旧条目。
"""
import json
import pathlib
import sys
import unicodedata

from pypinyin import Style, lazy_pinyin

ROOT = pathlib.Path(__file__).resolve().parents[1]
POEMS = ROOT / "app/src/main/assets/content/poems.json"
OUT = ROOT / "app/src/main/assets/content/search_index.json"
OVERRIDES = ROOT / "tools/search_index_overrides.json"


def strip_tone(s: str) -> str:
    """与运行时 SearchMatcher.stripTone 同口径：NFD 去变音符 + 小写 + v→u（lü→lu）。"""
    decomposed = unicodedata.normalize("NFD", s)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).lower().replace("v", "u")


def make_key(text: str, override) -> dict:
    syllables = override or lazy_pinyin(text, style=Style.NORMAL)
    full = strip_tone("".join(syllables))
    # 首字母只取 ASCII 音节首字符——标题里的全角括号/间隔号（古朗月行（节选）、
    # 无题·重帏）不得混进首字母串
    init = "".join(s[0] for s in syllables
                   if s and s[0].isascii() and s[0].isalpha()).lower()
    if not full or not init:
        raise SystemExit(f"空键: {text!r} -> {syllables}")
    return {"full": full, "init": init}


def main() -> None:
    poems = json.loads(POEMS.read_text(encoding="utf-8"))
    ov = json.loads(OVERRIDES.read_text(encoding="utf-8")) if OVERRIDES.exists() else {}
    ov_titles, ov_poets = ov.get("titles", {}), ov.get("poets", {})

    seen = set()
    poets, titles = {}, {}
    for p in poems:
        pid = p["id"]
        if pid in seen:
            raise SystemExit(f"重复诗 id: {pid}")
        seen.add(pid)
        name = p["poet"]
        if name not in poets:
            poets[name] = make_key(name, ov_poets.get(name))
        titles[pid] = make_key(p["title"], ov_titles.get(pid))

    stale = (set(ov_titles) - seen) | (set(ov_poets) - {p["poet"] for p in poems})
    if stale:
        raise SystemExit(f"过期 override 条目: {sorted(stale)}")
    if len(titles) != len(poems) or len(poets) != len({p["poet"] for p in poems}):
        raise SystemExit("条目数与 poems.json 不一致")

    OUT.write_text(
        json.dumps({"poets": poets, "titles": titles},
                   ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"poets={len(poets)} titles={len(titles)} -> {OUT.relative_to(ROOT)}")
    print("—— 全量输出供审（对照诗句实际读音核验多音字）——")
    for name in sorted(poets):
        print(f"  诗人 {name}: {poets[name]['full']} / {poets[name]['init']}")
    for pid in sorted(titles):
        print(f"  标题 {pid}: {titles[pid]['full']} / {titles[pid]['init']}")


if __name__ == "__main__":
    main()
```

- [ ] **Step 3: 运行并核对数量**

Run: `cd D:/develop/src/rakor/tangshi && python tools/build_search_index.py`

Expected 首行：`poets=76 titles=325 -> .../search_index.json`（路径分隔符随平台，Windows 下为反斜杠），后接 401 行全量打印。

独立对账（预期输出 `325 76`）：

```bash
python -c "import json; d=json.load(open('app/src/main/assets/content/poems.json',encoding='utf-8')); print(len(d), len({p['poet'] for p in d}))"
```

- [ ] **Step 4: 审全量打印（多音字人工核验）**

逐行扫打印输出，重点对照：

- **行**：`行宫 → xinggong/xg`、`行路难 → xinglunan`（应 xíng 非 háng）——计划预跑已核对 ✓
- **长**：`长信怨 → zhangxinyuan/zxy`（应 zhǎng ✓）、`长恨歌 → changhenge`（应 cháng ✓）
- **乐**：`登乐游原 → dengleyouyuan/dlyy`（lè ✓）
- **重/间/燕/发**：`重送 → chong…`、`间道 → jian…`、`燕集 → yan…`、`早发 → zaoFa…` 均应正确
- **复姓**：`皇甫冉 → huangfuran/hfr`、`綦毋潜 → qiwuqian/qwq`、`岑参 → cencan/cc`、`元结 → yuanjie/yj`——计划预跑已核对 ✓
- **首字母串**：不得含中文标点（`（`、`·` 已被 Step 2 过滤逻辑排除）

发现误读 → 按 Step 1 格式写入 `tools/search_index_overrides.json` → 重跑 Step 3。**预期 override 保持空表**（预跑未发现误读）。

- [ ] **Step 5: 校验自测（过期 override 必须报错）**

临时把 `tools/search_index_overrides.json` 的 titles 改为 `{"__ghost-id__": ["x"]}` → 运行工具 → Expected：`过期 override 条目: ['__ghost-id__']`，退出码非 0 → 还原为空表 `{}` 再跑一次恢复生成物。

```bash
python tools/build_search_index.py; echo "exit=$?"
```

- [ ] **Step 6: 提交**

```bash
git add tools/build_search_index.py tools/search_index_overrides.json app/src/main/assets/content/search_index.json
git commit -m "feat: 搜索拼音索引生成工具——诗人/标题全拼+首字母资产（pypinyin+过期条目校验）"
```

---

### Task 2: SearchIndex 模型与仓储加载（TDD）

**Files:**
- Create: `app/src/test/java/com/rakor/tangshi/data/SearchIndexTest.kt`
- Create: `app/src/main/java/com/rakor/tangshi/data/model/SearchIndex.kt`
- Modify: `app/src/main/java/com/rakor/tangshi/data/repo/PoemRepository.kt`

- [ ] **Step 1: 写失败测试**

`app/src/test/java/com/rakor/tangshi/data/SearchIndexTest.kt`：

```kotlin
package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.SearchIndex
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * search_index.json 解析：条目读取 + 缺字段默认（本类覆盖范围）。
 * 注意：真正的降级路径（文件缺失/坏文件/空表 → PoemRepository 捕获 + Log.w）不在本类
 * 覆盖——由 Task 3 集成测试读真实资产间接兜底，勿误以为已被此处测试保护。
 */
class SearchIndexTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `解析诗人与标题条目`() {
        val idx = json.decodeFromString<SearchIndex>(
            """{"poets":{"李白":{"full":"libai","init":"lb"}},
               "titles":{"jing-ye-si":{"full":"jingyesi","init":"jys"}}}"""
        )
        assertEquals("lb", idx.poets["李白"]?.init)
        assertEquals("jingyesi", idx.titles["jing-ye-si"]?.full)
    }

    @Test fun `空对象解析为双空表`() {
        val idx = json.decodeFromString<SearchIndex>("{}")
        assertTrue(idx.poets.isEmpty())
        assertTrue(idx.titles.isEmpty())
    }

    @Test fun `条目缺字段降级空串`() {
        val idx = json.decodeFromString<SearchIndex>("""{"poets":{"李白":{"full":"libai"}}}""")
        assertEquals("", idx.poets["李白"]?.init)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd D:/develop/src/rakor/tangshi && ./gradlew :app:testDebugUnitTest --tests "*SearchIndexTest" -q`

Expected: 编译失败——`Unresolved reference: SearchIndex`。

- [ ] **Step 3: 实现模型与加载**

新建 `app/src/main/java/com/rakor/tangshi/data/model/SearchIndex.kt`：

```kotlin
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
```

`PoemRepository.kt`——加 import 与成员（`json` 实例复用现有私有属性）：

```kotlin
import android.util.Log
import com.rakor.tangshi.data.model.SearchIndex
```

在 `loadAll()` 之前追加：

```kotlin
    /**
     * 搜索拼音索引；缺文件/坏文件/空表降级为双空表并留 logcat 痕迹
     * （诗句键运行时可算，搜索不崩；catch Exception 不吞 Error——OOM 等仍大声失败）。
     * 空表守卫：生成器保证双表恒非空，解码成功却为空 = schema 漂移，同样要留痕。
     */
    private val searchIndex: SearchIndex by lazy {
        try {
            val idx = context.assets.open("content/search_index.json").bufferedReader().use { r ->
                json.decodeFromString<SearchIndex>(r.readText())
            }
            require(idx.poets.isNotEmpty() && idx.titles.isNotEmpty()) {
                "search_index.json 解析为空（schema 漂移？）"
            }
            idx
        } catch (e: Exception) {
            Log.w("PoemRepository", "search_index.json 加载失败，降级空表", e)
            SearchIndex()
        }
    }
    fun loadSearchIndex(): SearchIndex = searchIndex
```

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :app:testDebugUnitTest --tests "*SearchIndexTest" -q`

Expected: `BUILD SUCCESSFUL`（3 tests PASS）。

- [ ] **Step 5: 全量回归 + 提交**

Run: `./gradlew testDebugUnitTest -q` → Expected: `BUILD SUCCESSFUL`（既有测试不受影响）。

```bash
git add app/src/main/java/com/rakor/tangshi/data/model/SearchIndex.kt \
        app/src/main/java/com/rakor/tangshi/data/repo/PoemRepository.kt \
        app/src/test/java/com/rakor/tangshi/data/SearchIndexTest.kt
git commit -m "feat: SearchIndex 模型与资产加载（缺文件/坏文件降级空表）"
```

---

### Task 3: SearchMatcher 首字母+诗人匹配（TDD）

**Files:**
- Modify: `app/src/test/java/com/rakor/tangshi/data/TestFixtures.kt`
- Modify: `app/src/test/java/com/rakor/tangshi/data/SearchMatcherTest.kt`
- Create: `app/src/test/java/com/rakor/tangshi/data/SearchIntegrationTest.kt`
- Modify: `app/src/main/java/com/rakor/tangshi/data/search/SearchMatcher.kt`

- [ ] **Step 1: 夹具加索引条目**

`TestFixtures.kt`——加 import：

```kotlin
import com.rakor.tangshi.data.model.PyKey
import com.rakor.tangshi.data.model.SearchIndex
```

在 `poems()` 函数之后追加：

```kotlin
    /** 索引夹具：与 search_index.json 同构（键值取真实生成结果口径） */
    fun searchIndex(): SearchIndex = SearchIndex(
        poets = mapOf(
            "李白" to PyKey("libai", "lb"),
            "骆宾王" to PyKey("luobinwang", "lbw")
        ),
        titles = mapOf(
            "jing-ye-si" to PyKey("jingyesi", "jys"),
            "yong-e" to PyKey("yonge", "ye")
        )
    )
```

- [ ] **Step 2: 重写匹配测试（新签名 + 首字母用例）**

`SearchMatcherTest.kt` 整体替换为：

```kotlin
package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import com.rakor.tangshi.data.search.SearchMatcher
import org.junit.Assert.*
import org.junit.Test

/**
 * 搜索匹配纯函数（2026-10-12 改版）：
 * - 汉字命中 = 标题/诗句/诗人名 contains；
 * - 拼音命中 = buildKeys 合并键 contains（诗句全拼+首字母 / 标题全拼 / 标题首字母 /
 *   诗人全拼 / 诗人首字母），首字母（LB/CQMYG）与全拼（chuangqian）双通道；
 * - 删诗人过滤；关键字为空 = 全量命中；索引缺键 = 降级（诗句键仍工作）。
 */
class SearchMatcherTest {
    private val poems = TestFixtures.poems()
    private val index = TestFixtures.searchIndex()
    private fun keys(p: Poem) = SearchMatcher.buildKeys(p, index)

    @Test fun `关键字命中汉字`() = assertTrue(SearchMatcher.matches(poems[0], "月光", keys(poems[0])))
    @Test fun `关键字命中诗人汉字`() = assertTrue(SearchMatcher.matches(poems[0], "李白", keys(poems[0])))
    @Test fun `标题汉字命中`() = assertTrue(SearchMatcher.matches(poems[0], "静夜思", keys(poems[0])))
    @Test fun `关键字命中全拼`() = assertTrue(SearchMatcher.matches(poems[0], "chuangqian", keys(poems[0])))
    @Test fun `关键字命中诗句首字母`() = assertTrue(SearchMatcher.matches(poems[0], "cqmyg", keys(poems[0])))
    @Test fun `关键字命中标题全拼`() = assertTrue(SearchMatcher.matches(poems[0], "jingyesi", keys(poems[0])))
    @Test fun `关键字命中标题首字母`() = assertTrue(SearchMatcher.matches(poems[0], "jys", keys(poems[0])))
    @Test fun `关键字命中诗人全拼`() = assertTrue(SearchMatcher.matches(poems[0], "libai", keys(poems[0])))
    @Test fun `关键字命中诗人首字母`() = assertTrue(SearchMatcher.matches(poems[0], "lb", keys(poems[0])))
    @Test fun `索引缺键降级诗句键仍工作`() {
        val noIndex = SearchMatcher.buildKeys(poems[0], SearchIndex())
        assertTrue(SearchMatcher.matches(poems[0], "cqmyg", noIndex))
        assertTrue(SearchMatcher.matches(poems[0], "chuangqian", noIndex))
    }
    @Test fun `不命中`() = assertFalse(SearchMatcher.matches(poems[0], "xyz", keys(poems[0])))
    @Test fun `空关键字命中全部`() = assertTrue(poems.all { SearchMatcher.matches(it, "", keys(it)) })
    @Test fun `V 形输入与 U 形等价`() = assertTrue(SearchMatcher.matches(poems[1], "lvshui", keys(poems[1])))
    @Test fun `带调首字归一`() = assertTrue(SearchMatcher.matches(poems[1], "eee", keys(poems[1])))

    /** 隔离守卫 lineInitKey：eeqx 只能经首字母段命中（全拼段为 eeequ…无此子串）——
     *  去调缺失时 init 段为 éééqx 该断言必红；eee 走全拼通道守不住此缺陷 */
    @Test fun `首字母通道跨带调字`() = assertTrue(SearchMatcher.matches(poems[1], "eeqx", keys(poems[1])))
}
```

- [ ] **Step 3: 写真实资产回归测试（规格示例直测）**

`app/src/test/java/com/rakor/tangshi/data/SearchIntegrationTest.kt`：

```kotlin
package com.rakor.tangshi.data

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import com.rakor.tangshi.data.search.SearchMatcher
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 真实资产回归：poems.json + search_index.json 全量过匹配器，直测规格示例。
 * 计数锚定当前 325 首语料——poems.json 变更后必须重跑 build_search_index.py，
 * 此处红了即提醒。单测工作目录在 app/ 或仓库根，双路径兜底。
 */
class SearchIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun assetText(rel: String): String =
        listOf(File("src/main/assets/$rel"), File("app/src/main/assets/$rel"))
            .firstOrNull { it.exists() }?.readText()
            ?: error("找不到资产 $rel（工作目录 ${File(".").absolutePath}）")

    private val poems: List<Poem> = json.decodeFromString(assetText("content/poems.json"))
    private val index: SearchIndex = json.decodeFromString(assetText("content/search_index.json"))
    private val keys = poems.associate { it.id to SearchMatcher.buildKeys(it, index) }

    private fun hits(keyword: String): List<String> =
        poems.filter { SearchMatcher.matches(it, keyword, keys.getValue(it.id)) }.map { it.id }

    @Test fun `LB 命中李白全部诗且共 82 首`() {
        val h = hits("LB")
        assertEquals(82, h.size)
        val libai = poems.filter { it.poet == "李白" }.map { it.id }.toSet()
        assertTrue(libai.all { it in h })
    }

    @Test fun `CQMYG 只命中静夜思`() = assertEquals(listOf("jing-ye-si"), hits("CQMYG"))

    @Test fun `DENGGUANQUELOU 命中登鹳雀楼`() = assertEquals(listOf("deng-que-lou"), hits("DENGGUANQUELOU"))

    @Test fun `全拼通道保留 chuangqian 命中静夜思`() = assertTrue("jing-ye-si" in hits("chuangqian"))

    @Test fun `V 形与 U 形全拼等价命中洛阳女儿行`() {
        val v = hits("LUOYANGNVERXING")
        assertEquals(hits("LUOYANGNUERXING"), v)
        assertTrue(v.isNotEmpty())
    }

    /** 索引覆盖闭环：锚 id 集合与诗人名集合（防增删诗/改诗人名后忘重跑工具；
     *  同 id 改标题的陈旧值不在此拦，由生成物全量打印人审兜底） */
    @Test fun `索引完整覆盖诗库`() {
        assertEquals(poems.map { it.id }.toSet(), index.titles.keys)
        assertEquals(poems.map { it.poet }.toSet(), index.poets.keys)
    }
}
```

- [ ] **Step 4: 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "*SearchMatcherTest" --tests "*SearchIntegrationTest" -q`

Expected: 编译失败——`Unresolved reference: buildKeys` / `matches` 参数不匹配。

- [ ] **Step 5: 实现 SearchMatcher**

`SearchMatcher.kt` 整体替换为：

```kotlin
package com.rakor.tangshi.data.search

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.SearchIndex
import java.text.Normalizer

/** 组合变音符正则：顶层只编译一次（此前 stripTone 内联 Regex，每次过滤每诗重编译） */
private val COMBINING_MARKS = Regex("\\p{M}+")

/**
 * 搜索匹配纯函数（规格 5.3，2026-10-12 改版）。
 *
 * 匹配范围：汉字关键字命中标题 + 全部诗句 + 诗人名（当前输入链路仅屏幕 A-Z 键，
 * 汉字匹配为数据能力预留——spec 实体键盘前提已勘误）；
 * 拼音命中走 [buildKeys] 预构建合并键——首字母（LB→李白、CQMYG→床前明月光）
 * 与全拼（chuangqian）双通道。拼音数据：诗句来自逐字 CharPy（人工校对，运行时算），
 * 标题/诗人来自 search_index.json（构建期 pypinyin 生成，缺条目降级空串段）。
 */
object SearchMatcher {

    /** 通用入口；VM 热路径走四参重载（keywordKey 预计算） */
    fun matches(poem: Poem, keyword: String, keys: String): Boolean =
        matches(poem, keyword, keys, stripTone(keyword.trim()))

    /**
     * 热路径重载：[keys] 为 [buildKeys] 预构建合并键，[keywordKey] 为
     * stripTone(keyword.trim())——循环不变量，调用方每个按键只算一次；
     * 每首诗只剩汉字文本 / 合并键两次 contains（汉字文本拼接沿旧行为每键重建，
     * 325 首开销与旧版持平）。
     */
    internal fun matches(
        poem: Poem,
        keyword: String,
        keys: String,
        keywordKey: String
    ): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        val text = poem.title + poem.lines.joinToString("") { it.text } + poem.poet
        return text.contains(kw) || keys.contains(keywordKey)
    }

    /**
     * 每诗合并键：五段 `/` 分隔——诗句全拼+首字母 / 标题全拼 / 标题首字母 /
     * 诗人全拼 / 诗人首字母。索引缺键段为空串（降级，诗句键仍可命中）；
     * 查询串只含 A-Z/汉字、不含 `/`，不会跨段拼出假命中。
     */
    fun buildKeys(poem: Poem, index: SearchIndex): String {
        val title = index.titles[poem.id]
        val poet = index.poets[poem.poet]
        return listOf(
            pinyinKey(poem) + lineInitKey(poem),
            title?.full.orEmpty(),
            title?.init.orEmpty(),
            poet?.full.orEmpty(),
            poet?.init.orEmpty()
        ).joinToString("/")
    }

    /** 全诗拼音键：诗句逐字拼音去声调连写（如 chuangqianmingyueguang…），VM 初始化预构建一次 */
    internal fun pinyinKey(poem: Poem): String =
        stripTone(poem.lines.joinToString("") { line -> line.chars.joinToString("") { it.py } })

    /**
     * 诗句首字母键：逐字拼音**去调后**取首字母小写连写（床前明月光 → cqmyg）。
     * 必须先 [stripTone]——带调首字（ā/é 等预组合字符）直接 first() 会把变音符号
     * 带进键（ééé ≠ eee），A-Z 查询跨该字失配；无拉丁首字（数据异常如 py=汉字
     * 𪭢）跳过。与全拼同源 CharPy。
     */
    internal fun lineInitKey(poem: Poem): String =
        poem.lines.joinToString("") { line ->
            line.chars.joinToString("") { c ->
                stripTone(c.py).firstOrNull { it in 'a'..'z' }?.toString() ?: ""
            }
        }

    /** 去声调 + 小写：NFD 分解去组合变音符（ǔ→u）+ v→u（lv→lu，与索引侧口径统一） */
    internal fun stripTone(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
            .lowercase().replace("v", "u")
}
```

- [ ] **Step 6: 运行新测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*SearchMatcherTest" --tests "*SearchIntegrationTest" -q`

Expected: `BUILD SUCCESSFUL`（夹具 15 + 集成 6 全 PASS）。集成计数若与 82/1/1 对不上，先核对 `search_index.json` 是否为 Task 1 最新生成物，再查实现——不得改期望值迁就（修正版模拟已核：lineInitKey 去调修复不改变 82/1/14/1/2 五个期望值）。

- [ ] **Step 7: 全量回归 + 提交**

Run: `./gradlew testDebugUnitTest -q` → Expected: `BUILD SUCCESSFUL`。

```bash
git add app/src/test/java/com/rakor/tangshi/data/TestFixtures.kt \
        app/src/test/java/com/rakor/tangshi/data/SearchMatcherTest.kt \
        app/src/test/java/com/rakor/tangshi/data/SearchIntegrationTest.kt \
        app/src/main/java/com/rakor/tangshi/data/search/SearchMatcher.kt
git commit -m "feat: SearchMatcher 首字母+诗人匹配——删诗人过滤，五段合并键双通道"
```

注（执行勘误，实测修正）：此时 `SearchScreen.kt:161` 仍引用旧 5 参 `matches`，而 **`testDebugUnitTest` 依赖 `compileDebugKotlin`**（"单测源集不编译 UI"论断不成立）——Step 6/7 的验证需临时把该行改为新 4 参调用 `SearchMatcher.matches(it, keyword, vm.pinyinKeys.getValue(it.id), keywordKey)` 跑测，跑完 `git checkout -- app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt` 还原。**提交树上单独跑 `testDebugUnitTest` 会红，属计划内已知状态，Task 4 Step 8 的 assembleDebug 才是首次无补丁全量编译。**

---

### Task 4: 搜索页删诗人选择 + 焦点/文案/VM 改造

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`

- [ ] **Step 1: 替换 SearchViewModel 与 KDoc**

将 `SearchViewModel` 类（含其上方 KDoc）整体替换：

```kotlin
/**
 * 搜索页数据：诗库快照 + 每诗合并拼音键缓存（诗句全拼+首字母 / 标题 / 诗人键，
 * 初始化构建一次，之后每个按键只做 String.contains）。
 */
class SearchViewModel(poems: PoemRepository) : ViewModel() {
    val all: List<Poem> = poems.loadAll()
    val keys: Map<String, String> = poems.loadSearchIndex().let { index ->
        all.associate { it.id to SearchMatcher.buildKeys(it, index) }
    }
}
```

（删除 `poets` 属性——随浮层一起消失；`Collator`/`Locale` import 随后清理。）

- [ ] **Step 2: 替换 SearchScreen KDoc**

将 `@Composable fun SearchScreen` 上方整段 KDoc（含 `@OptIn` 注解行）替换：

```kotlin
/**
 * 搜索页（规格 5.3，2026-10-12 改版）：顶栏放大镜+搜索唐诗｜关键字输入行（诗人/诗名/诗句）｜
 * 左结果列表（命中字高亮）+ 右屏幕键盘｜底部按键提示。
 *
 * 焦点状态机：
 * - 进屏初始焦点落关键字框；
 * - 关键字框 OK → 焦点移到键盘首键 A；键盘卡 hasFocus 双向驱动 keyboardActive
 *   （D-pad 从结果区直接进键盘同样亮激活边框），移出键盘区自动退激活；
 * - 键盘"搜索"键 OK / 键盘激活态按 B → 收起键盘（退高亮），焦点回结果列表首行
 *   （空结果回关键字框）；
 * - B 键两层语义：键盘激活态 > 系统返回（退出本屏回首页）。
 *
 * 实时过滤：keyword 变化即重算 results（VM 持每诗合并拼音键，matches 四参重载复用，
 * 避免每次按键重复拼接 325 首键串）。
 */
@Composable
fun SearchScreen(onPoem: (String) -> Unit) {
```

同时删除该 KDoc 上方的两行浮层遗留：注释行 `// 浮层焦点陷阱用 focusProperties.exit（ui 1.7 仍标 @ExperimentalComposeUiApi，行为已稳定）` 与 `@OptIn(ExperimentalComposeUiApi::class)` 行，以及文件头 `import androidx.compose.ui.ExperimentalComposeUiApi`（浮层焦点陷阱专用）。

- [ ] **Step 3: 删状态、改 results、改焦点请求器**

状态块替换为：

```kotlin
    // 本屏状态：关键字、键盘激活态
    var keyword by remember { mutableStateOf("") }
    var keyboardActive by remember { mutableStateOf(false) }
```

results 派生替换为：

```kotlin
    // 实时结果（拼音键走 VM 合并键缓存；关键字键每键只 stripTone 一次，每首诗
    // 只剩汉字文本 / 合并键两次 contains——325 首逐键过滤无重复拼接）
    val results = remember(vm.all, vm.keys, keyword) {
        val keywordKey = SearchMatcher.stripTone(keyword.trim())
        vm.all.filter {
            SearchMatcher.matches(it, keyword, vm.keys.getValue(it.id), keywordKey)
        }
    }
```

（删除其后 `poetOptions` val 整块。）

焦点请求器与 effect 替换为：

```kotlin
    // 焦点落点：关键字框 / 键盘首键 / 结果首行
    val keywordBtnFocus = remember { FocusRequester() }
    val firstKeyFocus = remember { FocusRequester() }
    val firstResultFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { requestFocusWhenReady(keywordBtnFocus) }
```

（删除 `pickerFirstFocus` 与其 `LaunchedEffect(poetPickerOpen)`。）

- [ ] **Step 4: exitKeyboard fallback 与 BackHandler 两层化**

```kotlin
    // 收起键盘：焦点回结果首行（空结果回关键字框）
    fun exitKeyboard() {
        keyboardActive = false
        if (runCatching { firstResultFocus.requestFocus() }.isFailure) {
            runCatching { keywordBtnFocus.requestFocus() }
        }
    }

    // B 键两层语义：键盘激活态 > 系统返回（BackHandler 先于系统拦截）
    BackHandler(enabled = keyboardActive) { exitKeyboard() }
```

（删除 `closePoetPicker()` 与第二个 `BackHandler(enabled = poetPickerOpen)`。）

- [ ] **Step 5: 筛选行改关键字行**

将整段筛选行（含其上方注释行 `// 筛选行：诗人筛选（弹浮层）+ 关键字显示框（进键盘激活态）` 与诗人钮、关键字钮两个子项的完整 `Row(...)`）替换为：

```kotlin
            // 输入行：关键字框（进键盘激活态）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    // 键盘键常驻组合（requester 恒已挂载），直接移焦点即可；激活边框由
                    // 键盘卡 hasFocus 双向驱动（见下方 onFocusChanged），此处无需置位
                    onClick = { runCatching { firstKeyFocus.requestFocus() } },
                    colors = normalPillColors(),
                    border = normalPillBorder(),
                    modifier = Modifier.focusTone(tone).focusRequester(keywordBtnFocus)
                ) {
                    TvIcon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    TvText("诗人/诗名/诗句：" + keyword.ifEmpty { "点按输入" } + "▏")
                }
            }
```

- [ ] **Step 6: 删诗人浮层与 PickerItem**

- 从注释行 `// 诗人浮层：半透明遮罩 + 居中白卡；首项"全部诗人"。` 起，到 `if (poetPickerOpen) { ... }` 块的闭合 `}`（位于 `KeyHintBar(...)` 之后、`CreamBackground` 闭合之前）——整块删除。
- 删除文件尾部 `PickerItem` 函数（KDoc `/** 浮层诗人项："姓名 诗数"小卡，官方焦点描边+缩放 */` 起至函数结束）。

- [ ] **Step 7: 清理 import**

删除以下 import（浮层/诗人排序专用，删后编译器校验）：

```
androidx.compose.foundation.background
androidx.compose.foundation.layout.heightIn
androidx.compose.foundation.rememberScrollState
androidx.compose.foundation.verticalScroll
androidx.compose.ui.ExperimentalComposeUiApi
androidx.compose.ui.focus.focusProperties
java.text.Collator
java.util.Locale
```

- [ ] **Step 8: 编译 + 全量单测**

```bash
cd D:/develop/src/rakor/tangshi && ./gradlew assembleDebug -q && ./gradlew testDebugUnitTest -q
```

Expected: `BUILD SUCCESSFUL`（Task 3 的 SearchScreen 引用缺口在此闭合）。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt
git commit -m "refactor: 搜索页删诗人选择——关键字框主入口（诗人/诗名/诗句），焦点两层化"
```

---

### Task 5: 键帽文字居中

**Files:**
- Modify: `app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`（`KeyBox` 内）

- [ ] **Step 1: 修复居中**

`KeyBox` 末尾的内层 Box：

```kotlin
        Box(contentAlignment = Alignment.Center, content = content)
```

替换为（`fillMaxSize` 已在文件头 import）：

```kotlin
        // fillMaxSize 必需：Surface 内容按 top-start 放置，不填满则文字顶在键帽左上角
        // （实测偏移 dx=33 dy=20px），填满后 contentAlignment=Center 才真正生效
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
```

- [ ] **Step 2: 编译**

Run: `./gradlew assembleDebug -q` → Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt
git commit -m "fix: 键帽文字居中——KeyBox 内层 Box 补 fillMaxSize（top-start 顶格修复）"
```

---

### Task 6: 实机验收（emulator-5554）

**Files:** 无代码改动（发现问题回 Task 1-5 修复后重跑本任务）

- [ ] **Step 1: 安装启动**

```bash
cd D:/develop/src/rakor/tangshi && ./gradlew :app:installDebug -q
D:/develop/androidsdk/platform-tools/adb.exe shell monkey -p com.rakor.tangshi -c android.intent.category.LEANBACK_LAUNCHER 1
```

Expected: szj_tv 模拟器正常启动到首页（`adb devices` 先确认 `emulator-5554` 在线）。

- [ ] **Step 2: 脚本断言（打字/计数/入口/居中）**

```bash
python tools/verify_search.py LB CQMYG JYS DENGGUANQUELOU CHUANGQIAN
```

Expected（每词独立走完 强停→首页→搜索页→打字→过滤 全链路，约 5 分钟）：

```
PASS  键帽文字居中: 最大偏移 dx=≤10 dy=≤12px（键 ?；基线 33/20，容差 10/12）
PASS  LB: 共找到 82 首（期望 82）
PASS  CQMYG: 共找到 1 首（期望 1）
PASS  JYS: 共找到 14 首（期望 14）
PASS  DENGGUANQUELOU: 共找到 1 首（期望 1）
PASS  CHUANGQIAN: 共找到 2 首（期望 2）——全拼通道不回归
```

脚本同时内断言：进屏焦点=关键字框（带光标）、全屏无「诗人：」筛选钮、每字母输入后关键字值逐步正确。退出码 0 为过。

- [ ] **Step 3: 空结果态焦点回退**

```bash
python tools/verify_search.py ABC
D:/develop/androidsdk/platform-tools/adb.exe shell "uiautomator dump /sdcard/ui.xml"
D:/develop/androidsdk/platform-tools/adb.exe exec-out "cat /sdcard/ui.xml" | grep -o 'text="[^"]*"' | grep 没有找到
```

Expected: 打出 `ABC` 后显示「没有找到相关唐诗」（脚本打印「空结果」）。然后按一次 B（`input keyevent 4`）→ 再 dump → 焦点（`focused="true"`）应落在**关键字框**子树（含 `▏` 文本的容器），即空结果 fallback 生效；同时屏幕上无「诗人：」文本。（勘误：原用词 XYZ 在新 contains 语义下是合法命中 15 首——寻隐者不遇标题首字母 xyzby 等——空态须用真正零结果词 ABC。）

- [ ] **Step 4: 手动走查清单**

- [ ] 键盘激活橙色描边：OK 进键盘后键盘白卡亮 4dp 橙边；D-pad 从结果区方向进键盘同样亮
- [ ] 键盘「搜索」键 OK → 收起激活态，焦点回结果首行
- [ ] 结果行 OK 进详情、B 返回正常；汉字关键字（实体键盘场景）与高亮渲染不回归
- [ ] 进屏焦点静音（无首焦提示音）、焦点移动有提示音；KaiTi 诗名、命中高亮正常
- [ ] 截图目检键帽字母/删除图标/「搜索」键文字居中（`adb exec-out screencap -p > /tmp/s.png` + Read 查看）

- [ ] **Step 5: 终检回归**

Run: `./gradlew testDebugUnitTest -q` → Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6: 记录结论**

全过 → 本计划完成（修复性改动随各 Task 提交，本任务无新提交）。任何一条不过 → 回对应 Task 修复，重跑本任务，**不带病收工**。
