#!/usr/bin/env python3
"""用法: python tools/validate_voice.py <音频目录=app/src/main/assets/audio>

校验（与 AudioAssetsTest 同一套门，供生成侧先行自检）：
  1. 每首诗 audio/{id}/manifest.json 存在，且 lines 句数 = poems.json 句数
  2. 每句音频文件存在且 >1KB
  3. 文件头魔数 = ID3 / 0xFFEx（MP3 sync）/ OggS（Opus）
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
POEMS = ROOT / "app/src/main/assets/content/poems.json"


def main():
    audio = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/audio"
    poems = json.loads(POEMS.read_text(encoding="utf-8"))
    bad = []
    for p in poems:
        d = audio / p["id"]
        mf = d / "manifest.json"
        if not mf.exists():
            bad.append(f"{p['id']}: missing manifest")
            continue
        m = json.loads(mf.read_text(encoding="utf-8"))
        if len(m["lines"]) != len(p["lines"]):
            bad.append(f"{p['id']}: manifest lines mismatch")
        for i, e in enumerate(m["lines"], 1):
            f = d / e["file"]
            if not f.exists() or f.stat().st_size < 1024:
                bad.append(f"{p['id']}/{e['file']}: missing/too small")
                continue
            head = f.read_bytes()[:4]
            ok = head.startswith(b"ID3") or (head[0] == 0xFF and (head[1] & 0xE0) == 0xE0) or head == b"OggS"
            if not ok:
                bad.append(f"{p['id']}/{e['file']}: bad magic {head!r}")
    print("\n".join(bad) if bad else f"ok: {len(poems)} poems complete")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
