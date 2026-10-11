#!/usr/bin/env python3
"""tools/validate_verdicts.py —— 校验各 chunk 的多音字裁定结果，生成合并后的覆盖表

对 build/adjudicate/chunk-*.json 逐条校验：
  1. phrase 必须真实出现在语料中（否则是臆造，剔除）；
  2. phrase 必须含被判定的那个字；
  3. 拼音个数必须等于 phrase 汉字数；
  4. 「修正」必须真的与现读不同（相同则剔除，多半是误报）；
  5. 修正后的读音必须在 pypinyin 的候选读音之内（防止生造音）。

通过后合并进 tools/polyphone_overrides.json 的 phrases（已有条目保持不变）。
"""
import json
import pathlib
import re
import sys

from pypinyin import Style, pinyin

ROOT = pathlib.Path(__file__).resolve().parent.parent
SK = ROOT / "build/corpus-skeleton.json"
ADJ = ROOT / "build/adjudicate"
TABLE = ROOT / "tools/polyphone_overrides.json"

HAN = re.compile(r"[\u4e00-\u9fff]")


def corpus_joined(sk: list) -> set:
    """全语料的句文本集合 + 全语料汉字串（用于 phrase 存在性检查）。"""
    lines = {l["text"] for p in sk for l in p["lines"]}
    joined = {l["text"] for p in sk for l in p["lines"]}
    return lines, joined


def main() -> int:
    sk = json.loads(SK.read_text(encoding="utf-8"))
    table = json.loads(TABLE.read_text(encoding="utf-8"))
    phrases: dict[str, str] = table.setdefault("phrases", {})

    # 现读快照：phrase+offset -> 当前拼音串
    han_re = re.compile(r"[\u4e00-\u9fff]")
    current: dict[str, list[str]] = {}
    for p in sk:
        for line in p["lines"]:
            han = han_re.findall(line["text"])
            pys = [c["py"] for c in line["chars"]]
            for i in range(len(han) - 1):
                for n in (2, 3, 4):
                    if i + n <= len(han):
                        current.setdefault("".join(han[i:i + n]), []).append(" ".join(pys[i:i + n]))

    stats = {"kept": 0, "dup": 0, "notfound": 0, "nochar": 0, "lenmismatch": 0,
             "noop": 0, "badcand": 0, "already": 0}
    rejected: list[str] = []
    for f in sorted(ADJ.glob("chunk-*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        for v in data.get("verdicts", []):
            ch = v.get("char")
            for fix in v.get("fixes", []):
                phrase, py = fix.get("phrase", ""), fix.get("py", "")
                tag = f"{ch} {phrase}->{py}"
                han_only = han_re.findall(phrase)
                if "".join(han_only) != phrase or len(han_only) < 2:
                    rejected.append(f"[非纯汉字或少于2字] {tag}")
                    stats["notfound"] += 1
                    continue
                if ch not in phrase:
                    rejected.append(f"[phrase 不含该字] {tag}"); stats["nochar"] += 1; continue
                if phrase not in current:
                    rejected.append(f"[语料中不存在] {tag}"); stats["notfound"] += 1; continue
                want = py.split()
                if len(want) != len(phrase):
                    rejected.append(f"[拼音数不符] {tag}"); stats["lenmismatch"] += 1; continue
                if py in current[phrase]:
                    rejected.append(f"[与现读相同] {tag}"); stats["noop"] += 1; continue
                # 每个字的读音须在候选之内
                ok = True
                for c, got in zip(phrase, want):
                    if HAN.match(c) and c != "一":
                        cand = pinyin(c, style=Style.TONE, heteronym=True)[0]
                        if len(cand) > 1 and got not in cand:
                            rejected.append(f"[读音不在候选内] {tag} 字{c}={got} 候选{cand}")
                            ok = False
                            break
                if not ok:
                    stats["badcand"] += 1
                    continue
                if phrase in phrases:
                    stats["dup"] += 1
                    if phrases[phrase] == py:
                        stats["already"] += 1
                    else:
                        rejected.append(f"[冲突，已存在不同值] {phrase}: {phrases[phrase]} vs {py}")
                        stats["dup"] += 1
                    continue
                phrases[phrase] = py
                stats["kept"] += 1

    TABLE.write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n",
                     encoding="utf-8", newline="\n")
    print(f"采纳 {stats['kept']} 条，剔除 {sum(v for k,v in stats.items() if k not in ('kept','already'))} 条")
    print("  剔除明细:", {k: v for k, v in stats.items() if k not in ("kept",)})
    for r in rejected:
        print("   ", r)
    print(f"-> {TABLE} 共 {len(phrases)} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main())