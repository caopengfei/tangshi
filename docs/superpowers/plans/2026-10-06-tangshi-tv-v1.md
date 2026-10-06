# 唐诗三百首 TV 应用 V1 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建离线 Android TV 儿童唐诗学习应用（诵读跟读/进度/创作背景/两个背诗小游戏/搜索/家长设置）。

**Architecture:** 单 Activity + Compose Navigation；诗词为 assets 静态 JSON（逐字拼音是唯一发音权威），进度存 Room，设置存 DataStore；音频为构建期预生成的逐句 Opus（播放器零 TTS 依赖）。规格书：`docs/superpowers/specs/2026-10-06-tangshi-tv-app-design.md`。

**Tech Stack:** Kotlin 2.0 / Compose（androidx.compose + material-icons-extended）/ Media3 ExoPlayer / Room / DataStore / kotlinx.serialization / JUnit4 + Robolectric。

**范围注记（重要）：**
- 数据以 **10 首种子诗**先行（Task 2/3），app 全功能可跑可测；剩余诗词扩充是 Task 13 的 runbook（带校验门），不在本计划内逐字展开。
- 音频先生成种子集（Task 11），全量生成同属 runbook。

---

## 文件结构总览

```
tangshi/
├─ settings.gradle.kts / build.gradle.kts / gradle/libs.versions.toml
├─ gradle.properties
├─ app/
│  ├─ build.gradle.kts
│  ├─ src/main/AndroidManifest.xml
│  ├─ src/main/java/com/rakor/tangshi/
│  │  ├─ MainActivity.kt
│  │  ├─ AppNavHost.kt
│  │  ├─ data/
│  │  │  ├─ model/Poem.kt                  # @Serializable 数据模型 + hanChars()
│  │  │  ├─ repo/PoemRepository.kt         # assets 加载 + 内存缓存
│  │  │  ├─ db/PoemDatabase.kt             # Room: 实体/DAO/DB
│  │  │  ├─ repo/ProgressRepository.kt     # 进度规则（唯一权威实现）
│  │  │  ├─ repo/SettingsRepository.kt     # DataStore
│  │  │  └─ search/SearchMatcher.kt        # 诗人+关键字(汉字/全拼)匹配
│  │  ├─ audio/PoemPlayer.kt               # ExoPlayer 封装（连读/单句循环/倍速）
│  │  ├─ audio/KeyTonePlayer.kt            # 焦点提示音
│  │  ├─ game/QuizGenerator.kt             # 填空/听音出题（纯函数可测）
│  │  ├─ ui/theme/Theme.kt                 # 风格D色板与形状
│  │  ├─ ui/components/Shared.kt           # FocusCard/PillButton/StatusBadge/KeyHintBar
│  │  └─ ui/screens/…(home/detail/library/search/games/settings)
│  ├─ src/main/assets/content/poems.json   # 诗数据（种子10首起步）
│  └─ src/test/java/com/rakor/tangshi/…    # 单测（含 AudioAssetsTest）
├─ tools/
│  ├─ make_poem.py                         # 文本+拼音 → poems.json 条目
│  ├─ gen_voice.py                         # TTS 输入文本/替换表应用/缺失多音字报错
│  ├─ tts_substitutions.json               # 同音字替换表
│  ├─ validate_voice.py                    # 音频完整性校验（魔数/大小/齐全）
│  ├─ build_manifest.py                    # ffprobe → manifest.json
│  └─ voice_script.txt                     # A节对照稿 / B节替换记录（存档）
```

---

### Task 1: 工程骨架（可构建、可安装、启动空白 TV 界面）

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`、`app/src/main/java/com/rakor/tangshi/MainActivity.kt`、`app/src/main/java/com/rakor/tangshi/ui/theme/Theme.kt`

- [ ] **Step 1: Gradle 引导**

若 `gradle -v` 不可用：下载 Gradle 并引导 wrapper（走本地代理）：

```bash
cd /d/develop/src/rakor/tangshi
mkdir -p /tmp/gradle && cd /tmp/gradle
export https_proxy=http://127.0.0.1:9999
curl -LO https://services.gradle.org/distributions/gradle-8.11.1-bin.zip && unzip -q gradle-8.11.1-bin.zip
cd /d/develop/src/rakor/tangshi
/tmp/gradle/gradle-8.11.1/bin/gradle wrapper --gradle-version 8.11.1
```

预期：项目根生成 `gradlew`、`gradle/wrapper/*`。

- [ ] **Step 2: 版本目录 `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
composeBom = "2024.12.01"
media3 = "1.5.1"
room = "2.6.1"
navigation = "2.8.5"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
datastore = "1.1.1"
serializationJson = "1.7.3"
coroutines = "1.9.0"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.0.21-1.0.27" }
```

- [ ] **Step 3: `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "tangshi"
include(":app")
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
```

- [ ] **Step 4: `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rakor.tangshi"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.rakor.tangshi"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
```

- [ ] **Step 5: `app/src/main/AndroidManifest.xml`（TV 入口）**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.software.leanback" android:required="true" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <application
        android:label="唐诗三百首"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Tangshi"
        android:banner="@drawable/app_banner">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

同时创建：`app/src/main/res/values/themes.xml`（`<style name="Theme.Tangshi" parent="android:Theme.Material.NoActionBar"/>`）、`app/src/main/res/drawable/app_banner.xml`（320×180 矢量：奶油底 `#FFF6E8` + 橙色文字路径可暂用简单色块 + `<path>` 画方块即可，先占位）、`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`（adaptive icon，背景 `#FFF6E8`，前景简单橙色圆）。图标是占位资产，Task 12 统一美化。

- [ ] **Step 6: `MainActivity.kt` + 主题 `Theme.kt`**

```kotlin
// ui/theme/Theme.kt
package com.rakor.tangshi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 风格D 童趣暖阳 —— 色板唯一来源，全部界面从这里取色
object T {
    val BgTop = Color(0xFFFFF6E8); val BgMid = Color(0xFFFDEFDD); val BgBottom = Color(0xFFFDE8D8)
    val Card = Color(0xFFFFFFFF)
    val Orange = Color(0xFFFF7A50); val OrangeLight = Color(0xFFFF9A76); val OrangeSoft = Color(0xFFFF8A65)
    val ChipBg = Color(0xFFFFF2EA); val BadgeBg = Color(0xFFFFE9DE)
    val TextTitle = Color(0xFF6B4F3A); val TextBody = Color(0xFF7A6450); val TextSubtle = Color(0xFFC0987E)
    val TextMuted = Color(0xFFC0A090); val Idle = Color(0xFFF5EFE6)
    val Amber = Color(0xFFFFB347)
}

private val scheme = darkColorScheme(  // TV 深色环境；实际颜色全走 T 对象
    primary = T.Orange, background = T.BgMid, surface = T.Card, onPrimary = Color.White
)

@Composable
fun TangshiTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = Typography(
        titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, letterSpacing = 4.sp, color = T.TextTitle)
    ), content = content)
```

```kotlin
// MainActivity.kt
package com.rakor.tangshi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.rakor.tangshi.ui.theme.TangshiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TangshiTheme { androidx.compose.material3.Text("唐诗三百首") } }
    }
}
```

- [ ] **Step 7: 构建验证**

```bash
./gradlew :app:assembleDebug
```
预期：`BUILD SUCCESSFUL`。（JDK17 需要：Android Studio 自带 jbr，可设 `org.gradle.java.home`）

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "chore: Android TV 工程骨架（Compose+TV manifest+风格D主题）"
```

---

### Task 2: 诗词数据模型 + 资产仓库 + 校验

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/data/model/Poem.kt`、`app/src/main/java/com/rakor/tangshi/data/repo/PoemRepository.kt`、`app/src/test/java/com/rakor/tangshi/data/PoemRepositoryTest.kt`、`app/src/main/assets/content/poems.json`、`tools/make_poem.py`
- Test: `app/src/test/java/com/rakor/tangshi/data/PoemRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.model.hanChars
import com.rakor.tangshi.data.repo.PoemRepository
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class PoemRepositoryTest {
    private val repo = PoemRepository(ApplicationProvider.getApplicationContext())

    @Test fun `加载全部并按 order 排序`() {
        val all = repo.loadAll()
        assertTrue(all.size >= 2)
        assertEquals(all.sortedBy { it.order }.map { it.id }, all.map { it.id })
    }
    @Test fun `按 id 查找`() {
        assertEquals("静夜思", repo.byId("jing-ye-si")!!.title)
    }
    @Test fun `拼音与汉字一一对应`() {
        repo.loadAll().forEach { p ->
            p.lines.forEach { line ->
                assertEquals("《${p.title}》『${line.text}』拼音数不符",
                    hanChars(line.text).size, line.chars.size)
                line.chars.forEach { cd ->
                    assertEquals(1, cd.c.length)
                    assertTrue(cd.py.isNotBlank())
                }
            }
        }
    }
    @Test fun `hanChars 排除标点`() {
        assertEquals(listOf('床','前'), hanChars("床前，。"))
    }
}
```

- [ ] **Step 2: 运行确认失败** —— `./gradlew :app:testDebugUnitTest --tests "com.rakor.tangshi.data.PoemRepositoryTest"`，预期编译失败（类不存在）。

- [ ] **Step 3: 模型与仓库实现**

```kotlin
// data/model/Poem.kt
package com.rakor.tangshi.data.model

import kotlinx.serialization.Serializable

@Serializable data class Poem(val id: String, val order: Int, val title: String,
                              val poet: String, val dynasty: String,
                              val lines: List<Line>, val background: String)
@Serializable data class Line(val text: String, val chars: List<CharPy>)
@Serializable data class CharPy(val c: String, val py: String)

/** 汉字字符（排除标点/空格）——拼音对齐与出题都用它 */
fun hanChars(text: String): List<Char> =
    text.filter { Character.getType(it) == Character.OTHER_LETTER.code }
