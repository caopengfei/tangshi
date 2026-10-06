#!/usr/bin/env python3
"""用法: python tools/gen_voice.py [--check-only]

读取 poems.json + tts_substitutions.json：
  1. 用 pypinyin 找出每个汉字的多音字候选（heteronym）
  2. 被 confirmedDefault 白名单覆盖 或 被替换表覆盖 → 通过
  3. 其余多音字 → 打印清单并 exit 1（不允许裸奔合成——规格 8 的多音字门）
产出 build/voice-tts-input/{poemId}/lineN.txt（替换后的 TTS 输入）与 build/voice-jobs.json

--check-only: 只跑多音字门，不写任何文件（供控制者裁定替换表后重跑）。
"""
import json
import pathlib
import sys

from pypinyin import Style, pinyin

ROOT = pathlib.Path(__file__).resolve().parent.parent
POEMS = ROOT / "app/src/main/assets/content/poems.json"
SUBS = ROOT / "tools/tts_substitutions.json"
OUT = ROOT / "build/voice-tts-input"
JOBS = ROOT / "build/voice-jobs.json"


def main():
    check_only = "--check-only" in sys.argv
    table = json.loads(SUBS.read_text(encoding="utf-8"))
    confirmed = set(table.get("confirmedDefault", []))   # 默认读音正确、无需处理的多音字
    globalmap = table.get("global", {})
    perpoem = table.get("poems", {})
    poems = json.loads(POEMS.read_text(encoding="utf-8"))

    # 先全量计算：门未过则一个文件都不写（check-only 恒只读，正常模式也不留半成品）
    jobs, missing, outputs = [], [], []
    for p in poems:
        repl = dict(globalmap)
        repl.update(perpoem.get(p["id"], {}))
        for i, line in enumerate(p["lines"], 1):
            text, report = line["text"], []
            for ch in text:
                if not ("\u4e00" <= ch <= "\u9fff"):
                    continue
                heteronyms = pinyin(ch, style=Style.TONE, heteronym=True)[0]
                if len(heteronyms) > 1 and ch not in confirmed and ch not in repl:
                    report.append(f"{ch}:{'/'.join(heteronyms)}")
                if ch in repl:  # 1:1 同音字替换（只改 TTS 输入）
                    text = text.replace(ch, repl[ch])
            if report:
                missing.append(f'{p["id"]} L{i}: {" ".join(report)}')
            outputs.append((p["id"], i, text))
            jobs.append({"poemId": p["id"], "line": i, "count": len(p["lines"])})

    if missing:
        print("未覆盖多音字（需加入替换表或 confirmedDefault）:")
        for m in missing:
            print(" ", m)
        sys.exit(1)

    if not check_only:
        for pid, i, text in outputs:
            d = OUT / pid
            d.mkdir(parents=True, exist_ok=True)
            (d / f"line{i}.txt").write_text(text, encoding="utf-8")
        JOBS.parent.mkdir(parents=True, exist_ok=True)
        JOBS.write_text(json.dumps(jobs, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"ok: {len(jobs)} lines ready"
          + ("" if check_only else " -> build/voice-tts-input/, jobs -> build/voice-jobs.json"))


if __name__ == "__main__":
    main()
