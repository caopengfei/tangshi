#!/usr/bin/env python3
"""tools/merge_poems.py —— 语料骨架 + 人工背景/释义 → app 资产 poems.json

输入：
  build/corpus-skeleton.json          确定性产物（id/order/title/poet/lines/拼音）
  tools/authored/*.json               人工撰写的 background/meaning（按 id 分片）
  tools/seed/poems-seed-archive.json  既有 10 首种子诗存档（人工校对，原样保留）
输出：
  app/src/main/assets/content/poems.json  全量 325 首，按 order 排序

合并规则：
  - 种子诗人工校对版本优先，骨架中同 id 者一律以种子为准（不覆盖文本/拼音/背景/释义）；
  - 骨架之外的种子诗（通行本外的 5 首，已有音频资产与本地进度）原样保留；
  - 骨架诗若 authored 分片缺失 background 或 meaning → 报错退出（内容不留空）；
  - 输出 JSON 用 indent=1 + LF，与既有资产格式一致（Task 14 加固项）。

注意：种子诗数据源是存档文件，**不是** poems.json 本身——本脚本会覆盖后者，
若从自身读取则二次运行会把种子诗越跑越少（踩过：首跑丢了 5 首）。

用法：python tools/merge_poems.py [--check-only]
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKELETON = ROOT / "build/corpus-skeleton.json"
AUTHORED_DIR = ROOT / "tools/authored"
SEED_ARCHIVE = ROOT / "tools/seed/poems-seed-archive.json"
POEMS = ROOT / "app/src/main/assets/content/poems.json"


def load_authored() -> dict:
    """收集 tools/authored/*.json 的 {id: {background, meaning}}。"""
    out: dict[str, dict] = {}
    if not AUTHORED_DIR.exists():
        return out
    for f in sorted(AUTHORED_DIR.glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        entries = data["poems"] if isinstance(data, dict) else data
        for e in entries:
            pid = e["id"]
            if pid in out:
                sys.exit(f"重复的 authored id: {pid}（{f.name}）")
            out[pid] = e
    return out


def main() -> int:
    check_only = "--check-only" in sys.argv
    skeleton = json.loads(SKELETON.read_text(encoding="utf-8"))
    if not SEED_ARCHIVE.exists():
        sys.exit(f"缺种子诗存档 {SEED_ARCHIVE}（应从 git HEAD 的 poems.json 生成）")
    seeds = json.loads(SEED_ARCHIVE.read_text(encoding="utf-8"))
    authored = load_authored()

    seed_by_id = {p["id"]: p for p in seeds}
    missing, merged = [], []

    skeleton_ids = {p["id"] for p in skeleton}
    for p in skeleton:
        pid = p["id"]
        if pid in seed_by_id:
            # 种子诗：人工校对版本优先，仅当骨架有新增同 id 时提示
            merged.append(seed_by_id[pid])
            continue
        a = authored.get(pid)
        bg, mean = (a or {}).get("background", ""), (a or {}).get("meaning", "")
        if not bg or not mean:
            missing.append((pid, p["title"], "background" if not bg else "meaning"))
        merged.append({
            "id": pid, "order": p["order"], "title": p["title"],
            "poet": p["poet"], "dynasty": p["dynasty"],
            "lines": p["lines"], "background": bg, "meaning": mean,
        })

    # 骨架之外的种子诗（通行本之外、但已配音频与学习进度的 5 首）原样保留，
    # 否则「共 N 首」会少算、已生成的音频资产与本地进度全部失效。
    extra = [p for p in seeds if p["id"] not in skeleton_ids]
    merged.extend(extra)
    if extra:
        print(f"  骨架外种子诗 {len(extra)} 首保留: "
              + "、".join(p['title'] for p in extra))

    merged.sort(key=lambda x: x["order"])
    lines = sum(len(p["lines"]) for p in merged)
    print(f"merged={len(merged)} poems, {lines} lines")
    print(f"  seeds={len(seeds)}  skeleton={len(skeleton)}  authored={len(authored)}")
    if missing:
        print(f"缺 background/meaning 的诗 {len(missing)} 首:")
        for pid, title, field in missing[:40]:
            print(f"  {pid} 《{title}》 缺 {field}")
        if len(missing) > 40:
            print(f"  …另有 {len(missing) - 40} 首")

    if check_only:
        return 1 if missing else 0
    if missing:
        sys.exit("内容不全，拒绝写入 poems.json")

    POEMS.write_text(json.dumps(merged, ensure_ascii=False, indent=1) + "\n",
                     encoding="utf-8", newline="\n")
    print(f"-> {POEMS}")
    return 0


if __name__ == "__main__":
    sys.exit(main())