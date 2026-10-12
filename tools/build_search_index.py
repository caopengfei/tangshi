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
    """与运行时 SearchMatcher.stripTone 同口径：NFD 去变音符 + 小写（lü→lu）。"""
    decomposed = unicodedata.normalize("NFD", s)
    return "".join(c for c in decomposed if not unicodedata.combining(c)).lower()


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
