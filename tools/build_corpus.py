#!/usr/bin/env python3
"""tools/build_corpus.py —— 唐诗三百首原始数据 → 规范化语料骨架

输入：tools/source/tangshi300.json（chinese-poetry，MIT，繁体，含「一作：」等异文注记）
输出：build/corpus-skeleton.json（每首含 id/order/title/poet/dynasty/lines（汉字+拼音），background/meaning 留空）

清洗步骤（顺序不可调换）：
  1. 合并 paragraphs 为整首文本 —— 异文注记会跨行折断（夜上受降城闻笛），必须先合并再剥离；
  2. 剥离括号注记：(…)（…），含全角半角、可嵌套、含换行；
  3. 去空格、统一标点为全角（， 。 ！ ？ ；），书名号《》保留；
  4. 繁体 → 简体（zhconv，句级一次性转换，避免逐字转换破坏词组）；
  5. 按标点切分为「句」——app 的 lines 粒度是一句一屏，与 10 首种子诗一致；
  6. lazy_pinyin 出带调拼音 →「一」「不」变调 → 同音异调字整字兜底（apply_char_defaults）
     → 应用 tools/polyphone_overrides.json 的词组级人工裁定（优先级最高）；
  7. 标题 → id 用标题逐字拼音 kebab 化，tools/id_overrides.json 可锁定人工 id。

只做确定性工作。background/meaning 由 tools/merge_poems.py 从 authored 数据合入，
本脚本不生成任何散文内容——避免模板化背景故事混进 app。
"""
import json
import pathlib
import re
import sys
import unicodedata

from pypinyin import Style, lazy_pinyin, pinyin
from zhconv import convert as zh_convert

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/source/tangshi300.json"
OVERRIDES = ROOT / "tools/polyphone_overrides.json"
OUT = ROOT / "build/corpus-skeleton.json"

import unicodedata

# 与 Poem.kt 的 hanChars 保持完全一致的判定（category == OTHER_LETTER）。
# 不能用 [一-鿿] 之类的基本区范围——《韩碑》的「䝙」（U+4759，CJK 扩展 A）、
# 《丹青引》的「𡒄」（U+214B2，扩展 B）都在基本区外，写范围会漏，必须逐字符判 category。
def is_han(c: str) -> bool:
    return unicodedata.category(c) == "Lo"
# 带调字符 -> 声调序号（0 轻声/无调）
TONE_MARKS = {c: i + 1 for i, chars in enumerate(
    ["āōēīūǖ", "áóéíóú", "ǎǒěǐǔ", "àòèìòù"]) for c in chars}


def tone_of(py: str) -> int:
    """取拼音的声调序号（扫描带调元音，如 céng→2），无调号（轻声）返回 0。"""
    for ch in py:
        if ch in TONE_MARKS:
            return TONE_MARKS[ch]
    return 0


# 「一」变调：后字 1/2/3 声读 yì，4 声读 yí，句末/轻声读 yī（标准普通话规则）。
# pypinyin 对此 inconsistently 实现（一直/一起/一点 已调，更上一层楼/一夜/一片 却漏调），
# 且非调性结果与既有种子诗冲突（种子作 yì），故在数据层显式实现，不依赖库行为。
# 下列成词与序数读本调 yī（其中 一起/一向/一齐 等虽为 3 声后字，习惯读 yī）。
YI_NEUTRAL = {"第一", "唯一", "万一", "三更", "一起", "一向", "一齐", "一丝",
              "一旦", "一身", "一生", "一切", "一贯", "一片"}
ANNOTATION = re.compile(r"[（(][^（()）]*[）)]")
PUNCT_MAP = {"，": "，", ",": "，", "。": "。", ".": "。", "!": "！", "?": "？",
             ";": "；", ":": "：", "、": "、", "…": "……"}
SENTENCE_END = "，。！？；"
# 通行本原书中的长标题在源数据里被截断，此处按通行本正名
TITLE_FIX = {
    "至德二載甫自京金光門": "至德二载甫自京金光门出间道归凤翔乾元初至耆德小像夷陵三峡之作",
    "賦得古原草送別": "赋得古原草送别",
    "夜上受降城聞笛": "夜上受降城闻笛",
}
# 括号内是注释而非诗题的组，需从标题中去掉
TITLE_STRIP = re.compile(r"[（(][^（()）]*[）)]")


def clean_text(poem_title: str, paragraphs: list[str]) -> str:
    text = "\n".join(paragraphs)
    prev = None
    while prev != text:            # 嵌套注记需反复剥离
        prev = text
        text = ANNOTATION.sub("", text)
    text = "".join(PUNCT_MAP.get(ch, ch) for ch in text)
    text = re.sub(r"[ \t\r\n]+", "", text)
    text = unicodedata.normalize("NFC", text)
    return zh_convert(text, "zh-cn")


def split_sentences(text: str) -> list[str]:
    """按句读切句：标点归属前一句，保留标点（app 逐句高亮/音频以句为单位）。"""
    out, buf = [], ""
    for ch in text:
        buf += ch
        if ch in SENTENCE_END:
            out.append(buf)
            buf = ""
    if buf:
        out.append(buf if buf[-1] in SENTENCE_END else buf + "。")
    return [s for s in out if any(is_han(c) for c in s)]


def apply_bu_sandhi(han: list[str], pys: list[str]) -> None:
    """就地修正「不」的变调：后字为四声读 bú，其余读 bù（标准普通话规则）。

    与「一」同理：pypinyin 只在部分词上生效（220 处仅 32 处正确），
    不显式实现就会漏掉近一半。对四声字的分片裁定互相矛盾时以此为准。
    """
    for j, c in enumerate(han):
        if c != "不":
            continue
        nxt = tone_of(pys[j + 1]) if j + 1 < len(pys) else 0
        pys[j] = "bú" if nxt == 4 else "bù"