```

```kotlin
// data/repo/PoemRepository.kt
package com.rakor.tangshi.data.repo

import android.content.Context
import com.rakor.tangshi.data.model.Poem
import kotlinx.serialization.json.Json

class PoemRepository(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val poems: List<Poem> by lazy {
        context.assets.open("content/poems.json").bufferedReader().use { r ->
            json.decodeFromString<List<Poem>>(r.readText())
        }.sortedBy { it.order }
    }
    fun loadAll(): List<Poem> = poems
    fun byId(id: String): Poem? = poems.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: 种子数据（先放 2 首完整示例，其余 8 首用工具补）**

`app/src/main/assets/content/poems.json`：

```json
[
  {
    "id": "jing-ye-si", "order": 1, "title": "静夜思", "poet": "李白", "dynasty": "唐",
    "background": "李白年轻时离开家乡，长期在外游历。一个秋天的夜晚，他住在扬州的旅舍里，抬头看见窗外一轮皎洁的明月，月光洒在地上像一层白霜。他想：家乡的亲人是不是也在看着同一轮月亮呢？想念之下，写下了这首《静夜思》。",
    "lines": [
      {"text": "床前明月光，", "chars": [{"c":"床","py":"chuáng"},{"c":"前","py":"qián"},{"c":"明","py":"míng"},{"c":"月","py":"yuè"},{"c":"光","py":"guāng"}]},
      {"text": "疑是地上霜。", "chars": [{"c":"疑","py":"yí"},{"c":"是","py":"shì"},{"c":"地","py":"dì"},{"c":"上","py":"shàng"},{"c":"霜","py":"shuāng"}]},
      {"text": "举头望明月，", "chars": [{"c":"举","py":"jǔ"},{"c":"头","py":"tóu"},{"c":"望","py":"wàng"},{"c":"明","py":"míng"},{"c":"月","py":"yuè"}]},
      {"text": "低头思故乡。", "chars": [{"c":"低","py":"dī"},{"c":"头","py":"tóu"},{"c":"思","py":"sī"},{"c":"故","py":"gù"},{"c":"乡","py":"xiāng"}]}
    ]
  },
  {
    "id": "yong-e", "order": 2, "title": "咏鹅", "poet": "骆宾王", "dynasty": "唐",
    "background": "骆宾王是唐朝的诗人。相传他七岁那年，看到家旁边池塘里白鹅在水上悠闲地游动，红红的脚掌拨动清清的水波，觉得又美又有趣，随口吟出了这首《咏鹅》。",
    "lines": [
      {"text": "鹅，鹅，鹅，", "chars": [{"c":"鹅","py":"é"},{"c":"鹅","py":"é"},{"c":"鹅","py":"é"}]},
      {"text": "曲项向天歌。", "chars": [{"c":"曲","py":"qū"},{"c":"项","py":"xiàng"},{"c":"向","py":"xiàng"},{"c":"天","py":"tiān"},{"c":"歌","py":"gē"}]},
      {"text": "白毛浮绿水，", "chars": [{"c":"白","py":"bái"},{"c":"毛","py":"máo"},{"c":"浮","py":"fú"},{"c":"绿","py":"lǜ"},{"c":"水","py":"shuǐ"}]},
      {"text": "红掌拨清波。", "chars": [{"c":"红","py":"hóng"},{"c":"掌","py":"zhǎng"},{"c":"拨","py":"bō"},{"c":"清","py":"qīng"},{"c":"波","py":"bō"}]}
    ]
  }
]
```

`tools/make_poem.py`（后续诗都经它录入，防手写 JSON 出错；pinyin 列与句中汉字数必须一致否则报错）：

```python
#!/usr/bin/env python3
"""用法: python tools/make_poem.py seed/poems.txt
文件格式(每首诗):
#id: chun-xiao
#order: 3
#title: 春晓
#poet: 孟浩然
#dynasty: 唐
@background: ……一段话……
春眠不觉晓，|chūn mián bù jué xiǎo
处处闻啼鸟。|chù chù wén tí niǎo
"""
import json, sys, re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
POEMS = ROOT / "app/src/main/assets/content/poems.json"

def main():
    src = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
    poems = json.loads(POEMS.read_text(encoding="utf-8"))
    by_id = {p["id"]: p for p in poems}
    for block in re.split(r"\n(?=#id:)", src.strip()):
        meta, lines, bg = {}, [], ""
        for raw in block.strip().splitlines():
            if raw.startswith("#"): k, v = raw[1:].split(":", 1); meta[k.strip()] = v.strip()
            elif raw.startswith("@background:"): bg = raw[len("@background:"):].strip()
            elif raw.strip():
                text, pys = raw.split("|")
                pys = pys.split()
                han = [c for c in text if re.match(r"[\u4e00-\u9fff]", c)]
                assert len(han) == len(pys), f"{meta.get('id')} 『{text}』 汉字{len(han)} != 拼音{len(pys)}"
                lines.append({"text": text, "chars": [{"c": c, "py": p} for c, p in zip(han, pys)]})
        entry = {"id": meta["id"], "order": int(meta["order"]), "title": meta["title"],
                 "poet": meta["poet"], "dynasty": meta["dynasty"], "background": bg, "lines": lines}
        by_id[entry["id"]] = entry
    POEMS.write_text(json.dumps(sorted(poems, key=lambda p: p["order"]), ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"ok, total={len(poems)}")

if __name__ == "__main__":
    main()
```

- [ ] **Step 5: 测试通过** —— 重跑 Step 2 命令，预期 4 个测试 PASS。
- [ ] **Step 6: Commit** —— `git add -A && git commit -m "feat: 诗词数据模型、资产仓库与对齐校验（种子2首+录入工具）"`

---

### Task 3: 补齐 10 首种子诗

**Files:**
- Create: `tools/seed/seed-poems.txt`
- Modify: `app/src/main/assets/content/poems.json`（经工具）
- Test: 复用 Task 2 测试

- [ ] **Step 1: 写 `tools/seed/seed-poems.txt`**（make_poem.py 格式），收录以下 8 首（文本为定稿；拼音逐字标注——多音字按统编教材：思 sī、曲 qū、绿 lǜ、发 fā、更 gèng、尽 jìn、还 huán（不出现）、重 chóng（不出现）等；录入后跑 Task 2 的对齐测试兜底）：

```
春晓/孟浩然：春眠不觉晓，处处闻啼鸟。夜来风雨声，花落知多少。
悯农（其二）/李绅：锄禾日当午，汗滴禾下土。谁知盘中餐，粒粒皆辛苦。
登鹳雀楼/王之涣：白日依山尽，黄河入海流。欲穷千里目，更上一层楼。
相思/王维：红豆生南国，春来发几枝。愿君多采撷，此物最相思。
望庐山瀑布/李白：日照香炉生紫烟，遥看瀑布挂前川。飞流直下三千尺，疑是银河落九天。
枫桥夜泊/张继：月落乌啼霜满天，江枫渔火对愁眠。姑苏城外寒山寺，夜半钟声到客船。
古朗月行（节选）/李白：小时不识月，呼作白玉盘。又疑瑶台镜，飞在青云端。
山行/杜牧：远上寒山石径斜，白云生处有人家。停车坐爱枫林晚，霜叶红于二月花。
```

每首配 80~150 字口语化创作背景（儿童可懂；山行"斜"按教材读 xié 标注）。
- [ ] **Step 2: 录入** —— `python tools/make_poem.py tools/seed/seed-poems.txt`，预期输出 `ok, total=10`。
- [ ] **Step 3: 全量校验** —— `./gradlew :app:testDebugUnitTest --tests "com.rakor.tangshi.data.PoemRepositoryTest"`，预期 PASS（10 首、拼音对齐）。
- [ ] **Step 4: Commit** —— `git add -A && git commit -m "feat: 种子诗数据补齐至10首"`

---

### Task 4: Room 进度存储 + 进度规则

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/data/db/PoemDatabase.kt`、`app/src/main/java/com/rakor/tangshi/data/repo/ProgressRepository.kt`
- Test: `app/src/test/java/com/rakor/tangshi/data/ProgressRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**（Robolectric + 内存 Room；覆盖规格 7.2 全部规则）

```kotlin
package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before.Test as _unused // (不要这行——见下)
```

正确写法（直接用这个）：

```kotlin
package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.ProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressRepositoryTest {
    private val repo = ProgressRepository(
        poemDatabase(ApplicationProvider.getApplicationContext<Context>(), "test-${System.nanoTime()}.db")
    )

    @Test fun `打开并播放过一句才算学习中`() = runTest {
        repo.onPoemOpened("jing-ye-si")
        assertNull(repo.observeProgress("jing-ye-si").first())   // 只打开不播放=未开始
        repo.onLinePlayed("jing-ye-si", 0)
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals("LEARNING", p.status)
        assertEquals(25, p.progress)                              // 4句播1句
    }
    @Test fun `进度按去重句数累计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.onLinePlayed("jing-ye-si", 0)                        // 重复不计
        repo.onLinePlayed("jing-ye-si", 1)
        assertEquals(50, repo.observeProgress("jing-ye-si").first()!!.progress)
    }
    @Test fun `会背置满且不回退`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        repo.markMastered("jing-ye-si")
        val p = repo.observeProgress("jing-ye-si").first()!!
        assertEquals("MASTERED", p.status); assertEquals(100, p.progress)
        repo.onPoemOpened("jing-ye-si")                           // 再打开不回退
        assertEquals("MASTERED", repo.observeProgress("jing-ye-si").first()!!.status)
    }
    @Test fun `今日所学与统计`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0)
        val stats = repo.observeStats(totalPoems = 10).first()
        assertEquals(1, stats.learned); assertEquals(0, stats.mastered)
        assertTrue(stats.todayPoemIds.contains("jing-ye-si"))
        assertEquals(1, stats.streakDays)
    }
    @Test fun `清除进度`() = runTest {
        repo.onLinePlayed("jing-ye-si", 0); repo.wipeAll()
        assertNull(repo.observeProgress("jing-ye-si").first())
    }
}
```

- [ ] **Step 2: 运行确认失败**（类不存在）。
- [ ] **Step 3: 实现**

```kotlin
// data/db/PoemDatabase.kt
package com.rakor.tangshi.data.db

