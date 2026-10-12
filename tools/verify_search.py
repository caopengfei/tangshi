#!/usr/bin/env python3
"""搜索页实机验证：D-pad 打字 + uiautomator 断言（配套 2026-10-12 搜索页改版规格）。

用法（模拟器在线、应用已装 debug 包）：
    python tools/verify_search.py LB CQMYG DENGGUANQUELOU

每个词独立一轮：强停→启动→首页 D-pad 进搜索页→OK 进键盘→贪心导航逐键 OK 打字→
断言关键字与结果计数。期望计数在 EXPECTED 表（与 SearchMatcherTest 同源口径）。
"""
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = "D:/develop/androidsdk/platform-tools/adb.exe"
KEY_ROWS = ["ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ"]
# 打字后期望的「共找到 N 首」（tools 模拟口径；与单测/集成断言同源）
EXPECTED = {
    "LB": 82,
    "CQMYG": 1,
    "JYS": 14,
    "DENGGUANQUELOU": 1,
    "CHUANGQIAN": 2,
}


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8")


def keyevent(code):
    adb("shell", f"input keyevent {code}")
    time.sleep(0.3)


def _bounds(b):
    x0, y0, x1, y1 = (int(v) for v in b.strip("[]").replace("][", ",").split(","))
    return (x0, y0, x1, y1)


def dump_state():
    """一次 dump → (文本节点列表, 焦点信息)。

    焦点常落在**无文本容器**上（键帽 Surface），故焦点单独抓取并附带其子树全部文本；
    文本节点列表供关键字/计数/键中心读取。
    """
    adb("shell", "uiautomator dump /sdcard/ui.xml")
    out = subprocess.run([ADB, "exec-out", "cat /sdcard/ui.xml"], capture_output=True)
    root = ET.fromstring(out.stdout.decode("utf-8"))
    texts, focused = [], None
    for n in root.iter():
        if n.get("focused") == "true":
            focused = {
                "b": _bounds(n.get("bounds")),
                "texts": [c.get("text") for c in n.iter() if c.get("text")],
            }
        if n.get("text") and n.get("bounds"):
            texts.append({"text": n.get("text"), "b": _bounds(n.get("bounds")),
                          "cls": n.get("class")})
    return texts, focused


