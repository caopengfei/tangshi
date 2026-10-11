#!/usr/bin/env python3
"""tools/audit_char.py —— 同音异调高危字的专项审计

为什么需要它：jìn/jǐn、láng/lǎng、mò/wù 这类「同音异调」的多音字，
（a）pypinyin 词组词典覆盖不全，倾向漏判；（b）逐字裁定 agent 只看得到自己
chunk 里的 95 字，看不到全语料的分布，无法察觉「某个字几乎全错」这种系统性偏差。

本脚本对指定字导出全语料读音分布 + 每处上下文，供人工一次性裁定；
裁定结果写入 tools/polyphone_overrides.json。

用法：python tools/audit_char.py 尽 没 干
"""
import io
import json
import pathlib
import sys
from collections import Counter

from pypinyin import Style, pinyin

ROOT = pathlib.Path(__file__).resolve().parent.parent
SK = ROOT / "build/corpus-skeleton.json"
OUTDIR = ROOT / "build/audit"


def main() -> int:
    chars = sys.argv[1:]
    if not chars:
        sys.exit(__name__ and "用法: python tools/audit_char.py <字> [字…]")
    poems = json.loads(SK.read_text(encoding="utf-8"))
    OUTDIR.mkdir(parents=True, exist_ok=True)

    for ch in chars:
        rows = []
        for p in poems:
            for i, line in enumerate(p["lines"], 1):
                cs = [c["c"] for c in line["chars"]]
                ps = [c["py"] for c in line["chars"]]
                for j, c in enumerate(cs):
                    if c == ch:
                        ctx = "".join(cs[max(0, j - 3):j + 4])
                        rows.append((ps[j], ctx, p["title"]))
        cands = pinyin(ch, style=Style.TONE, heteronym=True)[0]
        dist = Counter(r[0] for r in rows)

        out = io.open(OUTDIR / f"{ch}.txt", "w", encoding="utf-8")
        out.write(f"# 「{ch}」共 {len(rows)} 处，候选 {'/'.join(cands)}\n")
        out.write(f"# 现读分布: {dict(dist)}\n\n")
        for py in sorted(dist, key=lambda k: -dist[k]):
            out.write(f"## 现读 {py}（{dist[py]} 处）\n")
            for got, ctx, title in rows:
                if got == py:
                    out.write(f"    {ctx}   《{title}》\n")
            out.write("\n")
        out.close()
        print(f"{ch}: {len(rows)} 处, 分布 {dict(dist)} -> build/audit/{ch}.txt")
    return 0


if __name__ == "__main__":
    sys.exit(main())