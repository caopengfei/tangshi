"""把 build/polyphone-by-char.txt 切成 N 份，供并行人工/模型裁定。
每份一个 build/adjudicate/chunk-K.txt，裁定结果写入同目录 chunk-K.json。
"""
import io
import pathlib
import re
import sys

ROOT = pathlib.Path(r"D:\develop\src\rakor\tangshi")
SRC = ROOT / "build/polyphone-by-char.txt"
OUTDIR = ROOT / "build/adjudicate"

N = int(sys.argv[1]) if len(sys.argv) > 1 else 8

text = SRC.read_text(encoding="utf-8")
blocks = re.split(r"\n(?=## )", text)
head, items = blocks[0], blocks[1:]
# 按出现次数降序轮转分配，避免前若干块全是最常见的字
chunks = [[] for _ in range(N)]
for i, b in enumerate(items):
    chunks[i % N].append(b)

OUTDIR.mkdir(parents=True, exist_ok=True)
for k, items_k in enumerate(chunks):
    body = f"# 裁定块 {k} / 共 {N}（共 {len(items_k)} 字）\n" \
           f"# 填写 chunk-{k}.json：{{\"verdicts\": [{{\"char\":\"…\",\"readings\":{{\"现读音\":\"正/误\"}},\"fixes\":[{{\"phrase\":\"词组\",\"py\":\"逐字拼音\"}}]}}]}}\n"
    (OUTDIR / f"chunk-{k}.txt").write_text(body + "\n" + "".join(items_k), encoding="utf-8")
    print(f"chunk-{k}.txt: {len(items_k)} 字")