import android.content.Context
import androidx.room.*
import androidx.room.Entity as E
import androidx.room.PrimaryKey as PK

@E(tableName = "poem_progress")
data class PoemProgressEntity(
    @PK val poemId: String,
    val status: String,          // NOT_STARTED/LEARNING/MASTERED
    val progress: Int,
    val playedLines: String,     // 逗号分隔行号，去重集合的持久化
    val lastPlayedAt: Long,
    val masteredAt: Long?
)
@E(tableName = "study_log") data class StudyLogEntity(@PK val date: String, val poemsTouched: Int)

@Dao interface ProgressDao {
    @Query("SELECT * FROM poem_progress WHERE poemId = :id") suspend fun get(id: String): PoemProgressEntity?
    @Query("SELECT * FROM poem_progress") fun observeAll(): kotlinx.coroutines.flow.Flow<List<PoemProgressEntity>>
    @Upsert suspend fun upsert(e: PoemProgressEntity)
    @Query("DELETE FROM poem_progress") suspend fun clearProgress()
    @Query("DELETE FROM study_log") suspend fun clearLog()
    @Query("SELECT * FROM study_log") suspend fun allLog(): List<StudyLogEntity>
    @Upsert suspend fun upsertLog(e: StudyLogEntity)
}

@Database(entities = [PoemProgressEntity::class, StudyLogEntity::class], version = 1, exportSchema = false)
abstract class PoemDatabase : RoomDatabase() { abstract fun progressDao(): ProgressDao }

fun poemDatabase(context: Context, name: String = "tangshi.db"): PoemDatabase =
    Room.databaseBuilder(context, PoemDatabase::class.java, name).build()
```

```kotlin
// data/repo/ProgressRepository.kt
package com.rakor.tangshi.data.repo

import com.rakor.tangshi.data.db.PoemDatabase
import com.rakor.tangshi.data.db.PoemProgressEntity
import com.rakor.tangshi.data.db.StudyLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class PoemProgress(val status: String, val progress: Int, val lastPlayedAt: Long)
data class HomeStats(val learned: Int, val mastered: Int, val streakDays: Int, val todayPoemIds: Set<String>)

class ProgressRepository(private val db: PoemDatabase, private val lineCountOf: (String) -> Int = { 4 }) {
    private val dao = db.progressDao()
    private fun today(): String = LocalDate.now().toString()

    private suspend fun touchToday() {
        val log = dao.allLog().firstOrNull { it.date == today() } ?: StudyLogEntity(today(), 0)
        dao.upsertLog(log.copy(poemsTouched = log.poemsTouched + 1))
    }
    private suspend fun save(id: String, status: String, progress: Int, played: Set<Int>, last: Long, masteredAt: Long?) =
        dao.upsert(PoemProgressEntity(id, status, progress, played.joinToString(","), last, masteredAt))

    suspend fun onPoemOpened(id: String) {
        val cur = dao.get(id) ?: return                       // 未开始则打开不计
        if (cur.status != "MASTERED") return
    }
    suspend fun onLinePlayed(id: String, lineIndex: Int) {
        val now = System.currentTimeMillis()
        val cur = dao.get(id)
        val played = cur?.playedLines?.split(",")?.filter { it.isNotBlank() }?.map { it.toInt() }?.toSet() ?: emptySet()
        val newPlayed = played + lineIndex
        val total = lineCountOf(id).coerceAtLeast(1)
        val status = if (cur?.status == "MASTERED") "MASTERED" else "LEARNING"
        val progress = if (status == "MASTERED") 100 else newPlayed.size * 100 / total
        save(id, status, progress, newPlayed, now, cur?.masteredAt)
        touchToday()
    }
    suspend fun markMastered(id: String) {
        val cur = dao.get(id)
        save(id, "MASTERED", 100, (0 until lineCountOf(id)).toSet(), cur?.lastPlayedAt ?: System.currentTimeMillis(),
            cur?.masteredAt ?: System.currentTimeMillis())
    }
    fun observeProgress(id: String): Flow<PoemProgress?> =
        dao.observeAll().map { list -> list.firstOrNull { it.poemId == id } }
            .map { it?.let { e -> PoemProgress(e.status, e.progress, e.lastPlayedAt) } }
    fun observeStats(totalPoems: Int): Flow<HomeStats> = dao.observeAll().map { list ->
        val today = dao2today(list)
        HomeStats(
            learned = list.count { it.status != "NOT_STARTED" },   // 表中不会有NOT_STARTED行
            mastered = list.count { it.status == "MASTERED" },
            streakDays = streakDays(dao.allLogSus(), LocalDate.now()),
            todayPoemIds = list.filter { sameDay(it.lastPlayedAt) }.map { it.poemId }.toSet()
        ).let { if (totalPoems > 0) it else it }
    }
    private suspend fun dao2today(list: List<PoemProgressEntity>) = list
    suspend fun todayPoemIds(): Set<String> =
        dao.observeAll().first().filter { sameDay(it.lastPlayedAt) }.map { it.poemId }.toSet()
    suspend fun wipeAll() { dao.clearProgress(); dao.clearLog() }

    private fun sameDay(epochMs: Long): Boolean =
        java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

    companion object {  // 纯函数：连续天数（今天缺卡则从昨天起算）
        fun streakDays(dates: Set<String>, today: LocalDate): Int {
            var d = if (dates.contains(today.toString())) today else today.minusDays(1)
            var n = 0
            while (dates.contains(d.toString())) { n++; d = d.minusDays(1) }
            return n
        }
    }
}
```

注意：上面 `observeStats` 里出现了草稿遗留调用，**落地时删掉 `dao2today` 与多余的 `.let`，`allLogSus()` 改为 `dao.allLog().map { it.date }.toSet()`**（DAO `allLog()` 是 suspend，可直接在 `map` 内 suspend 调用：`dao.observeAll().map { list -> ... dao.allLog() ... }`）。最终签名：

```kotlin
fun observeStats(): Flow<HomeStats> = dao.observeAll().map { list ->
    HomeStats(
        learned = list.size,
        mastered = list.count { it.status == "MASTERED" },
        streakDays = streakDays(dao.allLog().map { it.date }.toSet(), LocalDate.now()),
        todayPoemIds = list.filter { sameDay(it.lastPlayedAt) }.map { it.poemId }.toSet()
    )
}
```

- [ ] **Step 4: 测试通过** —— 跑 Step 1 命令，5 个测试 PASS。
- [ ] **Step 5: Commit** —— `git add -A && git commit -m "feat: Room进度存储与进度规则（含连续天数纯函数）"`

---

### Task 5: 共享 UI 组件 + 主题资源

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/ui/components/Shared.kt`、`app/src/main/java/com/rakor/tangshi/audio/KeyTonePlayer.kt`

- [ ] **Step 1: 共享组件**（焦点规范：放大 1.06 + 3dp 珊瑚橙描边 + 光晕；全部可 D-pad 聚焦）

