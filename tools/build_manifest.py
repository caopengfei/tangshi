#!/usr/bin/env python3
"""用法: python tools/build_manifest.py  （要求 ffprobe 在 PATH）

对 poems.json 每首诗: 读取 app/src/main/assets/audio/{id}/lineN.opus，
用 ffprobe 测时长，写出 audio/{id}/manifest.json：
  {"lines": [{"file": "lineN.opus", "durationMs": N}, ...]}
"""
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
A = ROOT / "app/src/main/assets/audio"
POEMS = json.loads((ROOT / "app/src/main/assets/content/poems.json").read_text(encoding="utf-8"))


def duration_ms(f: pathlib.Path) -> int:
    out = subprocess.check_output(
        ["ffprobe", "-v", "quiet", "-print_format", "json", "-show_format", str(f)], text=True)
    return int(round(float(json.loads(out)["format"]["duration"]) * 1000))


def main():
    for p in POEMS:
        d = A / p["id"]
        lines = [{"file": f"line{i}.opus", "durationMs": duration_ms(d / f"line{i}.opus")}
                 for i in range(1, len(p["lines"]) + 1)]
        (d / "manifest.json").write_text(
            json.dumps({"lines": lines}, ensure_ascii=False, indent=1), encoding="utf-8")
        print(p["id"], "ok")


if __name__ == "__main__":
    try:
        main()
    except FileNotFoundError:
        sys.exit("ffprobe 不在 PATH——请安装 ffmpeg/ffprobe 后重试")
