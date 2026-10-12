# 搜索页改版设计（去诗人选择 + 拼音首字母搜索 + 键帽文字居中）

日期：2026-10-12
状态：已确认（用户批准）

## 背景与动机

搜索页（规格 5.3）当前形态：

- 顶部两控件：「诗人」筛选钮（弹 76 人浮层网格，Collator 拼音排序）+ 关键字框；
- 匹配 = 汉字 contains（标题+诗句）**或** 诗句全拼 contains（逐字拼音去声调连串）；
- 屏幕键盘只有 A-Z——汉字路径实际打不出来，诗句标题也无拼音数据，所以**今天的拼音搜索只能覆盖诗句**：输入 `dengguanquelou` 搜不到《登鹳雀楼》，搜诗人只能走浮层筛选。

用户需求（2026-10-12 提出）：

1. **去掉诗人选择**，搜索框同时支持搜诗人和诗句；
2. **拼音只需首字母**：`LB` → 李白，`CQMYG` → 床前明月光；
3. **右侧小键盘文字居中显示**。

## 用户决策（已确认）

1. **完整拼音匹配保留**：首字母和全拼都命中（超集，不破坏既有用法与测试）。
2. **诗名也参与拼音搜索**：首字母 + 全拼都搜（顺带修复"标题无拼音数据"的既有缺口）。
3. **拼音索引走方案 A**：构建期生成独立 JSON 资产（否决"生成 Kotlin 静态表"与"扩展 poems.json schema"两个备选——前者把数据写进代码、后者动整条数据管线回归面过大）。

## 目标与范围

**范围内**：

- 新工具 `tools/build_search_index.py` + 新资产 `app/src/main/assets/content/search_index.json`（诗人/标题的全拼+首字母）
- `SearchMatcher`：删诗人过滤、汉字范围扩到诗人名、拼音匹配改合并键
- `SearchScreen`：删诗人筛选钮与浮层、关键字框改主入口、焦点状态机简化、键帽文字居中
- 测试改造与实机验证

**范围外（明确不动）**：

- 诗句拼音数据来源（`CharPy`，人工校对的权威数据，运行时直接用）
- 屏幕键盘布局/键位（A-Z + 删除 + 搜索）、结果列表与命中高亮、计数行、KeyHintBar
- 导航路由、`PoemRepository` 读诗主链路、TTS/音频/游戏等其他屏幕

## 第 1 节：数据层与匹配语义

### 索引资产 `assets/content/search_index.json`

只存运行时推不出来的两块：

```json
{
  "poets":  { "李白":       { "full": "libai",     "init": "lb"  } },
  "titles": { "jing-ye-si": { "full": "jingyesi",  "init": "jys" } }
}
```

- `poets` 键 = 诗人名（76 条）；`titles` 键 = 诗 id（325 条）。
- `full` = 去调全拼小写连写；`init` = 逐字首字母小写连写。

### 生成工具 `tools/build_search_index.py`

- 输入 `app/src/main/assets/content/poems.json`，pypinyin 词组级上下文（`lazy_pinyin` style=NORMAL）出全拼，首字母取逐字首字符。
- **多音字 override 表**：新建 `tools/search_index_overrides.json`（沿用 `tools/polyphone_overrides.json` 的记录模式），生成时全量打印 76 诗人 + 325 标题供审；重点位：`将进酒 → qjj`（非 jjj）、复姓 `皇甫/綦毋/司空/欧阳` 类、标题中 `行/长/乐/不` 等多音字。
- **强制完整校验**：poems.json 中每个诗 id、每个诗人都必须有条目，缺一即非零退出——防"poems.json 更新后索引未重跑"的第一道闸。
- 生成结果提交进仓库（与 poems.json 同模式：数据预生成，构建机不依赖 Python）。

### 诗句键仍运行时算（不进索引）

- 全拼：现有 `SearchMatcher.pinyinKey()` 不动（`CharPy.py` 去声调连写）。
- 首字母：逐字 `py` 首字母小写连写（`chuáng… → cqmyg…`）。

### 每诗合并键（`/` 分隔，单次 contains）

```
"<诗句全拼+首字母>/<标题全拼>/<标题首字母>/<诗人全拼>/<诗人首字母>"
例（静夜思）："chuangqianmingyueguang…cqmygdtsxjtwmydtsxg/jingyesi/jys/libai/lb"
```