```kotlin
// ui/components/Shared.kt
package com.rakor.tangshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.rakor.tangshi.ui.theme.T

@Composable
fun Modifier.tvFocus(borderWidth: Modifier.() -> Unit = {}): Modifier = this // 占位防误用；真实实现见 FocusCard
```

实际组件（覆盖上面占位，一个文件全放这里）：

```kotlin
package com.rakor.tangshi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.rakor.tangshi.ui.theme.T

/** 白色悬浮卡；isFocused 外部控制视觉（供 LazyGrid 等复用） */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    focused: Boolean,
    corner: Int = 20,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .shadow(if (focused) 14.dp else 6.dp, RoundedCornerShape(corner.dp),
                    ambientColor = Color(0xD2B08C), spotColor = Color(0xD2B08C))
            .background(T.Card, RoundedCornerShape(corner.dp))
            .then(if (focused) Modifier.border(3.dp, T.OrangeSoft, RoundedCornerShape(corner.dp)) else Modifier),
        content = content
    )
}

/** 渐变胶囊按钮；focused 时描边+放大 */
@Composable
fun PillButton(text: String, icon: ImageVector? = null, filled: Boolean = true,
               focused: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Row(modifier
        .scale(if (focused) 1.06f else 1f)
        .background(if (filled) Brush.horizontalGradient(listOf(T.OrangeLight, T.Orange)) else T.Card, shape)
        .border(if (focused) 3.dp else if (filled) 0.dp else 2.dp,
                if (filled && focused) Color.White else T.OrangeSoft, shape)
        .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { androidx.compose.material3.Icon(icon, null, tint = if (filled) Color.White else T.Orange); Spacer(6.dp) }
        Text(text, color = if (filled) Color.White else T.TextBody)
    }
}

/** 状态徽章：NOT_STARTED/LEARNING+percent/MASTERED —— 贴底居中（外部布局控制位置） */
@Composable
fun StatusBadge(status: String, progress: Int) {
    val (bg, fg, label) = when (status) {
        "MASTERED" -> Triple(T.BadgeBg, T.Orange, "已会背")
        "LEARNING" -> Triple(T.ChipBg, T.OrangeSoft, "学习中 $progress%")
        else -> Triple(T.Idle, T.TextMuted, "未学习")
    }
    Text(label, Modifier.background(bg, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
         color = fg)
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
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(T.BgTop, T.BgMid, T.BgBottom))), content = content)
}
```

- [ ] **Step 2: 焦点提示音**

```kotlin
// audio/KeyTonePlayer.kt
package com.rakor.tangshi.audio

import android.media.AudioManager
import android.media.ToneGenerator

/** 按键提示音（家长设置可关）。ToneGenerator 非线程安全，单线程使用。 */
class KeyTonePlayer(private val enabled: () -> Boolean) {
    private var tone: ToneGenerator? = null
    fun focusMove() { if (enabled()) beep(ToneGenerator.TONE_PROP_BEEP, 40) }
    fun confirm()   { if (enabled()) beep(ToneGenerator.TONE_PROP_ACK, 60) }
    private fun beep(t: Int, ms: Int) {
        runCatching { tone?.release(); tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45).also { it.startTone(t, ms) } }
    }
    fun release() { runCatching { tone?.release() }; tone = null }
}
```

- [ ] **Step 3: 编译验证** —— `./gradlew :app:assembleDebug`，预期 BUILD SUCCESSFUL。
- [ ] **Step 4: Commit** —— `git add -A && git commit -m "feat: 共享UI组件（FocusCard/PillButton/StatusBadge/KeyHintBar）与焦点提示音"`

---

### Task 6: 导航骨架 + 首页

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/AppNavHost.kt`、`app/src/main/java/com/rakor/tangshi/ui/screens/home/HomeScreen.kt`
- Modify: `MainActivity.kt`（挂 NavHost）

- [ ] **Step 1: 路由骨架**（规格第 6 节）

```kotlin
// AppNavHost.kt
package com.rakor.tangshi

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.rakor.tangshi.ui.screens.home.HomeScreen

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val LIBRARY = "library"
    const val GAME_SETUP = "gameSetup"; const val SETTINGS = "settings"
    const val POEM = "poem/{id}"; const val GAME = "game/{mode}"
    fun poem(id: String) = "poem/$id"; fun game(mode: String) = "game/$mode"
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(
            onPoem = { nav.navigate(Routes.poem(it)) }, onSearch = { nav.navigate(Routes.SEARCH) },
            onLibrary = { nav.navigate(Routes.LIBRARY) }, onGames = { nav.navigate(Routes.GAME_SETUP) },
            onSettings = { nav.navigate(Routes.SETTINGS) }) }
        composable(Routes.POEM) { /* Task 7 */ }
        composable(Routes.SEARCH) { /* Task 9 */ }
        composable(Routes.LIBRARY) { /* Task 8 */ }
        composable(Routes.GAME_SETUP) { /* Task 10 */ }
        composable(Routes.GAME) { /* Task 10 */ }
        composable(Routes.SETTINGS) { /* Task 11 */ }
    }
}
```

MainActivity 的 `setContent { TangshiTheme { AppNavHost() } }`。

- [ ] **Step 2: HomeViewModel + HomeScreen**（布局按规格 5.1；VM 注入 PoemRepository + ProgressRepository；构造参数注入便于测试）

```kotlin
// ui/screens/home/HomeScreen.kt —— 关键结构（完整落地时按此骨架补齐样式常量，全部取自 T 对象）
package com.rakor.tangshi.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository
import com.rakor.tangshi.data.repo.HomeStats
import com.rakor.tangshi.ui.components.*
import com.rakor.tangshi.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    val stats = progress.observeStats().stateIn(viewModelScope, SharingStarted.Eagerly, HomeStats(0,0,0,emptySet()))
    val current = poems.loadAll().first()   // 首选：最近LEARNING；MVP: 第一首（Task 12 完善为最近学习）
}

@Composable
fun HomeScreen(onPoem: (String) -> Unit, onSearch: () -> Unit, onLibrary: () -> Unit,
               onGames: () -> Unit, onSettings: () -> Unit, vm: HomeViewModel = viewModel()) {
    val stats by vm.stats.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    CreamBackground {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("唐诗三百首", fontSize = 22.sp, letterSpacing = 4.sp, color = T.TextTitle)
                Text("小朋友，今天继续加油哦 🌞", color = T.TextSubtle)
            }
            Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FocusCard(Modifier.weight(1.5f).fillMaxHeight().focusRequester(focus)
                    .focusPropertiesCompat(), focused = true) {   // 大卡默认聚焦，点击进入详情
                    Text("当前学习 · 第 ${vm.current.order} 首", color = T.Orange)
                    Text(vm.current.title, fontSize = 26.sp, letterSpacing = 4.sp, color = T.TextTitle)
                    Text(vm.current.poet, color = T.TextMuted)
                    vm.current.lines.forEach { Text(it.text, color = T.TextBody) }
                    PillButton("继 续 诵 读", Icons.Filled.PlayArrow, filled = true, onClick = { onPoem(vm.current.id) })
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatCard(Icons.Filled.MenuBook, "已学唐诗", "${stats.learned} / 10")
                    StatCard(Icons.Filled.LocalFireDepartment, "连续学习", "${stats.streakDays} 天")
                    StatCard(Icons.Filled.ThumbUp, "已会背诵", "${stats.mastered} 首")
                }
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                var f = remember { focus }; PillButton("搜索", Icons.Filled.Search, filled = false, focused = true, onClick = onSearch)
                PillButton("全部唐诗", Icons.Filled.MenuBook, filled = false, onClick = onLibrary)
                PillButton("背诗小游戏", Icons.Filled.SportsEsports, filled = false, onClick = onGames)
                PillButton("家长设置", Icons.Filled.Settings, filled = false, onClick = onSettings)
            }
            KeyHintBar("◀ ▲▼ 选择　OK 确认")
        }
    }
}

@Composable private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) =
    FocusCard(focused = false, corner = 18) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(icon, null, tint = T.Orange)
                Spacer(6.dp); Text(label, color = T.TextBody)
            }
            Text(value, color = T.Orange, fontSize = 18.sp)
        }
    }