def key_center(ns, ch):
    """键盘字母键 TextView 的中心坐标。"""
    for n in ns:
        if n["text"] == ch and n["cls"] == "android.widget.TextView":
            x0, y0, x1, y1 = n["b"]
            return ((x0 + x1) // 2, (y0 + y1) // 2)
    return None


def keyword_value(ns):
    """关键字框当前值：文本形如 '<前缀>：值▏'；未输入时取『点按输入』→ 空。"""
    for n in ns:
        if "▏" in n["text"]:
            v = n["text"].split("：", 1)[-1].removesuffix("▏")
            return "" if v in ("点按输入", "") else v
    return None


def to_key(ch):
    """贪心导航到字母键：每步 dump 读焦点，按坐标差方向移动，直到焦点键文本==ch。"""
    import os
    debug = bool(os.environ.get("VERIFY_DEBUG"))
    prev = None
    for step in range(48):
        ns, f = dump_state()
        # 焦点在关键字框（带光标）→ OK 进键盘（落首键 A，已验证路径）
        if f and any("▏" in t for t in f["texts"]):
            keyevent(23)
            continue
        tgt = key_center(ns, ch)
        if tgt is None:
            raise RuntimeError(f"键盘上找不到键 {ch}")
        cx = cy = None
        if f:
            x0, y0, x1, y1 = f["b"]
            if ch in f["texts"]:
                return
            # 焦点容器内含字母键文本 → 用该字母中心（容器即键帽）
            inner = [n for n in ns if len(n["text"]) == 1
                     and n["b"][0] >= x0 and n["b"][2] <= x1
                     and n["b"][1] >= y0 and n["b"][3] <= y1]
            if inner:
                if inner[0]["text"] == ch:
                    return
                cx, cy = ((inner[0]["b"][0] + inner[0]["b"][2]) // 2,
                          (inner[0]["b"][1] + inner[0]["b"][3]) // 2)
            else:
                cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        if cx is None:
            keyevent(23)  # 焦点未知 → OK 试探
            continue
        dx, dy = tgt[0] - cx, tgt[1] - cy
        move = None
        if abs(dy) > 50:
            move = 20 if dy > 0 else 19
        elif abs(dx) > 50:
            move = 22 if dx > 0 else 21
        if move is None:
            return  # 已在目标键上
        if prev == (move, cx, cy):  # 同一焦点重复同向移动无效 → 换轴
            move = 22 if abs(dx) > abs(dy) else 20
        prev = (move, cx, cy)
        if debug:
            print(f"  [step {step}] focus={f['texts'][:1] if f else None} "
                  f"@{(cx, cy)} target={ch}@{tgt} dx={dx} dy={dy} press={move}",
                  file=sys.stderr)
        keyevent(move)
    raise RuntimeError(f"导航到键 {ch} 超过步数上限")


def goto_search_screen():
    """强停→启动→首页（焦点=当前诗卡）→ DOWN LEFT LEFT OK → 搜索页。"""
    adb("shell", "am force-stop com.rakor.tangshi")
    time.sleep(1)
    adb("shell", "monkey -p com.rakor.tangshi -c android.intent.category.LEANBACK_LAUNCHER 1")
    time.sleep(4)
    for k in (20, 21, 21):
        keyevent(k)
    keyevent(23)
    time.sleep(1)


def type_word(word):
    goto_search_screen()
    # 入口断言：焦点在关键字框（带光标文本）；诗人筛选钮已删除（无「诗人：」文本）
    _, f = dump_state()
    if not (f and any("▏" in t for t in f["texts"])):
        raise RuntimeError(f"入口焦点应在关键字框，实际 {f and f['texts']}")
    if any(n["text"].startswith("诗人：") for n in dump_state()[0]):
        raise RuntimeError("检测到旧版「诗人：」筛选钮——诗人选择应已删除")
    keyevent(23)  # 关键字框 OK → 键盘首键 A
    for ch in word:
        to_key(ch)
        keyevent(23)
        got = keyword_value(dump_state()[0])
        if got != word[:word.index(ch) + 1]:
            raise RuntimeError(f"打字断言失败：期望 {word[:word.index(ch) + 1]}，实际 {got}")


def check_centering(tolerance=(10, 12)):
    """键帽文字居中数值断言：字母 TextView 中心 vs 其键帽父容器中心的最大偏移。

    改版前基线 dx=33 dy=20px（top-start 顶格）；修复后应 ≤ 容差。
    """
    adb("shell", "uiautomator dump /sdcard/ui.xml")
    out = subprocess.run([ADB, "exec-out", "cat /sdcard/ui.xml"], capture_output=True)
    root = ET.fromstring(out.stdout.decode("utf-8"))
    parent = {c: p for p in root.iter() for c in p}
    worst = (0, 0, "?")
    for n in root.iter():
        if n.get("class") != "android.widget.TextView" or len(n.get("text", "")) != 1:
            continue
        p = parent.get(n)
        if p is None or not p.get("bounds"):
            continue
        tb, pb = _bounds(n.get("bounds")), _bounds(p.get("bounds"))
        if pb[2] - pb[0] > 200:  # 非键帽（结果行/整卡）
            continue
        dx = abs((tb[0] + tb[2]) // 2 - (pb[0] + pb[2]) // 2)
        dy = abs((tb[1] + tb[3]) // 2 - (pb[1] + pb[3]) // 2)
        if dx > worst[0] or dy > worst[1]:
            worst = (dx, dy, n.get("text"))
    ok = worst[0] <= tolerance[0] and worst[1] <= tolerance[1]
    print(f"{'PASS' if ok else 'FAIL'}  键帽文字居中: 最大偏移 dx={worst[0]} dy={worst[1]}px"
          f"（键 {worst[2]}；基线 33/20，容差 {tolerance[0]}/{tolerance[1]}）")
    return ok


def count_of(ns):
    for n in ns:
        if n["text"].startswith("共找到"):
            return int(n["text"].split(" ")[1])
    return None


def main(words):
    failures = []
    for i, w in enumerate(words):
        type_word(w)
        ns, _ = dump_state()
        got_count = count_of(ns)
        want = EXPECTED.get(w)
        ok = want is None or got_count == want
        print(f"{'PASS' if ok else 'FAIL'}  {w}: 共找到 {got_count} 首" +
              (f"（期望 {want}）" if want else ""))
        if not ok:
            failures.append(w)
        if i == 0 and not check_centering():
            failures.append("centering")
    return 1 if failures else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
