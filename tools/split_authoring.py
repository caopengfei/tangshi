"""把 build/corpus-skeleton.json 中缺 background/meaning 的诗，按体裁切成撰写分片。
分片含完整诗句（撰写背景/释义时必须看得见原文），输出到 tools/authoring/。
"""
import json
import pathlib
import re

ROOT = pathlib.Path(r"D:\develop\src\rakor\tangshi")
SK = ROOT / "build/corpus-skeleton.json"
OUT = ROOT / "tools/authoring"
PER = int(__import__("sys").argv[1]) if len(__import__("sys").argv) > 1 else 16

seed_ids = {p["id"] for p in json.loads(
    (ROOT / "app/src/main/assets/content/poems.json").read_text(encoding="utf-8"))}

poems = [p for p in json.loads(SK.read_text(encoding="utf-8")) if p["id"] not in seed_ids]
# 源数据的体裁名是繁体，按源顺序分组即可（无需再转简体）
order = list(dict.fromkeys(p["form"] for p in poems))
buckets: dict[str, list] = {f: [] for f in order}
for p in poems:
    buckets[p["form"]].append(p)

groups, cur, size = [], [], 0
for f in order:
    for p in buckets[f]:
        cur.append(p)
        size += 1
        if size >= PER:
            groups.append(cur)
            cur, size = [], 0
if cur:
    groups.append(cur)

OUT.mkdir(parents=True, exist_ok=True)
for old in OUT.glob("*.json"):
    old.unlink()

manifest = []
for i, g in enumerate(groups):
    name = f"part-{i:02d}"
    body = {
        "_instructions": [
            "为下列每首诗撰写 background 与 meaning，写入同目录同名 .json。",
            "background: 80~150 字，一诗一段，口语化、讲故事，面向 7~12 岁儿童，"
            "说清「诗人为什么写这首诗」——时间、地点、人物、心情、触发的具体事由。",
            "不要写「X 是唐朝诗人」「他是唐朝诗人」这种身份介绍：全书都是唐诗，"
            "读者打开就知道是唐朝人，再交代一遍是废话，还会让多首开头雷同。"
            "诗人身份/性格只用行为带出，如「写诗从来不只是写眼前」。"
            "但「唐朝」作为历史语境要保留，如「凉州是唐朝和西域交界的地方」。",
            "meaning: 40~90 字，用儿童听得懂的话把这首诗的意思说一遍，"
            "不要逐字翻译，不要出现「本诗」「诗人」等书面套话。",
            "严禁编造史实；不确定的用「相传」「大概」等模糊表述。",
            "严禁套模板：每首的切入角度、句式、用词都要不同，不要出现开头雷同的段落。",
            "只输出 JSON，结构：{\"poems\":[{\"id\":\"…\",\"background\":\"…\",\"meaning\":\"…\"}]}，"
            "id 必须与输入完全一致，顺序一致，不要增删条目。",
        ],
        "poems": [
            {"id": p["id"], "title": p["title"], "poet": p["poet"], "form": p["form"],
             "lines": [l["text"] for l in p["lines"]]}
            for p in g
        ],
    }
    (OUT / f"{name}.json").write_text(
        json.dumps(body, ensure_ascii=False, indent=1), encoding="utf-8")
    manifest.append({"part": name, "count": len(g),
                     "forms": sorted({p["form"] for p in g})})

(OUT / "_manifest.json").write_text(
    json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"{len(poems)} poems -> {len(groups)} parts of ~{PER}")
for m in manifest:
    print(f"  {m['part']}: {m['count']} {m['forms']}")