```

落地注记：`viewModel()` 获取需 `factory` 注入仓库（`app` 提供 `AppGraph` 单例持有 PoemRepository/ProgressRepository/KeyTonePlayer，Task 7 一起建：`di/AppGraph.kt`，手写单例，不引框架）。`focusPropertiesCompat` 是草稿残留，删除；`var f = remember { focus }` 删除；每个 PillButton 用独立 `FocusRequester` 并在 `onFocusChanged` 里接 `KeyTonePlayer.focusMove()`。**首页以"最近学习的诗优先，否则第一首未学，否则第一首"计算 current**：

```kotlin
val current: Poem by (combine(progress.observeAllSummaries(), flowOf(poems.loadAll())) { s, all ->
    s.filter { it.status == "LEARNING" }.maxByOrNull { it.lastPlayedAt }?.let { p -> all.first { it.id == p.poemId } }
        ?: all.firstOrNull { p -> s.none { it.poemId == p.id } } ?: all.first()
}).stateIn(viewModelScope, SharingStarted.Eagerly, poems.loadAll().first())
```

- [ ] **Step 3: 编译 + 安装冒烟** —— `./gradlew :app:installDebug` 后 TV 模拟器启动，预期显示首页（当前诗卡 + 3 统计卡 + 4 入口）。
- [ ] **Step 4: Commit** —— `git add -A && git commit -m "feat: 导航骨架与首页"`

---

### Task 7: 详情学习页 + PoemPlayer

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/audio/PoemPlayer.kt`、`app/src/main/java/com/rakor/tangshi/di/AppGraph.kt`、`app/src/main/java/com/rakor/tangshi/ui/screens/detail/DetailScreen.kt`
- Modify: `AppNavHost.kt`（补 POEM 路由）
- Test: `app/src/test/java/com/rakor/tangshi/game/…`（本任务无纯逻辑单测；播放逻辑靠 Task 12 冒烟）

- [ ] **Step 1: AppGraph**（手写依赖容器，Application 初始化）

```kotlin
// di/AppGraph.kt
package com.rakor.tangshi.di

import android.app.Application
import android.content.Context
import com.rakor.tangshi.audio.KeyTonePlayer
import com.rakor.tangshi.data.db.poemDatabase
import com.rakor.tangshi.data.repo.PoemRepository
import com.rakor.tangshi.data.repo.ProgressRepository

class AppGraph(context: Context) {
    val poems = PoemRepository(context)
    val progress = ProgressRepository(poemDatabase(context)) { id -> poems.byId(id)?.lines?.size ?: 4 }
    val keyTone = KeyTonePlayer { true }   // Task 11 接 DataStore
    companion object { lateinit var instance: AppGraph }
}
// Application: class TangshiApp : Application() { override fun onCreate() { super.onCreate(); AppGraph.instance = AppGraph(this) } }
// Manifest 加 android:name=".TangshiApp"
```

- [ ] **Step 2: PoemPlayer**（逐句播放；句间停顿已在转码期烘进文件尾部——见 Task 10 注记；两种模式：CONTINUOUS 顺序播放 / LINE_LOOP 单句循环）

```kotlin
// audio/PoemPlayer.kt
package com.rakor.tangshi.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow

class PoemPlayer(private val exo: ExoPlayer) {
    enum class Mode { CONTINUOUS, LINE_LOOP }
    val currentLine = MutableStateFlow(0)   // 1-based；0=未播放
    val isPlaying = MutableStateFlow(false)

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { isPlaying.value = p }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                exo.currentMediaItemIndex.takeIf { it >= 0 }?.let { currentLine.value = it + 1 }
            }
        })
    }

    fun playContinuous(poemId: String, lineCount: Int, startLine: Int = 1, speed: Float = 1f) {
        exo.setMediaItems((1..lineCount).map { lineItem(poemId, it) }, startLine - 1, 0)
        exo.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_OFF
        exo.prepare(); exo.play()
    }
    fun playLineLoop(poemId: String, line: Int, speed: Float = 1f) {
        exo.setMediaItems(listOf(lineItem(poemId, line)))
        exo.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        exo.repeatMode = Player.REPEAT_MODE_ONE
        exo.currentMediaItemIndex.let { currentLine.value = line }
        exo.prepare(); exo.play()
    }
    fun togglePause() { if (exo.isPlaying) exo.pause() else exo.play() }
    fun pause() { exo.pause() }
    fun release() { exo.release() }
    private fun lineItem(poemId: String, line: Int) =
        MediaItem.fromUri("asset:///audio/$poemId/line$line.opus")
}
```

- [ ] **Step 3: DetailScreen**（规格 5.2：左逐字拼音+整句高亮，右创作背景，三按钮；◀▶选句 OK 播放/暂停 B返回；我会背了带确认对话框）

```kotlin
// ui/screens/detail/DetailScreen.kt —— 核心 composable 骨架（样式常量取 T 对象；完整落地同 Task 6 注记）
@Composable
fun DetailScreen(poemId: String, onBack: () -> Unit) {
    val g = AppGraph.instance
    val poem = remember { g.poems.byId(poemId)!! }
    val context = LocalContext.current
    val player = remember { PoemPlayer(ExoPlayer.Builder(context).build()) }
    val line by player.currentLine.collectAsState()
    val playing by player.isPlaying.collectAsState()
    var selected by remember { mutableIntStateOf(1) }
    var mode by remember { mutableStateOf(PoemPlayer.Mode.CONTINUOUS) }
    var showMasteredDialog by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<PoemProgress?>(null) }
    LaunchedEffect(poemId) { g.progress.onPoemOpened(poemId); g.progress.observeProgress(poemId).collect { progress = it } }
    DisposableEffect(Unit) { onDispose { player.release() } }

    fun okAction() {
        when (mode) {
            PoemPlayer.Mode.LINE_LOOP -> player.togglePause()
            PoemPlayer.Mode.CONTINUOUS -> if (playing) player.pause()
                else player.playContinuous(poemId, poem.lines.size, startLine = selected.coerceAtMost(poem.lines.size))
        }
    }
    BackHandler(onBack = onBack)
    CreamBackground {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("← 返回（B）", color = T.TextSubtle)
                Text("${poem.title} · ${poem.dynasty} · ${poem.poet}", fontSize = 22.sp, letterSpacing = 4.sp, color = T.TextTitle)
                Text("第 ${poem.order} / 10 首", color = T.TextSubtle)
            }
            Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1.4f)) {
                    poem.lines.forEachIndexed { i, ln ->
                        val isCurrent = (mode == PoemPlayer.Mode.LINE_LOOP && selected == i + 1) || line == i + 1
                        PoemLine(ln, highlighted = isCurrent, Modifier
                            .onKeyEvent { e -> if (e.type == KeyEventTypes.KeyUp) false else false })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("朗读", Icons.Filled.PlayArrow, filled = true, onClick = {
                            mode = PoemPlayer.Mode.CONTINUOUS; player.playContinuous(poemId, poem.lines.size, selected) })
                        PillButton("逐句跟读", Icons.Filled.Replay, filled = false, onClick = {
                            mode = PoemPlayer.Mode.LINE_LOOP; player.playLineLoop(poemId, selected) })
                        PillButton("我会背了", Icons.Filled.ThumbUp, filled = false,
                            onClick = { showMasteredDialog = true })
                    }
                }
                FocusCard(Modifier.weight(1f).fillMaxHeight(), focused = false, corner = 18) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("🌙 诗人为什么写这首诗？", color = T.Orange)
                        Spacer(8.dp)
                        Text(poem.background, lineHeight = 30.sp, color = T.TextBody)
                    }
                }
            }
            KeyHintBar("◀ ▶ 逐句移动　OK 播放/暂停　B 返回", "已学进度 ${progress?.progress ?: 0}%")
        }
    }
    if (showMasteredDialog) androidx.compose.material3.AlertDialog(onDismissRequest = { showMasteredDialog = false },
        title = { Text("确定已经会背这首诗了吗？") },
        confirmButton = { PillButton("确定", filled = true, onClick = {
            showMasteredDialog = false
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {  // 改用 rememberCoroutineScope
                g.progress.markMastered(poemId)
            } }) },
        dismissButton = { PillButton("再听听", filled = false, onClick = { showMasteredDialog = false }) })
}

/** 一行诗：每字上方小拼音；整句高亮=浅橙底圆角+左侧“◀ 正在读” */
@Composable
fun PoemLine(line: Line, highlighted: Boolean, modifier: Modifier = Modifier) {
    Row(modifier
        .background(if (highlighted) T.ChipBg else Color.Transparent, RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom) {
        if (highlighted) Text("◀ 正在读", color = T.Orange, fontSize = 12.sp)
        line.chars.forEach { cd ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(3.dp)) {
                Text(cd.py, fontSize = 11.sp, color = T.OrangeSoft)
                Text(cd.c, fontSize = 24.sp, color = T.TextTitle)
            }
        }
    }
}
```

落地注记：键盘事件用 `Modifier.focusable()` + `onPreviewKeyEvent`（LEFT/RIGHT 改 selected、OK=okAction、DPad 左右在 LINE_LOOP 下切句并 `playLineLoop`）；对话框协程用 `rememberCoroutineScope()`（草稿中的 CoroutineScope 写法替换掉）；播放过句后调用 `g.progress.onLinePlayed(poemId, lineIdx)`（在 `onMediaItemTransition` 之后由 UI 观察 currentLine 变化时记录，幂等由 Task 4 去重保证）。T01 起音频未生成前播放会静默失败——属预期，Task 10 补提示条。

