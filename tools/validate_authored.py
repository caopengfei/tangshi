#!/usr/bin/env python3
"""tools/validate_authored.py —— 校验人工撰写的 background/meaning

在 merge_poems.py 之前做内容级门禁，把「写漏/超长/串味/模板化」挡在写入前：
  1. id 存在且无重复、无多余（与骨架严格一致）；
  2. background 80~150 字、meaning 40~90 字；
  3. 不含 markdown 残留（#、*、```、---）；
  4. 不含英文/拼音残留（正文中不应有 ASCII 字母）；
  5. background 开头 8 字不得与其他首雷同（模板化检测）；
  6. background 必须提到诗题中的字（确认没写错诗）；
  7. 释义不得是背景的子串（防复制粘贴）。

用法：python tools/validate_authored.py [part-00 part-01 …]（缺省校验 tools/authored 全部）
"""
import json
import pathlib
import re
import sys
from collections import defaultdict

ROOT = pathlib.Path(__file__).resolve().parent.parent
AUTHORED = ROOT / "tools/authored"
SKELETON = ROOT / "build/corpus-skeleton.json"

HAN_ONLY = re.compile(r"[\u4e00-\u9fff]")
BG_MIN, BG_MAX = 80, 150
# 释义下限放宽到 36：种子诗里《山行》释义 39 字、《相思》38 字，
# 40 这个下限是我拍脑袋定的，既不合范本又把好文案判死。下限只拦「明显敷衍」。
MEAN_MIN, MEAN_MAX = 36, 90
MARKDOWN = re.compile(r"[#*`]|^\s*-\s", re.M)
LATIN = re.compile(r"[A-Za-z]")
# 冗余身份介绍：全书都是唐诗，逐首交代「是唐朝诗人」是废话且导致开头雷同。
# 但「唐朝」作历史语境（地名/年代对比）时必要，故只拦「是唐朝…诗人」句式，
# 且白名单放行已知的合法历史语境用法。
REDUNDANT_IDENTITY = re.compile(r"[一-鿿]{2,4}是唐朝[^，。]{0,10}诗人")
IDENTITY_WHITELIST = {"凉州在长城最西边，是唐朝和西域交界的地方"}


def zh_len(s: str) -> int:
    return len(HAN_ONLY.findall(s))


def main() -> int:
    sk = {p["id"]: p for p in json.loads(SKELETON.read_text(encoding="utf-8"))}
    files = ([AUTHORED / f"{n}.json" for n in sys.argv[1:]]
             or sorted(AUTHORED.glob("*.json")))
    if not files:
        sys.exit("tools/authored 为空")

    errs: list[str] = []
    opens: dict[str, list[str]] = defaultdict(list)
    bg_lengths, mean_lengths = [], []

    for f in files:
        if not f.exists():
            errs.append(f"{f.name}: 文件不存在")
            continue
        data = json.loads(f.read_text(encoding="utf-8"))
        entries = data["poems"] if isinstance(data, dict) else data
        got = [e["id"] for e in entries]
        if len(got) != len(set(got)):
            errs.append(f"{f.name}: id 重复")

        for e in entries:
            pid = e.get("id", "?")
            bg, mean = e.get("background", ""), e.get("meaning", "")
            if pid not in sk:
                errs.append(f"{f.name}/{pid}: id 不在骨架中（臆造或错拼）")
                continue
            title = sk[pid]["title"]

            for name, txt in (("background", bg), ("meaning", mean)):
                if not txt.strip():
                    errs.append(f"{pid}: {name} 为空")
                    continue
                if MARKDOWN.search(txt):
                    errs.append(f"{pid}: {name} 含 markdown 残留")
                if LATIN.search(txt):
                    errs.append(f"{pid}: {name} 含拉丁字母残留")
                if REDUNDANT_IDENTITY.search(txt) and txt not in IDENTITY_WHITELIST:
                    errs.append(f"{pid}: {name} 含冗余身份介绍（「是唐朝…诗人」）")

            b, m = zh_len(bg), zh_len(mean)
            bg_lengths.append(b)
            mean_lengths.append(m)
            if not (BG_MIN <= b <= BG_MAX):
                errs.append(f"{pid} 《{title}》: background {b} 字，超出 {BG_MIN}~{BG_MAX}")
            if not (MEAN_MIN <= m <= MEAN_MAX):
                errs.append(f"{pid} 《{title}》: meaning {m} 字，超出 {MEAN_MIN}~{MEAN_MAX}")

            if bg:
                opens[bg[:8]].append(pid)
                # 「写错诗」检测：种子诗都以《诗题》收尾点题，但 32 首验收文案证明
                # 结尾可多样化（「才有了《X》」「把听到的写下来，成了《X》」等），
                # 且长诗题《黄鹤楼送孟浩然之广陵》《夜上受降城闻笛》不易自然塞进正文。
                # 故改为只检查标题核心二字是否出现（组诗的「其一/其二」等序号不算），
                # 误报由人工复核兜底——宁漏不错，避免把好文案判死。
                core = re.sub(r"[·（()）]|其[一二三四五六七八九十]", "", title)[:2]
                if len(core) >= 2 and core not in bg:
                    errs.append(f"{pid} 《{title}》: background 未提及核心词「{core}」")
            if bg and mean and mean.strip() and mean in bg:
                errs.append(f"{pid}: meaning 是 background 的子串")

    dup_open = {k: v for k, v in opens.items() if len(v) > 1}
    for k, v in dup_open.items():
        errs.append(f"开头雷同「{k}…」: {v}")

    total = len(bg_lengths)
    print(f"校验 {len(files)} 个分片 / {total} 首")
    if bg_lengths:
        print(f"  background {min(bg_lengths)}~{max(bg_lengths)} 字 "
              f"(均值 {sum(bg_lengths) // len(bg_lengths)})")
        print(f"  meaning    {min(mean_lengths)}~{max(mean_lengths)} 字 "
              f"(均值 {sum(mean_lengths) // len(mean_lengths)})")
    if errs:
        print(f"\n不合规 {len(errs)} 项:")
        for e in errs:
            print("  ", e)
        return 1
    print("\n全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())