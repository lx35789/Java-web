"""
颜色指标提取 —— 所有实验层共用的核心模块

★ 与 Node 端 `_selftest.js` 使用完全一致的 OpenCV 8-bit HSV 约定：
      H ∈ [0,180)   S ∈ [0,255]   V ∈ [0,255]

设计要点
--------
1. 不做图像配准、不做分割模型。只用 HSV 阈值区分【绿色 / 黄色 / 褐色】。
   理由见 docs/13：SuperPoint+SuperGlue 针对刚体场景，植物是非刚体且正在变化，
   用它对齐后再测变化在逻辑上自相矛盾；YOLOv8-Seg 缺少家庭盆栽的标注数据。
2. 前景（植株区域）= 三类颜色掩码的并集。白墙、桌面等背景因为不落在任何
   颜色区间而自动被排除，无需额外的背景分割模型。
3. 所有指标都是【占比】而非绝对像素数 —— 这样对拍摄距离和植株大小不敏感。
"""

from __future__ import annotations

import glob
import os
from dataclasses import dataclass, asdict
from typing import Iterable

import cv2
import numpy as np

# ---------------------------------------------------------------- 颜色阈值
# OpenCV 的 H 是 0-180（标准色相角 / 2）
#   绿 ≈ 60（标准 120°）   黄 ≈ 30（标准 60°）   褐 ≈ 0-20（标准 0-40°）
GREEN_LOWER, GREEN_UPPER = (35, 40, 40), (85, 255, 255)
YELLOW_LOWER, YELLOW_UPPER = (20, 60, 60), (35, 255, 255)
BROWN_LOWER, BROWN_UPPER = (0, 60, 20), (20, 255, 160)

# 前景像素少于这个数，判定为无效图（拍糊了 / 没拍到植物）
MIN_PLANT_PIXELS = 500


@dataclass
class ColorMetrics:
    """单张图像的颜色指标"""
    path: str
    valid: bool
    plant_pixels: int
    green_ratio: float
    yellow_ratio: float
    brown_ratio: float
    health_index: int

    def to_dict(self) -> dict:
        return asdict(self)


def load_bgr(path: str) -> np.ndarray | None:
    """读图。用 np.fromfile 而不是 cv2.imread，避免中文路径在某些平台上失败。"""
    try:
        data = np.fromfile(path, dtype=np.uint8)
        img = cv2.imdecode(data, cv2.IMREAD_COLOR)
        return img
    except Exception:
        return None


def build_masks(img_bgr: np.ndarray) -> dict[str, np.ndarray]:
    """在 HSV 空间生成三类颜色掩码"""
    hsv = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2HSV)
    return {
        "green": cv2.inRange(hsv, np.array(GREEN_LOWER), np.array(GREEN_UPPER)),
        "yellow": cv2.inRange(hsv, np.array(YELLOW_LOWER), np.array(YELLOW_UPPER)),
        "brown": cv2.inRange(hsv, np.array(BROWN_LOWER), np.array(BROWN_UPPER)),
    }


def health_index(green_ratio: float, yellow_ratio: float, brown_ratio: float) -> int:
    """
    合成 0-100 健康指数

    ★ 已知局限（论文里要写）：在胁迫极端时会被 clamp 到 0，丧失区分度。
      如果需要更细的低端区分度，可以改成不 clamp、事后 min-max 归一化。
    """
    raw = green_ratio * 100 - yellow_ratio * 80 - brown_ratio * 120
    return int(round(max(0.0, min(100.0, raw))))


def analyze_image(path: str, min_plant_pixels: int = MIN_PLANT_PIXELS) -> ColorMetrics:
    """对单张图像算颜色指标"""
    img = load_bgr(path)
    if img is None:
        return ColorMetrics(path, False, 0, 0.0, 0.0, 0.0, 0)

    masks = build_masks(img)
    g = int(cv2.countNonZero(masks["green"]))
    y = int(cv2.countNonZero(masks["yellow"]))
    b = int(cv2.countNonZero(masks["brown"]))
    plant = g + y + b

    if plant < min_plant_pixels:
        return ColorMetrics(path, False, plant, 0.0, 0.0, 0.0, 0)

    gr, yr, br = g / plant, y / plant, b / plant
    return ColorMetrics(path, True, plant, gr, yr, br, health_index(gr, yr, br))


def analyze_dir(directory: str, exts: Iterable[str] = ("jpg", "jpeg", "png", "bmp")) -> list[ColorMetrics]:
    """递归分析一个目录下所有图片"""
    files: list[str] = []
    for e in exts:
        files.extend(glob.glob(os.path.join(directory, "**", f"*.{e}"), recursive=True))
        files.extend(glob.glob(os.path.join(directory, "**", f"*.{e.upper()}"), recursive=True))
    files = sorted(set(files))
    return [analyze_image(f) for f in files]


def health_index_from_image(path: str) -> int | None:
    """便捷函数：单图 -> 健康指数（无效返回 None）"""
    m = analyze_image(path)
    return m.health_index if m.valid else None


# ---------------------------------------------------------------- 自检
if __name__ == "__main__":
    # 与 Node 端 _selftest.js 的第 3 节对齐，确认两边算出来的数一致
    cases = [
        (1.0, 0.0, 0.0, 100),
        (0.0, 1.0, 0.0, 0),
        (0.0, 0.0, 1.0, 0),
        (0.7, 0.3, 0.0, 46),
        (0.9, 0.1, 0.0, 82),
        (0.8, 0.1, 0.1, 60),
    ]
    ok = 0
    for g, y, b, expect in cases:
        got = health_index(g, y, b)
        flag = "PASS" if got == expect else "FAIL"
        if got == expect:
            ok += 1
        print(f"  [{flag}] health_index({g}, {y}, {b}) = {got}  (expect {expect})")
    print(f"\n{ok}/{len(cases)} passed  —— 应与 Node 端 _selftest.js 结果一致")