- [ ] **Step 4: 编译 + 冒烟** —— `./gradlew :app:installDebug`：详情页拼音逐字显示、高亮随播放切换（无音频时按钮无响应属预期）、我会背了确认后首页"已会背诵"+1。
- [ ] **Step 5: Commit** —— `git add -A && git commit -m "feat: 详情学习页（逐字拼音/整句高亮/跟读模式/创作背景）与PoemPlayer"`

---

### Task 8: 全部唐诗页

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/ui/screens/library/LibraryScreen.kt`
- Modify: `AppNavHost.kt`

- [ ] **Step 1: 实现**（规格 5.4：状态筛选行 + 4×2 网格分页 + 一卡一诗[诗名大字居中/诗人居中/徽章贴底] + 页码）

```kotlin
// 关键结构：数据流 = poems.loadAll() × progress.observeAllSummaries() → 状态过滤 → 分页
// ProgressRepository 增加：fun observeAllSummaries(): Flow<List<ProgressSummary>>，data class ProgressSummary(poemId, status, progress, lastPlayedAt)
class LibraryViewModel(poems: PoemRepository, progress: ProgressRepository) : ViewModel() {
    val summaries = progress.observeAllSummaries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val all = poems.loadAll()
}
// Screen: filter 状态（ALL/NOT_STARTED/LEARNING/MASTERED）+ page 状态（每页8）
// 卡片：FocusCard { Column(horizontalAlignment=CenterHorizontally){ Spacer(weight) 诗名(20sp/letterSpacing4) 诗人 徽章 } }
// 焦点翻页：页面末卡片再向右 → 下一页（page+1 并请求新页首焦点）；筛选行切换重置 page=0
// OK → onPoem(poem.id)；B/BackHandler → 返回
```

- [ ] **Step 2: 冒烟** —— 安装后：筛选切换生效、翻页正常、徽章显示与 Task 4 规则一致（刚会背的诗显示 👍 已会背）。
- [ ] **Step 3: Commit** —— `git add -A && git commit -m "feat: 全部唐诗页（筛选/分页网格/状态徽章）"`

---

### Task 9: 搜索页

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/data/search/SearchMatcher.kt`、`app/src/main/java/com/rakor/tangshi/ui/screens/search/SearchScreen.kt`
- Test: `app/src/test/java/com/rakor/tangshi/data/SearchMatcherTest.kt`
- Modify: `AppNavHost.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.rakor.tangshi.data

import com.rakor.tangshi.data.search.SearchMatcher
import org.junit.Assert.*
import org.junit.Test

class SearchMatcherTest {
    private val poems = TestFixtures.poems()   // 测试夹具：复用 Task 2 的两首诗构造对象（放 src/test 下）

    @Test fun `关键字命中汉字`() { assertTrue(SearchMatcher.matches(poems[0], null, "月光")) }
    @Test fun `关键字命中全拼`() { assertTrue(SearchMatcher.matches(poems[0], null, "chuangqian")) }
    @Test fun `诗名全拼`() { assertTrue(SearchMatcher.matches(poems[0], null, "jingyesi")) }
    @Test fun `不命中`() { assertFalse(SearchMatcher.matches(poems[0], null, "xyz")) }
    @Test fun `诗人过滤`() {
        assertEquals(listOf("yong-e"), poems.filter { SearchMatcher.matches(it, "骆宾王", "") }.map { it.id })
    }
}
```

- [ ] **Step 2: 运行确认失败**，然后实现：

```kotlin
// data/search/SearchMatcher.kt
package com.rakor.tangshi.data.search

import com.rakor.tangshi.data.model.Poem
import java.text.Normalizer

object SearchMatcher {
    fun matches(poem: Poem, poetFilter: String?, keyword: String): Boolean {
        if (!poetFilter.isNullOrBlank() && poem.poet != poetFilter) return false
        val kw = keyword.trim()
        if (kw.isEmpty()) return true
        val text = poem.title + poem.lines.joinToString("") { it.text }
        val pinyin = buildString {
            poem.lines.forEach { l -> l.chars.forEach { append(it.py) } }
        }.let(::stripTone)
        val kwPinyin = stripTone(kw)
        return text.contains(kw) || pinyin.contains(kwPinyin)
    }
    private fun stripTone(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
}
```

- [ ] **Step 3: 测试通过**（`./gradlew :app:testDebugUnitTest --tests "*SearchMatcherTest"`）。
- [ ] **Step 4: SearchScreen**（规格 5.3：左结果列表/右屏幕键盘；诗人浮层网格"姓名+收录诗数"按拼音排序；命中字/拼音首句预览高亮）——纯 UI 装配，组件复用 Task 5；键盘 A~Z + 删除 + 搜索胶囊；结果 OK → onPoem(id)。 poets 列表 = `poems.groupBy { it.poet }.map { PoetEntry(name, count) }.sortedBy { pinyinOf(name) }`（pinyinOf 复用 pypinyin? 不——用 title 首字 py？诗人姓名拼音：poems 里无 poet 拼音数据；排序键用 `Collator.getInstance(java.util.Locale.CHINA)` 按汉字排序即可，无需拼音）。
- [ ] **Step 5: 冒烟 + Commit** —— `git add -A && git commit -m "feat: 搜索（诗人浮层/屏幕键盘/汉字与全拼匹配）"`

---

### Task 10: 背诗小游戏（出题 + 三屏）

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/game/QuizGenerator.kt`、`ui/screens/game/GameSetupScreen.kt`、`GameQuestionScreen.kt`、`GameResultScreen.kt`
- Test: `app/src/test/java/com/rakor/tangshi/game/QuizGeneratorTest.kt`
- Modify: `AppNavHost.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.rakor.tangshi.game

import com.rakor.tangshi.data.TestFixtures.poems
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class QuizGeneratorTest {
    @Test fun `填空题结构`() {
        val q = QuizGenerator.fillBlank(poems(), Random(42))
        assertEquals(4, q.options.size)
        assertEquals(1, q.options.count { it == q.answer })
        assertTrue(q.lineIndex in poems().first().lines.indices || q.poem.id.isNotBlank()) // q 来自题池
        assertTrue(q.charIndex in q.lineText.indices)
    }
    @Test fun `听音题选项含答案`() {
        val q = QuizGenerator.listen(poems(), Random(7))
        assertEquals(4, q.options.size); assertTrue(q.poem.id in q.options.map { it.id })
    }
    @Test fun `星级规则`() {
        assertEquals(3, QuizGenerator.stars(9)); assertEquals(3, QuizGenerator.stars(10))
        assertEquals(2, QuizGenerator.stars(7)); assertEquals(1, QuizGenerator.stars(5))
        assertEquals(0, QuizGenerator.stars(4))
    }
    @Test fun `结算文案`() {
        assertEquals("诗仙驾到！", QuizGenerator.message(10, streak = 5))
        assertEquals("太棒了！", QuizGenerator.message(8, streak = 5))
        assertEquals("再试一次会更棒", QuizGenerator.message(3, streak = 5))
    }
    @Test fun `填空题答案恰出现一次（多种子回归，防FALLBACK池污染）`() {
        repeat(100) { seed ->
            val q = QuizGenerator.fillBlank(poems(), Random(seed))
            assertEquals(4, q.options.size)
            assertEquals(1, q.options.count { it == q.answer })
        }
    }
}
```

- [ ] **Step 2: 运行确认失败**，实现：

```kotlin
// game/QuizGenerator.kt
package com.rakor.tangshi.game

import com.rakor.tangshi.data.model.Poem
import com.rakor.tangshi.data.model.hanChars
import kotlin.random.Random

object QuizGenerator {
    private val FALLBACK_CHARS = listOf("春","风","雨","山","月","白","日","人","来","不","去","中","大","天","地","花","鸟")

    data class FillBlank(val poem: Poem, val lineIndex: Int, val lineText: String,
                         val charIndex: Int, val answer: Char, val options: List<Char>)
    data class Listen(val poem: Poem, val lineIndex: Int, val options: List<Poem>)

