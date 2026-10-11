#!/usr/bin/env python3
"""tools/review_polyphones.py —— 多音字人工校对清单

用法: python tools/review_polyphones.py [--only <poemId>] [--ctx]
输出 build/polyphone-review.txt：逐条列出每个多音字在句中的实际语境与候选读音，供人工裁定。
裁定结果写入 tools/polyphone_overrides.json 的 phrases（词组级，1 优先于逐字）。

为什么需要门：pypinyin 的逐字默认读音在古诗语境下会误判（实测「白日依山尽」→jǐn、
「枫桥夜泊」→pō），而 chars.py 是 app 拼音标注与 TTS 发音的唯一权威（规格 7.1/8），
错一个音就是孩子跟读学错。规格 8 要求多音字必须人工确认，不允许裸奔。
"""
import json
import pathlib
import re
import sys

from pypinyin import Style, pinyin

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKELETON = ROOT / "build/corpus-skeleton.json"
OUT = ROOT / "build/polyphone-review.txt"

HAN = re.compile(r"[\u4e00-\u9fff]")


def write_by_char(poems: list) -> None:
    """按字聚合的裁定稿：一个字的所有语境与现读列在一处，便于一次判定。
    逐条稿（按行）便于定位，逐字稿（按字）便于裁定，两者互补。"""
    import io
    agg: dict[str, dict[str, list]] = {}
    for p in poems:
        for line in p["lines"]:
            chars = [c["c"] for c in line["chars"]]
            for j, cd in enumerate(line["chars"]):
                ch = cd["c"]
                if ch == "一" or len(pinyin(ch, style=Style.TONE, heteronym=True)[0]) <= 1:
                    continue
                lo, hi = max(0, j - 3), min(len(chars), j + 4)
                agg.setdefault(ch, {}).setdefault(cd["py"], []).append(
                    ("".join(chars[lo:hi]), p["title"]))

    out = io.open(ROOT / "build/polyphone-by-char.txt", "w", encoding="utf-8")
    out.write(f"# 多音字裁定稿：共 {len(agg)} 字，按出现次数降序\n")
    out.write("# 每项：候选读音 / 各读音的全部语境（去重，最多 8 例）\n")
    for ch in sorted(agg, key=lambda c: -sum(len(v) for v in agg[c].values())):
        cands = pinyin(ch, style=Style.TONE, heteronym=True)[0]
        total = sum(len(v) for v in agg[ch].values())
        out.write(f"\n## {ch}  n={total}  候选:{'/'.join(cands)}\n")
        for py, items in agg[ch].items():
            out.write(f"  现读 {py} ({len(items)}处): ")
            seen = list(dict.fromkeys(c for c, _ in items))[:8]
            out.write(" | ".join(seen))
            out.write("\n")
    out.close()
    print(f"  -> build/polyphone-by-char.txt ({len(agg)} 字)")


def main():
    args = set(sys.argv[1:])
    poems = json.loads(SKELETON.read_text(encoding="utf-8"))
    if "--only" in args:
        want = args[list(args.index("--only")) + 1]
        poems = [p for p in poems if p["id"] == want]

    rows, seen = [], {}
    for p in poems:
        for i, line in enumerate(p["lines"], 1):
            for j, cd in enumerate(line["chars"]):
                ch = cd["c"]
                cands = pinyin(ch, style=Style.TONE, heteronym=True)[0]
                if len(cands) <= 1:
                    continue
                got = cd["py"]
                # 「一」由 apply_yi_sandhi 显式处理，其候选含本调 yī，无需人工裁定
                if ch == "一":
                    continue
                # 词组语境：取同句中该字前后各 2 字
                chars = [x["c"] for x in line["chars"]]
                lo, hi = max(0, j - 2), min(len(chars), j + 3)
                ctx = "".join(chars[lo:hi])
                rows.append((p["id"], p["title"], i, ctx, ch, got, cands))
                seen.setdefault((ch, got), []).append(f'{p["id"]}:L{i}')

    lines_out = [f"# 多音字校对清单  共 {len(rows)} 处 / {len(seen)} 个 (字,读音) 组合",
                 "# 裁定后写入 tools/polyphone_overrides.json 的 phrases（词组级优先）", ""]
    # 按字聚合，便于一次性裁定同一个字的不同语境
    by_char = {}
    for row in rows:
        by_char.setdefault(row[4], []).append(row)
    for ch, items in sorted(by_char.items(), key=lambda kv: -len(kv[1])):
        variants = sorted({(r[5], tuple(r[6])) for r in items})
        lines_out.append(f'== {ch}  ({len(items)} 处)  候选: {"/".join(v for v, _ in variants)}')
        for r in items[:6]:
            lines_out.append(f'   {r[1]} L{r[2]}  …{r[3]}…  现读 {r[5]}')
        if len(items) > 6:
            lines_out.append(f"   …另有 {len(items) - 6} 处")
        lines_out.append("")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines_out), encoding="utf-8")
    print(f"{len(rows)} 处多音字, {len(seen)} 个组合 -> {OUT}")
    write_by_char(poems)


if __name__ == "__main__":
    main()