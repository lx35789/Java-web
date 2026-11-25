"""
植物状态照片分析 —— 只回答「这株植物现在看起来怎么样」，不判断病害。

为什么不用分割模型：家庭盆栽没有带掩膜的标注数据，而 HSV 阈值在这个
场景已经够用 —— 白墙、木桌、瓷砖都不落在黄绿褐三个区间里，天然被排除，
等于免费得到了前景掩膜。所有指标都用「占比」而不是绝对像素数，
这样对拍摄距离和植株大小不敏感（同一盆植物拉远拍，占比基本不变）。

算法与 experiments/legacy/common/color_metrics.py 保持一致：
OpenCV 的 H 是 0-180，绿≈60、黄≈30、褐≈0-20。
"""
from __future__ import annotations

import io

import cv2
import numpy as np
from PIL import Image

# 三个颜色区间（OpenCV 8-bit HSV 约定）
GREEN_LOWER, GREEN_UPPER = (35, 40, 40), (85, 255, 255)
YELLOW_LOWER, YELLOW_UPPER = (20, 60, 60), (35, 255, 255)
BROWN_LOWER, BROWN_UPPER = (0, 60, 20), (20, 255, 160)

# 前景像素少于此值：拍糊了 / 没拍到植株 / 全白
MIN_PLANT_PIXELS = 500

# 分析前先缩小，长边超过这个值对占比没有增益，只是浪费 CPU
MAX_SIDE = 640


def _clamp(v: float, lo: int = 0, hi: int = 100) -> int:
    return int(max(lo, min(hi, round(v))))


def analyze(raw: bytes) -> dict:
    """输入图片字节，返回颜色占比与健康提示。"""
    try:
        img = Image.open(io.BytesIO(raw)).convert("RGB")
    except Exception:
        return {"available": False, "reason": "图片无法解码"}

    w, h = img.size
    scale = min(1.0, MAX_SIDE / max(w, h))
    if scale < 1.0:
        img = img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)

    rgb = np.asarray(img)
    hsv = cv2.cvtColor(rgb, cv2.COLOR_RGB2HSV)

    def mask(lo, hi):
        return cv2.inRange(hsv, np.array(lo, np.uint8), np.array(hi, np.uint8)) > 0

    m_g = mask(GREEN_LOWER, GREEN_UPPER)
    m_y = mask(YELLOW_LOWER, YELLOW_UPPER)
    m_b = mask(BROWN_LOWER, BROWN_UPPER)

    n_g, n_y, n_b = int(m_g.sum()), int(m_y.sum()), int(m_b.sum())
    total = n_g + n_y + n_b

    if total < MIN_PLANT_PIXELS:
        return {
            "available": False,
            "reason": "画面里没找到足够的叶片像素（可能没拍到植株，或光线太暗）",
            "plantPixels": total,
        }

    g, y, b = n_g / total, n_y / total, n_b / total

    # 褐色权重最高（枯焦基本不可逆），黄色次之，绿色为正
    hint = _clamp(100 * (g - 0.9 * y - 1.3 * b) / max(1e-6, g + y + b))

    advice = []
    if b > 0.25:
        advice.append("褐色占比偏高，注意枯焦或腐烂")
    if y > 0.30:
        advice.append("黄色占比偏高，常见于浇水不当或缺素")
    if g > 0.85:
        advice.append("叶色正常")

    return {
        "available": True,
        "reason": None,
        "greenRatio": round(g, 4),
        "yellowRatio": round(y, 4),
        "brownRatio": round(b, 4),
        "plantPixels": total,
        "healthHint": hint,
        "advice": advice,
    }