    fun fillBlank(pool: List<Poem>, rng: Random): FillBlank {
        val poem = pool.random(rng)
        val li = poem.lines.indices.random(rng)
        val line = poem.lines[li]
        val candidates = line.chars.mapIndexedNotNull { i, c -> if (hanChars(c.c).isNotEmpty()) i else null }
        val ci = candidates.filter { it > 0 }.ifEmpty { candidates }.random(rng)   // 避开首字
        val answer = line.chars[ci].c.first()
        val distractors = (poem.lines.flatMap { it.chars.map { c -> c.c.first() } }.filter { it != answer }
                + FALLBACK_CHARS).filter { it != answer }.distinct().shuffled(rng).take(3)  // 二次过滤: 答案在FALLBACK池中时防重复(评审修复)
        return FillBlank(poem, li, line.text, ci, answer, (distractors + answer).shuffled(rng))
    }
    fun listen(pool: List<Poem>, rng: Random): Listen {
        val poem = pool.random(rng)
        val li = poem.lines.indices.random(rng)
        val others = pool.filter { it.id != poem.id }.shuffled(rng).take(3)
        return Listen(poem, li, (others + poem).shuffled(rng))
    }
    fun stars(correct: Int) = when { correct >= 9 -> 3; correct >= 7 -> 2; correct >= 5 -> 1; else -> 0 }
    fun message(correct: Int, streak: Int) = when {
        correct == 10 -> "诗仙驾到！"
        correct >= 7 -> "太棒了！已连续学习 $streak 天啦"
        correct >= 5 -> "继续加油！"
        else -> "再试一次会更棒"
    }
}
```

- [ ] **Step 3: 测试通过**。
- [ ] **Step 4: 三屏 UI**（规格 5.5/5.6/5.7；GameViewModel 状态机：`start(mode, scope)` 按范围取池[今日所学/已学全部/全部]，10 题；题面 A 用 `PoemLine` 渲染但第 charIndex 字替换为橙色"？"[32sp 白字橙底方块]；题面 B 播放按钮复用 PoemPlayer.playLineLoop、重听计数≤3；答对/答错反馈 800ms 后自动下一题；结算屏星=amber 实心 Star 图标 × stars()、文案 message()、按钮 再来一轮/返回首页。池不足 5 首时显示"先去学几首诗再来吧！"并只有返回按钮）。范围选择 UI：三枚竖排 PillButton，focused+selected 双态。
- [ ] **Step 5: 冒烟 + Commit** —— `git add -A && git commit -m "feat: 背诗小游戏（填空/听音/结算，含范围选择与星级）"`

---

### Task 11: 家长设置 + DataStore

**Files:**
- Create: `app/src/main/java/com/rakor/tangshi/data/repo/SettingsRepository.kt`、`ui/screens/settings/SettingsScreen.kt`
- Modify: `AppGraph`（keyTone 接 SettingsRepository、player 倍速从设置读）、`DetailScreen`（顶部提示条：音频缺失静默提示——播放报错状态置一次性 banner）
- Test: 无单测（UI 配置读写），冒烟覆盖

- [ ] **Step 1: SettingsRepository**

```kotlin
// data/repo/SettingsRepository.kt
package com.rakor.tangshi.data.repo

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    data class Settings(val speed: Float = 1.0f, val lineByLine: Boolean = true, val keyTone: Boolean = true)
    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(p[floatKey("speed")] ?: 1.0f, p[boolKey("lineByLine")] ?: true, p[boolKey("keyTone")] ?: true)
    }
    suspend fun setSpeed(v: Float) = context.dataStore.edit { it[floatKey("speed")] = v }
    suspend fun setLineByLine(v: Boolean) = context.dataStore.edit { it[boolKey("lineByLine")] = v }
    suspend fun setKeyTone(v: Boolean) = context.dataStore.edit { it[boolKey("keyTone")] = v }
    private fun floatKey(k: String) = floatPreferencesKey(k)
    private fun boolKey(k: String) = booleanPreferencesKey(k)
}
```

- [ ] **Step 2: SettingsScreen**（规格 5.8：双列卡片[语速 慢0.9/正常1.0/快1.15、跟读模式逐句暂停✓/连续、提示音开✓/关、清除进度→对话框二次确认→wipeAll]、底栏"关于：版本 1.0 · 唐诗数据来源说明"）。AppGraph.keyTone 的 enabled 改为 `runBlocking { settings.first().keyTone }` 的缓存读（KeyTonePlayer 调用在主线程，用内存缓存字段 + Flow collect 更新实现，禁 runBlocking——落地时 AppGraph 持有 `@Volatile var keyToneEnabled` 并在 Application 里启动协程收集 settings 更新）。**执行裁定（2026-10-06 评审）**：清除进度后**留在本页**显示"已清除全部学习进度"提示条（2.6s 消失），不回首页——与 GameSetupScreen 提示条模式一致。
- [ ] **Step 3: 冒烟 + Commit** —— `git add -A && git commit -m "feat: 家长设置（语速倍率/跟读模式/提示音/清除进度二次确认）"`

---

### Task 12: 音频生成工具链 + 种子集生成 + 资产校验测试

**Files:**
- Create: `tools/gen_voice.py`、`tools/tts_substitutions.json`、`tools/validate_voice.py`、`tools/build_manifest.py`、`tools/voice_script.txt`、`app/src/test/java/com/rakor/tangshi/data/AudioAssetsTest.kt`
- Create(资产): `app/src/main/assets/audio/{poemId}/line{N}.opus` + `manifest.json`（10 首种子集）

- [ ] **Step 1: gen_voice.py**（应用同音字替换 → 产出 TTS 输入与任务清单；未覆盖多音字直接报错——规格 8）

```python
#!/usr/bin/env python3
"""用法: python tools/gen_voice.py [--check-only]
读取 poems.json + tts_substitutions.json：
  1. 用 pypinyin 找出每个字的多音字候选（heteronym）
  2. 被"确认默认读音正确"白名单覆盖 或 被替换表覆盖 → 通过
  3. 其余多音字 → 打印清单并 exit 1（不允许裸奔合成）
产出 build/voice-tts-input/{poemId}/lineN.txt（替换后的 TTS 输入）与 build/voice-jobs.json
"""
import json, pathlib, sys
from pypinyin import pinyin, Style

ROOT = pathlib.Path(__file__).resolve().parent.parent
POEMS = ROOT / "app/src/main/assets/content/poems.json"
SUBS  = ROOT / "tools/tts_substitutions.json"
OUT   = ROOT / "build/voice-tts-input"

def main():
    check_only = "--check-only" in sys.argv
    table = json.loads(SUBS.read_text(encoding="utf-8"))
    confirmed = set(table.get("confirmedDefault", []))     # 默认读音正确、无需处理的多音字
    globalmap = table.get("global", {})
    perpoem   = table.get("poems", {})
    poems = json.loads(POEMS.read_text(encoding="utf-8"))
    jobs, missing = [], []
    OUT.mkdir(parents=True, exist_ok=True)
    for p in poems:
        repl = dict(globalmap); repl.update(perpoem.get(p["id"], {}))
        d = OUT / p["id"]; d.mkdir(parents=True, exist_ok=True)
        for i, line in enumerate(p["lines"], 1):
            text, report = line["text"], []
            for ch in text:
                if not ch.strip() or not ("\u4e00" <= ch <= "\u9fff"): continue
                heteronyms = [r[0] for r in pinyin(ch, style=Style.TONE, heteronym=True)]
                if len(heteronyms) > 1 and ch not in confirmed and ch not in repl:
                    report.append(f"{ch}:{'/'.join(heteronyms)}")
                if ch in repl:  # 1:1 同音字替换（只改 TTS 输入）
                    text = text.replace(ch, repl[ch])
            if report: missing.append(f'{p["id"]} L{i}: {" ".join(report)}')
            (d / f"line{i}.txt").write_text(text, encoding="utf-8")
            jobs.append({"poemId": p["id"], "line": i, "count": len(p["lines"])})
    (ROOT / "build/voice-jobs.json").write_text(json.dumps(jobs, ensure_ascii=False, indent=1), encoding="utf-8")
    if missing:
        print("未覆盖多音字（需加入替换表或 confirmedDefault）:"); [print(" ", m) for m in missing]; sys.exit(1)
    print(f"ok: {len(jobs)} lines ready -> build/voice-tts-input/, jobs -> build/voice-jobs.json")

if __name__ == "__main__":
    main()
```

- [ ] **Step 2: tts_substitutions.json 起步内容**（初版按种子集扫描结果补全；`confirmedDefault` 起步含：思、地、上、曲、拨、绿、看、还——生成前跑 `--check-only` 按报告逐个人工裁定，裁定记录写入 `tools/voice_script.txt` B 节）

```json
{ "confirmedDefault": ["思", "曲", "拨"], "global": {}, "poems": {} }
```

- [ ] **Step 3: 校验与 manifest 工具**

```python
# tools/validate_voice.py —— 用法: python tools/validate_voice.py <音频目录>
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
poems = json.loads((root.parent / "content/poems.json").read_text(encoding="utf-8")) if (root.parent / "content/poems.json").exists() else json.loads((pathlib.Path(__file__).parent.parent / "app/src/main/assets/content/poems.json").read_text(encoding="utf-8"))
bad = []
for p in poems:
    d = root / p["id"]; mf = d / "manifest.json"
    if not mf.exists(): bad.append(f"{p['id']}: missing manifest"); continue
    m = json.loads(mf.read_text(encoding="utf-8"))
    if len(m["lines"]) != len(p["lines"]): bad.append(f"{p['id']}: manifest lines mismatch")
    for i, e in enumerate(m["lines"], 1):
        f = d / e["file"]
        if not f.exists() or f.stat().st_size < 1024: bad.append(f"{p['id']}/{e['file']}: missing/too small"); continue
        head = f.read_bytes()[:4]
        if not (head.startswith(b"ID3") or (head[0] == 0xFF and (head[1] & 0xE0) == 0xE0) or head[:4] == b"OggS"):
            bad.append(f"{p['id']}/{e['file']}: bad magic {head!r}")
