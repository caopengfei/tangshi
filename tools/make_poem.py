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
    src = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8-sig")
    poems = json.loads(POEMS.read_text(encoding="utf-8"))
    by_id = {p["id"]: p for p in poems}
    imported = 0
    for block in re.split(r"\n(?=#id:)", src.strip()):
        meta, lines, bg = {}, [], ""
        for raw in block.strip().splitlines():
            if raw.startswith("#"): k, v = raw[1:].split(":", 1); meta[k.strip()] = v.strip()
            elif raw.startswith("@background:"): bg = raw[len("@background:"):].strip()
            elif raw.strip():
                if "|" not in raw: sys.exit(f"格式错误（缺 |）: {raw[:30]}")
                text, pys = raw.split("|")
                pys = pys.split()
                han = [c for c in text if re.match(r"[\u4e00-\u9fff]", c)]
                assert len(han) == len(pys), f"{meta.get('id')} 『{text}』 汉字{len(han)} != 拼音{len(pys)}"
                lines.append({"text": text, "chars": [{"c": c, "py": p} for c, p in zip(han, pys)]})
        if not lines: sys.exit(f"{meta.get('id')}: 无诗句行")
        entry = {"id": meta["id"], "order": int(meta["order"]), "title": meta["title"],
                 "poet": meta["poet"], "dynasty": meta["dynasty"], "background": bg, "lines": lines}
        by_id[entry["id"]] = entry
        imported += 1
    POEMS.write_text(json.dumps(sorted(by_id.values(), key=lambda p: p["order"]), ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"imported {imported}, total {len(by_id)}")

if __name__ == "__main__":
    main()
