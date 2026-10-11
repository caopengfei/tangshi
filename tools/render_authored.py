"""把 tools/authored/*.json 渲染成可读 Markdown 对照稿（含原诗），便于人工验收文风。
用法：python tools/render_authored.py [part-00 ...]  → build/authored-preview.md
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
AUTHORED = ROOT / "tools/authored"
SK = ROOT / "build/corpus-skeleton.json"
OUT = ROOT / "build/authored-preview.md"


def main() -> int:
    sk = {p["id"]: p for p in json.loads(SK.read_text(encoding="utf-8"))}
    names = sys.argv[1:] or [p.stem for p in sorted(AUTHORED.glob("*.json"))]
    out = ["# 人工撰写内容 · 验收稿", "",
           "每首含：原诗 → 创作背景 → 释义。对照文风范本见下方附录。", ""]
    n = 0
    for name in names:
        f = AUTHORED / f"{name}.json"
        if not f.exists():
            print(f"skip {name}: 不存在")
            continue
        entries = json.loads(f.read_text(encoding="utf-8"))["poems"]
        out += [f"## {name}（{len(entries)} 首）", ""]
        for e in entries:
            p = sk.get(e["id"])
            if not p:
                continue
            n += 1
            out += [f"### {p['title']} · {p['poet']}　`{e['id']}`", "",
                    "> " + "　".join(l["text"] for l in p["lines"]), "",
                    f"**背景**（{len(e['background'])}字）", "",
                    e["background"], "",
                    f"**释义**（{len(e['meaning'])}字）", "",
                    e["meaning"], "", "---", ""]
    out += ["## 附录 · 已上线种子诗文风范本", "",
            "### 静夜思 · 李白", "",
            "> 床前明月光，疑是地上霜。举头望明月，低头思故乡。", "",
            "**背景**：李白年轻时离开家乡，长期在外游历。一个秋天的夜晚，他住在扬州的旅舍里，抬头看见窗外一轮皎洁的明月，月光洒在地上像一层白霜。他想：家乡的亲人是不是也在看着同一轮月亮呢？想念之下，写下了这首《静夜思》。", "",
            "**释义**：明亮的月光洒在床前，好像地上铺了一层白霜。抬起头望着天上的明月，低下头来思念远方的家乡。", "",
            "### 枫桥夜泊 · 张继", "",
            "> 月落乌啼霜满天，江枫渔火对愁眠。姑苏城外寒山寺，夜半钟声到客船。", "",
            "**背景**：张继是唐朝诗人。相传他进京赶考没有考中，回家路上，船停在苏州城外的枫桥边过夜。深夜里，月亮落下去，乌鸦啼叫，寒霜满天，他对着江边的枫树和渔船上的灯火，怎么也睡不着。半夜，寒山寺的钟声传到船上，他把心里的愁写成了这首《枫桥夜泊》。", "",
            "**释义**：月亮西落，乌鸦声声啼叫，寒霜仿佛布满了天空；我对着江边的枫树和点点渔火，愁得难以入眠。姑苏城外的寒山寺，半夜的钟声悠悠地传到我的客船上。", ""]
    OUT.write_text("\n".join(out), encoding="utf-8", newline="\n")
    print(f"{n} 首 -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())