查询串只含 A-Z 或汉字，不含 `/`，永不会跨段拼出假命中。拼装函数放 `SearchMatcher` 纯函数，VM 与测试共用同一份格式知识。

### `SearchMatcher` 改动

- 删 `poetFilter` 参数（三参与热路径重载都改）。
- 汉字匹配：`标题 + 全部诗句 + 诗人名` contains 关键字（**勘误 2026-10-12**：原注"实体键盘可打中文"不成立——keyword 仅由屏幕 A-Z 键写入，无汉字输入路径；汉字通道保留为数据能力预留，不新增输入方式）。
- 拼音匹配：`合并键.contains(stripTone(keyword))`（`stripTone` 已做小写化，键全小写）。
- VM 预构建每诗合并键一次；每键过滤每首诗仍是一次汉字 + 一次合并键 contains，性能与现状持平。
- 运行时降级：索引文件缺失或缺键 → 该诗对应段为空串，诗句全拼/首字母照常工作，不崩溃。

## 第 2 节：搜索页 UI 与焦点状态机

### 删除诗人选择（整块）

- 「诗人：… ▾」筛选钮、诗人浮层（遮罩 + 白卡 + 网格）、`PickerItem` 组件；
- `poetFilter` / `poetPickerOpen` 状态、浮层焦点陷阱（`focusProperties.exit`）、专属 `BackHandler`、`pickerFirstFocus` 及其 `LaunchedEffect`、`poetOptions`；
- VM 的 `poets` 列表（含 Collator/Locale 排序依赖）。

### 关键字框改主入口

- 文案：**「诗人/诗名/诗句：」** + 关键字（空态「点按输入」保留）；
- OK → 键盘首键 A（不变）；进屏初始焦点落关键字框（`poetBtnFocus` 改名 `keywordBtnFocus`）。

### 焦点状态机（新，B 键两层语义）

```
进屏 → 关键字框 ──OK──→ 键盘（A 起）──「搜索」键OK / 激活态B──→ 结果首行（无结果回关键字框）
B 键：键盘激活态 → 系统返回（退出本屏）
```

- 无结果 fallback 从原「诗人钮」改为「关键字框」；
- 键盘卡 `hasFocus` 双向驱动激活描边机制不动；
- 焦点音效 `firstFocus` 静音首落逻辑不动；KDoc 焦点状态机注释同步更新。

### 键帽文字居中

- 根因：`KeyBox` 内层 `Box(contentAlignment = Center)` 未 `fillMaxSize`，Surface 内容按 top-start 放置，文字只包在小 Box 里顶在键帽左上角。
- 修复：内层 Box 补 `Modifier.fillMaxSize()`；覆盖字母键、删除图标、「搜索」键。

### 不动的部分

结果行、汉字命中高亮、计数行、KeyHintBar、键盘布局与激活描边。

## 第 3 节：测试与验证

### 单元测试（`SearchMatcherTest` + `TestFixtures`）

- 夹具为两首诗手配 title/poet 键，经 `SearchMatcher` 拼装函数成合并键；
- 删「诗人过滤」用例，其余按新签名改造；
- 新增：`cqmyg`（句首字母）/ `lb`（诗人首字母）/ `jys`（标题首字母）/ `libai` / `jingyesi`（全拼保留+新覆盖）/ `李白`（汉字路径含诗人）/ 索引缺键降级 / `xyz` 不命中 / 空关键字全量命中。

### 工具侧验证

- 跑 `python tools/build_search_index.py`，审全量打印输出，疑义条目进 override 表重跑；
- 缺条目校验必须非零退出（脚本自测一遍：临时删条目确认报错）。

### 实机验证（szj_tv 模拟器 debug 包）

- 键帽字母/图标居中（修复前后对比）；
- 进屏焦点落关键字框；`LB → 李白`、`CQMYG → 静夜思`、`JYS → 静夜思`、`dengguanquelou → 登鹳雀楼`、`chuangqian` 不回归；
- 诗人钮/浮层消失、无结果时焦点回退正确、B 两层语义、键盘激活描边不回归。

### 回归

`gradlew test` 全绿。