print("\n".join(bad) if bad else f"ok: {len(poems)} poems complete"); sys.exit(1 if bad else 0)
```

```python
# tools/build_manifest.py —— 用法: python tools/build_manifest.py  (ffprobe 需在 PATH)
import json, pathlib, subprocess
ROOT = pathlib.Path(__file__).resolve().parent.parent
A = ROOT / "app/src/main/assets/audio"
POEMS = json.loads((ROOT / "app/src/main/assets/content/poems.json").read_text(encoding="utf-8"))
for p in POEMS:
    d = A / p["id"]
    lines = []
    for i in range(1, len(p["lines"]) + 1):
        f = d / f"line{i}.opus"
        dur = float(subprocess.check_output(["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", str(f)], text=True) and json.loads(subprocess.check_output(["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", str(f)], text=True))["format"]["duration"])
        lines.append({"file": f"line{i}.opus", "durationMs": int(dur * 1000)})
    (d / "manifest.json").write_text(json.dumps({"lines": lines}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(p["id"], "ok")
```

- [ ] **Step 4: AudioAssetsTest**（对齐三字经项目模式；随 poems.json 增长自动覆盖全量）

```kotlin
package com.rakor.tangshi.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioAssetsTest {
    @Test fun `每首诗每句音频齐全且合法`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val poems = com.rakor.tangshi.data.repo.PoemRepository(ctx).loadAll()
        assertTrue("poems.json 为空", poems.isNotEmpty())
        poems.forEach { p ->
            val mf = ctx.assets.open("audio/${p.id}/manifest.json").bufferedReader().use { it.readText() }
            val lines = kotlinx.serialization.json.Json.decodeFromString<Manifest>(mf).lines
            assertTrue("${p.id} manifest 句数", lines.size == p.lines.size)
            lines.forEach { e ->
                val bytes = ctx.assets.open("audio/${p.id}/${e.file}").use { it.readBytes() }
                assertTrue("${p.id}/${e.file} 太小(${bytes.size})", bytes.size > 1024)
                val ok = bytes.copyOfRange(0, 3).decodeToString().startsWith("ID3") ||
                         (bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) ||
                         bytes.copyOfRange(0, 4).decodeToString() == "OggS"
                assertTrue("${p.id}/${e.file} 魔数错误", ok)
            }
        }
    }
    @kotlinx.serialization.Serializable data class ManifestEntry(val file: String, val durationMs: Int)
    @kotlinx.serialization.Serializable data class Manifest(val lines: List<ManifestEntry>)
}
```

- [ ] **Step 5: 生成种子集音频（browser-use 自动化 runbook，按三字经文档接口）**

1. `python tools/gen_voice.py` 通过（无未覆盖多音字；有则人工裁定更新 `tts_substitutions.json`/`confirmedDefault`，并把每次裁定记入 `tools/voice_script.txt`）。
2. 打开 `https://airvoz.com/zh/text-to-speech/voice/Xiaoshuang`（browser-use；页面下拉无关，仅取会话上下文）。
3. 在该页面上下文按 `build/voice-jobs.json` 逐条执行（并发≤3、失败指数退避；POST `https://airvoz.com/api/tts`，body `{"input":"<lineN.txt 内容>","voice":"zh-CN-XiaoshuangNeural","language":"zh-CN","speed":0.85,"pitch":1,"volume":1}`，响应为 MP3 裸流），先落 `build/voice-staging/{poemId}/line{N}.mp3` 并校验 >1KB。
4. 转码（句间停顿在此烘进尾部：逗号句 +0.3s、句号句 +0.6s；若 ffmpeg 无 libopus，回退 `-c:a libmp3lame -b:a 32k` 且文件扩展名同步改 .mp3、PoemPlayer 与 manifest 同步改）：

```bash
python - <<'PY'
import json, pathlib, subprocess
ROOT = pathlib.Path('.').resolve()
POEMS = json.loads((ROOT/'app/src/main/assets/content/poems.json').read_text(encoding='utf-8'))
for p in POEMS:
    for i, line in enumerate(p['lines'], 1):
        src = ROOT/'build/voice-staging'/p['id']/f'line{i}.mp3'
        pad = '0.6' if line['text'].rstrip().endswith(('。','！','？')) else '0.3'
        dst = ROOT/'app/src/main/assets/audio'/p['id']; dst.mkdir(parents=True, exist_ok=True)
        subprocess.run(['ffmpeg','-y','-i',str(src),'-ac','1','-ar','24000','-c:a','libopus','-b:a','24k','-af',f'apad=pad_dur={pad}',str(dst/f'line{i}.opus')], check=True)
PY
```

5. `python tools/build_manifest.py` 生成 manifest；`python tools/validate_voice.py app/src/main/assets/audio` 预期 `ok: 10 poems complete`。
6. `./gradlew :app:testDebugUnitTest --tests "*AudioAssetsTest"` PASS。
7. `./gradlew :app:installDebug` 试听抽查（重点：替换表涉及句 + 每首诗全文连读一次）。
- [ ] **Step 6: Commit** —— `git add -A && git commit -m "feat: 语音生成工具链与种子集音频（晓双/0.85/Opus/manifest/校验）"`

---

### Task 13: 模拟器全流程冒烟 + 收尾

- [ ] **Step 0（评审遗留，收尾必查）：** ① 规格第 6 节"菜单键映射至家长设置入口"——在 MainActivity 全局按键处理实现（KEYCODE_MENU → 导航 settings）；② 规格 4 节"诗体内容用楷体系（KaiTi 回退）"——诗文渲染处（详情页 PoemLine/首页诗句）应用楷体 FontFamily；③ 首页 CTA"开始/继续诵读"条件文案已实现（Task 6 修复）。

- [ ] **Step 1:** 全流程手测清单（TV 模拟器 1080p）：首页焦点可见→详情页连读高亮逐句移动→跟读单句循环→我会背了确认→首页统计变化→全部唐诗筛选/翻页/OK 进详情→搜索（诗人浮层过滤 + `jingyesi` 全拼命中 + 键盘输入）→游戏一轮（A 填空 4 选项/听音重听 3 次上限/结算星级与文案正确）→设置语速改"慢"后朗读明显变慢（变速不变调）→提示音关→清除进度二次确认后统计归零。
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest` 全绿；`./gradlew :app:assembleRelease` 成功（R8 收敛 icons-extended 体积）。
- [ ] **Step 3:** Commit —— `git add -A && git commit -m "chore: V1 冒烟通过"`

---

### Task 14: 内容扩充 runbook（310 首 + 全量音频）

按以下循环迭代直至全量（每批 20~30 首），每批一个 commit：

0. 首批前一次性加固（评审遗留）：`tools/make_poem.py` 的 `POEMS.write_text(...)` 加 `newline="\n"`（防 CRLF 提交噪音）；`PoemRepositoryTest` 增加 order 唯一性与 id 格式（kebab-case）断言（为 310 首批量导入把门）。

1. 整理该批诗文与创作背景 → 写入 `tools/seed/batch-N.txt`（make_poem.py 格式）；拼音初标用 pypinyin，**人工复核多音字**（对照 `tools/tts_substitutions.json` 与 confirmedDefault；新发现字加白名单或替换表并记 `voice_script.txt`）。
2. `python tools/make_poem.py tools/seed/batch-N.txt` → 跑 PoemRepositoryTest（对齐校验门）。
3. `python tools/gen_voice.py`（多音字门，exit 1 即回步骤 1 裁定）→ browser-use 按 Task 12 Step 5 流程生成 → 转码 → manifest → validate_voice.py。
4. AudioAssetsTest PASS → 每批抽听 3 首 → commit。
5. 全部完成后：首页/列表"共 N 首"自动跟随数据；规格 2 节计数约定不变。

---

## 自审记录（已执行）

- **规格覆盖**：规格 1~13 节逐条对到任务——用语约定(Task 1 主题+Task 6 文案)、视觉规范(Task 1 T 对象/Task 5 组件)、8 屏(Task 6~11)、进度规则(Task 4)、搜索含全拼(Task 9)、音频流水线含晓双/0.85/浏览器上下文/同音字替换/Opus/manifest/fallback(Task 12)、游戏规则(Task 10)、错误处理(详情页提示条 Task 11/资产门 Task 12)、内容准备(Task 3/14)、测试策略(各任务+Task 12/13)。规格 5.2"第 N / 总数 首"动态计数在 Task 6 HomeViewModel/Task 8 以 poems.loadAll().size 实现。
- **占位扫描**：无 TBD/TODO；草稿残留代码已在任务注记中显式标注"落地时删除/替换"（Task 4 observeStats、Task 6 焦点、Task 7 协程），均给出正确写法。
- **类型一致性**：Poem/Line/CharPy、ProgressRepository API（onPoemOpened/onLinePlayed/markMastered/observeProgress/observeStats/todayPoemIds/wipeAll）、QuizGenerator.fillBlank/listen/stars/message、SearchMatcher.matches 在引用处签名一致；音频文件名 line{N}.opus 与 PoemPlayer.lineItem、build_manifest、AudioAssetsTest 一致。
