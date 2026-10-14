#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 Android TV 应用 banner（app/src/main/res/drawable/app_banner.xml）。

用法:
    python tools/make_banner.py                    # 只生成 XML
    python tools/make_banner.py --svg out.svg      # 额外导出同几何的 SVG（便于预览/复用）
    python tools/make_banner.py --preview out.png  # 本地光栅化预览（需 numpy + Pillow）

为什么需要脚本而不是手写 XML：Android TV 启动横幅按 Google TV 应用质量规范
（TV-BN「应用启动横幅包含应用名」）必须含应用名，而 VectorDrawable 不支持文字，
「唐诗三百首」只能以轮廓路径画进去。字形取自 app/src/main/res/font/wenkai.ttf
（霞鹜文楷，SIL OFL 1.1，见 tools/NOTICE.md），用 fontTools 取轮廓后转 pathData。
字体本身不上屏，只是轮廓来源。

场景与 mipmap/ic_launcher 同一套视觉语言（月夜山水），调色板逐值对齐。
"""
import argparse
import os
import re
import sys

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.pens.transformPen import TransformPen
from fontTools.misc.transform import Transform
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "wenkai.ttf")
OUT_XML = os.path.join(ROOT, "app", "src", "main", "res", "drawable", "app_banner.xml")

W, H = 320, 180

# ---- 调色板（与 ic_launcher / colors.xml 一致）--------------------------------
SKY = "#1B2A4E"      # 夜空 = ic_launcher_background
MOON = "#F7E9C6"     # 明月 / 月晕
STAR = "#FFF6E0"     # 疏星
CLOUD = "#EAF0FF"    # 流云
FAR = "#294467"      # 远山
NEAR = "#16233D"     # 近山
PAGE_L = "#FAF3E3"   # 诗书左页
PAGE_R = "#EFE4CE"   # 诗书右页
SPINE = "#D9C9A8"    # 书脊
RULE_INK = "#B9A98A"  # 页上诗行
CORAL = "#FF7A50"    # 品牌主色，只用作字标下划线
TITLE = "#FAF3E3"    # 字标

# ---- 字标排布 ----------------------------------------------------------------
TEXT = "唐诗三百首"
TEXT_INK_H = 30.0        # 墨迹高度（px）
TEXT_TRACK = 5.0         # 字距（px，加在前进宽度上）
TEXT_INK_LEFT = 22.0     # 墨迹左缘
TEXT_INK_VCENTER = 62.0  # 墨迹垂直中心
RULE_Y = 88.0            # 字标下划线顶边
RULE_W, RULE_H = 56.0, 3.4

# ---- 场景元素 ----------------------------------------------------------------
MOON_C = (272.0, 42.0)
MOON_R, HALO_R = 20.0, 32.0
STARS = [(214, 22, 1.5, 0.85), (230, 46, 1.2, 0.7), (312, 26, 1.2, 0.7),
         (308, 88, 1.5, 0.8), (14, 26, 1.5, 0.85), (44, 16, 1.0, 0.7),
         (76, 24, 1.2, 0.65), (186, 24, 1.3, 0.8), (212, 68, 1.0, 0.6),
         (12, 60, 1.1, 0.7), (292, 62, 1.1, 0.6)]
CLOUDS = [(216, 80, 74, 5.5, 0.10), (92, 30, 74, 5.0, 0.07)]
FAR_RIDGE = ("M-2,118 Q30,100 62,114 Q94,96 126,112 Q158,96 190,112 "
             "Q222,96 254,112 Q286,98 322,114 L322,182 L-2,182 Z")
NEAR_RIDGE = ("M-2,142 Q34,122 72,138 Q110,120 148,136 Q186,120 224,136 "
              "Q262,122 300,138 L322,142 L322,182 L-2,182 Z")
# 诗书：图标里以 (54,76.5) 为中心、宽 52 / 高 19，此处等比放大后平移到 (BOOK_C)
BOOK_S, BOOK_C = 1.15, (160.0, 146.0)


def num(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def circle(cx, cy, r):
    return (f"M{num(cx)},{num(cy)} m{num(-r)},0 "
            f"a{num(r)},{num(r)} 0,1 1,{num(2 * r)},0 "
            f"a{num(r)},{num(r)} 0,1 1,{num(-2 * r)},0")


def pill(x, y, w, h):
    """两端半圆的横向胶囊。"""
    r = h / 2.0
    return (f"M{num(x)},{num(y)} h{num(w - 2 * r)} "
            f"a{num(r)},{num(r)} 0,0 1,0,{num(h)} "
            f"h{num(-(w - 2 * r))} a{num(r)},{num(r)} 0,0 1,0,{num(-h)} z")


# ---- 字形轮廓 ----------------------------------------------------------------

def glyph_paths(text, scale, tracking, dx, dy):
    """取字形轮廓 → 单条 pathData。轮廓外/内方向由字体保证，nonzero 填充即可镂空。"""
    font = TTFont(FONT, fontNumber=0, lazy=True)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    parts, pen_x = [], 0.0
    for ch in text:
        gname = cmap[ord(ch)]
        rec = DecomposingRecordingPen(gs)
        gs[gname].draw(TransformPen(rec, Transform(scale, 0, 0, -scale, pen_x + dx, dy)))
        for op, args in rec.value:
            if op in ("moveTo", "lineTo"):
                parts.append(op[0].upper() + " ".join(f"{num(p[0])},{num(p[1])}" for p in args))
            elif op == "curveTo":
                parts.append("C" + " ".join(f"{num(p[0])},{num(p[1])}" for p in args))
            elif op == "qCurveTo":
                pts = list(args)
                if pts[-1] is None:  # TrueType 全离线点轮廓：以相邻中点作为隐含起点
                    n = len(pts) - 1
                    mids = [((pts[i][0] + pts[(i + 1) % n][0]) / 2,
                             (pts[i][1] + pts[(i + 1) % n][1]) / 2) for i in range(n)]
                    parts.append(f"M{num(mids[-1][0])},{num(mids[-1][1])}")
                    for i in range(n):
                        end = mids[i]
                        parts.append(f"Q{num(pts[i][0])},{num(pts[i][1])} "
                                     f"{num(end[0])},{num(end[1])}")
                else:
                    for i in range(len(pts) - 1):
                        c, e = pts[i], pts[i + 1]
                        parts.append(f"Q{num(c[0])},{num(c[1])} {num(e[0])},{num(e[1])}")
            elif op == "closePath":
                parts.append("Z")
            else:
                raise RuntimeError(f"未处理的轮廓指令 {op}（复合字形未分解？）")
        pen_x += hmtx[gname][0] * scale + tracking
    return " ".join(parts)


def glyph_bbox(text, scale, tracking):
    d = glyph_paths(text, scale, tracking, 0.0, 0.0)
    xs, ys = [], []
    for c in _flatten_path(d):
        for x, y in c:
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def title_path():
    """按目标墨迹框排布字标，返回 (pathData, 墨迹框)。"""
    probe = glyph_bbox(TEXT, 1.0, 0.0)
    scale = TEXT_INK_H / (probe[3] - probe[1])
    bb = glyph_bbox(TEXT, scale, TEXT_TRACK)
    dx = TEXT_INK_LEFT - bb[0]
    dy = TEXT_INK_VCENTER - (bb[1] + bb[3]) / 2.0
    ink = (bb[0] + dx, bb[1] + dy, bb[2] + dx, bb[3] + dy)
    return glyph_paths(TEXT, scale, TEXT_TRACK, dx, dy), ink


# ---- 场景 --------------------------------------------------------------------

def build_scene(title_d):
    bx, by = BOOK_C
    def T(x, y):
        return num(bx + (x - 54) * BOOK_S), num(by + (y - 76.5) * BOOK_S)

    def page(mirror):
        """诗书单页：从书脊顶到外缘，再折回到书脊底。"""
        s = -1 if mirror else 1
        def P(u, v):
            return T(54 + s * u, v)
        a, b, c = P(0, 73), P(26, 69), P(26, 83)
        d, e = P(18, 81), P(8, 82)
        f = P(0, 86)
        return (f"M{a[0]},{a[1]} C{P(8, 68)[0]},{P(8, 68)[1]} {P(18, 67)[0]},{P(18, 67)[1]} "
                f"{b[0]},{b[1]} L{c[0]},{c[1]} C{d[0]},{d[1]} {e[0]},{e[1]} {f[0]},{f[1]} Z")

    def lines(mirror):
        s = -1 if mirror else 1
        def P(u, v):
            return T(54 + s * u, v)
        p0, p1, p2, p3 = P(20, 74), P(7, 76.5), P(21, 78), P(8, 80.5)
        return f"M{p0[0]},{p0[1]} L{p1[0]},{p1[1]} M{p2[0]},{p2[1]} L{p3[0]},{p3[1]}"

    spine_a, spine_b = T(53.4, 73), T(54.6, 86)
    spine = (f"M{spine_a[0]},{spine_a[1]} L{spine_b[0]},{spine_a[1]} "
             f"L{spine_b[0]},{spine_b[1]} L{spine_a[0]},{spine_b[1]} Z")

    shapes = [
        {"note": "夜空", "d": f"M0,0 h{W} v{H} h-{W} z", "fill": SKY},
        {"note": "月晕", "d": circle(*MOON_C, HALO_R), "fill": MOON, "alpha": 0.10},
        {"note": "明月", "d": circle(*MOON_C, MOON_R), "fill": MOON},
        {"note": "疏星", "d": [circle(x, y, r) for x, y, r, _ in STARS],
         "fill": STAR, "alpha": [a for *_, a in STARS]},
        {"note": "流云", "d": [pill(x, y, w, h) for x, y, w, h, _ in CLOUDS],
         "fill": CLOUD, "alpha": [a for *_, a in CLOUDS]},
        {"note": "远山", "d": FAR_RIDGE, "fill": FAR},
        {"note": "近山", "d": NEAR_RIDGE, "fill": NEAR},
        {"note": "诗书 · 左页", "d": page(True), "fill": PAGE_L},
        {"note": "诗书 · 右页", "d": page(False), "fill": PAGE_R},
        {"note": "书脊", "d": spine, "fill": SPINE},
        {"note": "页上诗行", "d": [lines(True), lines(False)],
         "stroke": RULE_INK, "strokeW": 1.15, "strokeAlpha": 0.6},
        {"note": "字标下划线（珊瑚橙）", "d": pill(TEXT_INK_LEFT, RULE_Y, RULE_W, RULE_H), "fill": CORAL},
        {"note": "字标「唐诗三百首」", "d": title_d, "fill": TITLE},
    ]
    # 展开 d / alpha 列表项
    out = []
    for s in shapes:
        if isinstance(s["d"], list):
            alphas = s.get("alpha")
            if isinstance(alphas, list):
                for d, a in zip(s["d"], alphas):
                    out.append({"note": s["note"], "d": d, "fill": s["fill"],
                                "alpha": a, "stroke": s.get("stroke"),
                                "strokeW": s.get("strokeW"),
                                "strokeAlpha": s.get("strokeAlpha")})
            else:
                for d in s["d"]:
                    t = dict(s)
                    t["d"] = d
                    out.append(t)
        else:
            out.append(s)
    return out


# ---- 输出 --------------------------------------------------------------------

def dump_xml(shapes, path):
    head = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- 应用 banner（Android TV 启动横幅，{W}x{H}dp）：月夜山水 + 字标。',
        '     与 mipmap/ic_launcher 同一套视觉语言；字标字形取自 res/font/wenkai.ttf（霞鹜文楷）。',
        '     TV-BN 规范要求 banner 含应用名，故「唐诗三百首」直接画进图形。',
        '     由 tools/make_banner.py 生成，手改请同步改脚本。 -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{W}dp"',
        f'    android:height="{H}dp"',
        f'    android:viewportWidth="{W}"',
        f'    android:viewportHeight="{H}">',
        '',
    ]
    body, last = [], None
    for s in shapes:
        if s["note"] != last:
            body.append(f'    <!-- {s["note"]} -->')
            last = s["note"]
        a = [f'        android:pathData="{s["d"]}"']
        if s.get("stroke"):
            a.append(f'        android:strokeColor="{s["stroke"]}"')
            a.append(f'        android:strokeWidth="{num(s["strokeW"])}"')
            if s.get("strokeAlpha", 1) != 1:
                a.append(f'        android:strokeAlpha="{s["strokeAlpha"]}"')
        else:
            a.append(f'        android:fillColor="{s["fill"]}"')
            if s.get("alpha") is not None and s["alpha"] != 1:
                a.append(f'        android:fillAlpha="{num(s["alpha"])}"')
        body.append('    <path')
        body.append("\n".join(a) + " />")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(head + body + ['</vector>', '']))


def dump_svg(shapes, path):
    body = []
    for s in shapes:
        if s.get("stroke"):
            body.append(f'  <path d="{s["d"]}" fill="none" stroke="{s["stroke"]}" '
                        f'stroke-width="{num(s["strokeW"])}" '
                        f'stroke-opacity="{num(s.get("strokeAlpha", 1))}"/>')
        else:
            op = f' fill-opacity="{num(s["alpha"])}"' if s.get("alpha") is not None else ""
            body.append(f'  <path d="{s["d"]}" fill="{s["fill"]}"{op}/>')
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
                f'viewBox="0 0 {W} {H}">\n' + "\n".join(body) + "\n</svg>\n")


# ---- 本地预览（可选，需 numpy + Pillow）--------------------------------------

_NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
_CMD_ARG = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)")
_BEZ_N = 16
_ARC_N = 48


def _arc_points(x0, y0, rx, ry, phi_deg, large_arc, sweep, x1, y1):
    import math
    phi = math.radians(phi_deg)
    cp, sp = math.cos(phi), math.sin(phi)
    dx2, dy2 = (x0 - x1) / 2.0, (y0 - y1) / 2.0
    x1p, y1p = cp * dx2 + sp * dy2, -sp * dx2 + cp * dy2
    rx, ry = abs(rx), abs(ry)
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        s = math.sqrt(lam)
        rx, ry = rx * s, ry * s
    num_ = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num_ / den)) if den else 0.0
    if large_arc == sweep:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = cp * cxp - sp * cyp + (x0 + x1) / 2.0
    cy = sp * cxp + cp * cyp + (y0 + y1) / 2.0
    t1 = math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    dt = math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - t1
    if not sweep and dt > 0:
        dt -= 2 * math.pi
    elif sweep and dt < 0:
        dt += 2 * math.pi
    return [(cp * rx * math.cos(t1 + dt * i / _ARC_N) - sp * ry * math.sin(t1 + dt * i / _ARC_N) + cx,
             sp * rx * math.cos(t1 + dt * i / _ARC_N) + cp * ry * math.sin(t1 + dt * i / _ARC_N) + cy)
            for i in range(1, _ARC_N + 1)]


def _flatten_path(d):
    """pathData → 闭合折线列表。与渲染器共用，仅支持矢量图会出现的指令。"""
    import math
    contours, cur = [], []
    x = y = sx = sy = 0.0
    pc = pq = None
    for cmd, s in _CMD_ARG.findall(d):
        nums = [float(v) for v in _NUM.findall(s or "")]
        rel, c = cmd.islower(), cmd.upper()
        if c == "Z":
            if cur:
                cur.append((sx, sy))
                contours.append(cur)
                cur = []
            x, y, pc, pq = sx, sy, None, None
            continue
        if c == "M":
            if cur:
                contours.append(cur)
                cur = []
            x, y = (x + nums[0], y + nums[1]) if rel else (nums[0], nums[1])
            sx, sy = x, y
            cur.append((x, y))
            rest = range(2, len(nums), 2)
        elif c == "L":
            rest = range(0, len(nums), 2)
        elif c == "H":
            for v in nums:
                x = x + v if rel else v
                cur.append((x, y))
            continue
        elif c == "V":
            for v in nums:
                y = y + v if rel else v
                cur.append((x, y))
            continue
        elif c == "C":
            for k in range(0, len(nums), 6):
                p = [(x + nums[k + j] if rel else nums[k + j]) for j in range(6)]
                c1, c2, e = (p[0], p[1]), (p[2], p[3]), (p[4], p[5])
                for i in range(1, _BEZ_N + 1):
                    t = i / _BEZ_N
                    u = 1 - t
                    cur.append((u**3 * x + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t**3 * e[0],
                                u**3 * y + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t**3 * e[1]))
                pc, x, y = c2, e[0], e[1]
            continue
        elif c in ("Q", "S", "T", "A"):
            for k in range(0, len(nums), {"Q": 4, "S": 4, "T": 2, "A": 7}[c]):
                if c == "A":
                    e = (x + nums[k + 5], y + nums[k + 6]) if rel else (nums[k + 5], nums[k + 6])
                    cur.extend(_arc_points(x, y, nums[k], nums[k + 1], nums[k + 2],
                                           int(nums[k + 3]), int(nums[k + 4]), e[0], e[1]))
                    x, y = e
                    continue
                step = 2 if c == "T" else 4
                p = [(x + nums[k + j] if rel else nums[k + j]) for j in range(step)]
                if c == "Q":
                    ctrl, e = (p[0], p[1]), (p[2], p[3])
                    pq = ctrl
                elif c == "S":
                    ctrl1 = (2 * x - pc[0], 2 * y - pc[1]) if pc else (x, y)
                    c2, e = (p[0], p[1]), (p[2], p[3])
                    for i in range(1, _BEZ_N + 1):
                        t = i / _BEZ_N
                        u = 1 - t
                        cur.append((u**3 * x + 3 * u * u * t * ctrl1[0] + 3 * u * t * t * c2[0] + t**3 * e[0],
                                    u**3 * y + 3 * u * u * t * ctrl1[1] + 3 * u * t * t * c2[1] + t**3 * e[1]))
                    pc, x, y = c2, e[0], e[1]
                    continue
                else:
                    e = (p[0], p[1])
                    ctrl = (2 * x - pq[0], 2 * y - pq[1]) if pq else (x, y)
                    pq = ctrl
                for i in range(1, _BEZ_N + 1):
                    t = i / _BEZ_N
                    u = 1 - t
                    cur.append((u * u * x + 2 * u * t * ctrl[0] + t * t * e[0],
                                u * u * y + 2 * u * t * ctrl[1] + t * t * e[1]))
                x, y = e[0], e[1]
            continue
        else:
            continue
        for k in rest:
            x, y = (x + nums[k], y + nums[k + 1]) if rel else (nums[k], nums[k + 1])
            cur.append((x, y))
        if c == "M":
            pc = pq = None
    if cur:
        contours.append(cur)
    return [c for c in contours if len(c) >= 2]


def render_preview(shapes, out, ss=4):
    import numpy as np
    from PIL import Image

    def rgb(c):
        c = c.lstrip("#")
        return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))

    R, C = H * ss, W * ss
    canvas = np.zeros((R, C, 4), dtype=np.float64)
    ys = (np.arange(R) + 0.5) / ss
    for s in shapes:
        if s.get("stroke"):
            cont, col, a = [], rgb(s["stroke"]), s.get("strokeAlpha", 1.0)
            hw = s["strokeW"] / 2.0
            for pts in _flatten_path(s["d"]):
                for i in range(len(pts) - 1):
                    (x0, y0), (x1, y1) = pts[i], pts[i + 1]
                    dx, dy = x1 - x0, y1 - y0
                    L = (dx * dx + dy * dy) ** 0.5
                    if not L:
                        continue
                    nx, ny = -dy / L * hw, dx / L * hw
                    cont.append([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny),
                                 (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)])
        else:
            cont, col, a = _flatten_path(s["d"]), rgb(s["fill"]), s.get("alpha", 1.0)
        delta = np.zeros((R, C + 2))
        for pts in cont:
            n = len(pts)
            for i in range(n):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % n]
                if y0 == y1:
                    continue
                m = (ys >= min(y0, y1)) & (ys < max(y0, y1))
                if not m.any():
                    continue
                t = (ys[m] - y0) / (y1 - y0)
                cols = np.clip(np.ceil((x0 + t * (x1 - x0)) * ss - 0.5).astype(np.int64) + 1, 0, C + 1)
                np.add.at(delta, (np.nonzero(m)[0], cols), 1.0 if y1 > y0 else -1.0)
        mask = (np.cumsum(delta[:, :C], axis=1) != 0).astype(np.float64) * a
        for ch in range(3):
            canvas[..., ch] = canvas[..., ch] * (1 - mask) + col[ch] / 255.0 * mask
        canvas[..., 3] = canvas[..., 3] * (1 - mask) + mask
    img = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), "RGBA")
    img.resize((W, H), Image.LANCZOS).save(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--svg")
    ap.add_argument("--preview", help="本地光栅化预览输出 PNG（需 numpy + Pillow）")
    ap.add_argument("--out", default=OUT_XML)
    args = ap.parse_args()

    if not os.path.exists(FONT):
        sys.exit(f"找不到字体 {FONT}")
    title_d, ink = title_path()
    shapes = build_scene(title_d)
    dump_xml(shapes, args.out)
    print(f"写出 {os.path.relpath(args.out, ROOT)}  （{len(shapes)} 条 path）")
    print(f"字标墨迹框 x[{ink[0]:.1f},{ink[2]:.1f}] y[{ink[1]:.1f},{ink[3]:.1f}]"
          f"（宽 {ink[2] - ink[0]:.1f}）")
    if args.svg:
        dump_svg(shapes, args.svg)
        print(f"写出 SVG {args.svg}")
    if args.preview:
        render_preview(shapes, args.preview)
        print(f"写出预览 {args.preview}")


if __name__ == "__main__":
    main()