def apply_char_defaults(han: list[str], pys: list[str], char_defaults: dict) -> None:
    """整字级强制读音。

    与词组级覆盖的分工：词组级穷举「不尽/吹不尽/金散尽…全是 jìn」不现实，
    而同音异调字（jìn/jǐn、mò/wù、láng/lǎng）pypinyin 词组词典又覆盖不全、
    倾向漏判。对这类字用整字默认读音兜底，再由词组级覆盖为少数例外开口子。
    依据：tools/audit_char.py 导出的全语料读音分布逐处核验。
    """
    for j, c in enumerate(han):
        if c in char_defaults:
            pys[j] = char_defaults[c]


def line_pinyin(sentence: str, overrides: dict, char_defaults: dict) -> list[dict]:
    """逐字拼音。lazy_pinyin 相比 pinyin() 会做一变调（「更上一层楼」→ yì），
    与既有 10 首种子诗的人工标注一致，故用前者。"""
    han = [c for c in sentence if is_han(c)]
    joined = "".join(han)
    pys = lazy_pinyin(joined, style=Style.TONE)
    apply_yi_sandhi(han, pys)
    apply_bu_sandhi(han, pys)
    apply_char_defaults(han, pys, char_defaults)
    # 词组级人工裁定覆盖：长词优先，命中的每个出现位置都替换（优先级最高，压过上面一切）
    for phrase in sorted(overrides, key=len, reverse=True):
        want = overrides[phrase].split()
        if len(want) != len(phrase):
            raise SystemExit(f"覆盖表长度不符: {phrase} -> {overrides[phrase]}")
        start = 0
        while (idx := joined.find(phrase, start)) >= 0:
            pys[idx:idx + len(phrase)] = want
            start = idx + len(phrase)
    assert len(han) == len(pys), f"拼音数不符: {sentence}"
    return [{"c": c, "py": p} for c, p in zip(han, pys)]


def apply_yi_sandhi(han: list[str], pys: list[str]) -> None:
    """就地修正「一」的读音（标准普通话变调）。非调性的显式裁定优先，故在其后调用。"""
    for j, c in enumerate(han):
        if c != "一":
            continue
        two = "".join(han[j:j + 2])
        if two in YI_NEUTRAL:
            continue
        nxt = tone_of(pys[j + 1]) if j + 1 < len(pys) else 0
        if nxt == 4:
            pys[j] = "yí"
        elif nxt in (1, 2, 3):
            pys[j] = "yì"


def kebab(title: str) -> str:
    base = TITLE_STRIP.sub("", title).strip("《》 ")
    han = "".join(c for c in base if is_han(c))
    digits = "".join(c for c in base if c.isdigit())
    slug = "-".join(p[0] for p in pinyin(han, style=Style.NORMAL, heteronym=False))
    if digits:
        slug = f"{slug}-{digits}"
    return slug or "shi"


def main():
    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    overrides, char_defaults = {}, {}
    if OVERRIDES.exists():
        tbl = json.loads(OVERRIDES.read_text(encoding="utf-8"))
        overrides = tbl.get("phrases", {})
        char_defaults = tbl.get("charDefaults", {})

    seed_path = ROOT / "app/src/main/assets/content/poems.json"
    seed_poems = json.loads(seed_path.read_text(encoding="utf-8"))
    existing = {p["id"] for p in seed_poems}
    next_order = max(p["order"] for p in seed_poems) + 1
    # 种子诗 id 为人工拟定，与标题逐字拼音推导结果不一致的，按种子为准（音频资产与学习进度已绑定）
    id_map = json.loads((ROOT / "tools/id_overrides.json").read_text(encoding="utf-8"))
    id_map = id_map.get("titles", {})

    poems, used_ids, skipped = [], {}, []
    for group in data["content"]:
        form = group["type"]
        for item in group["content"]:
            title = TITLE_FIX.get(item["chapter"], item["chapter"])
            title = zh_convert(title, "zh-cn")
            lines = split_sentences(clean_text(item["chapter"], item["paragraphs"]))
            if not lines:
                skipped.append((title, "无有效诗句"))
                continue
            pid = id_map.get(title) or kebab(title)
            if pid in used_ids:
                n = used_ids[pid] + 1
                used_ids[pid] = n
                pid = f"{pid}-{n}"
            else:
                used_ids[pid] = 1
            poems.append({
                "id": pid, "order": next_order, "form": form,
                "title": title, "poet": zh_convert(item["author"], "zh-cn"),
                "dynasty": "唐",
                "lines": [{"text": s, "chars": line_pinyin(s, overrides, char_defaults)}
                          for s in lines],
                "background": "", "meaning": "",
                "_inSeed": pid in existing,
            })
            next_order += 1

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(poems, ensure_ascii=False, indent=1), encoding="utf-8")

    total_lines = sum(len(p["lines"]) for p in poems)
    print(f"poems={len(poems)} lines={total_lines} skipped={len(skipped)}")
    print(f"id clashes with seed set: {sorted(p['id'] for p in poems if p['_inSeed'])}")
    for form in dict.fromkeys(p["form"] for p in poems):
        n = sum(1 for p in poems if p["form"] == form)
        print(f"  {form}: {n}")
    for t, why in skipped:
        print(f"  SKIP {t}: {why}")
    return 0


if __name__ == "__main__":
    sys.exit(main())