"""
合成数据生成器 —— 让你在下载真实数据集之前，先把整条流水线跑通

用途
----
1. 验证环境装好了（opencv / numpy / pandas / scipy / matplotlib 都能 import）
2. 验证四个 layer 脚本的输入输出、CSV、图表都正常
3. 作为【流水线自检】，和真实数据结果分开报告

★ 合成数据的结果【没有科学意义】，只用来证明代码能跑。
   论文里绝对不能把合成数据的数字当实验结果。

生成结构
--------
    synthetic/
      healthy/          layer1 用（绿为主）
      overwatered/      layer1 用（黄褐多）
      bbwv2/            layer1 / layer4 用（介于两者，且色斑分布不同）
      ts/Day1 ... Day30/layer3 用（同一株随时间缓慢变差）
      pairs.csv         layer2 用（每组 20 对 w6/w7 配对）

用法
----
    python make_synthetic.py --out synthetic
    # 然后依次跑：
    python layer1_stress_discrimination.py --data synthetic --out results_syn
    python layer2_observation_pair.py --pairs synthetic/pairs.csv --out results_syn
    python layer3_time_series.py --data synthetic/ts --out results_syn
    python layer4_symptom_vs_disease.py --data synthetic --out results_syn
"""
from __future__ import annotations

import argparse
import os

import cv2
import numpy as np

# 三类"叶片颜色"的 BGR 值（OpenCV 是 BGR 顺序）
LEAF_GREEN = (34, 139, 34)    # 对应 RGB(34,139,34)
LEAF_YELLOW = (32, 165, 218)  # 对应 RGB(218,165,32)
LEAF_BROWN = (33, 67, 101)    # 对应 RGB(101,67,33)
BACKGROUND = (245, 245, 245)  # 接近白墙

H, W = 320, 320


def make_image(green_p: float, yellow_p: float, brown_p: float, seed: int, bg_p: float = 0.35) -> np.ndarray:
    """
    生成一张"盆栽照"：中央一个椭圆植株 + 白背景
    green_p / yellow_p / brown_p 是【植株区域内】三种颜色的占比
    """
    rng = np.random.default_rng(seed)
    img = np.full((H, W, 3), BACKGROUND, dtype=np.uint8)

    # 植株：中心椭圆
    yy, xx = np.mgrid[0:H, 0:W]
    cy, cx = H // 2, W // 2
    mask = (((yy - cy) / (H * 0.34)) ** 2 + ((xx - cx) / (W * 0.28)) ** 2) <= 1.0

    # 加一点噪声让边缘不那么完美
    noise = rng.random((H, W))
    mask &= noise > 0.03

    idx = np.where(mask)
    n = len(idx[0])
    if n == 0:
        return img

    # 按占比分配三类颜色
    total = green_p + yellow_p + brown_p
    if total <= 0:
        return img
    g_n = int(n * green_p / total)
    y_n = int(n * yellow_p / total)
    assign = np.array([0] * g_n + [1] * y_n + [2] * (n - g_n - y_n))
    rng.shuffle(assign)

    colors = np.array([LEAF_GREEN, LEAF_YELLOW, LEAF_BROWN], dtype=np.int16)
    # 加轻微亮度扰动，模拟真实光照不均
    jitter = rng.integers(-18, 18, size=(n, 1))
    px = np.clip(colors[assign] + jitter, 0, 255).astype(np.uint8)
    img[idx[0], idx[1]] = px
    return img


def save(path: str, img: np.ndarray) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    ok, buf = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), 92])
    if ok:
        buf.tofile(path)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="synthetic")
    ap.add_argument("--per-group", type=int, default=30, help="每组合成多少张")
    args = ap.parse_args()

    root = args.out
    n = args.per_group
    rng = np.random.default_rng(7)

    # ---- layer1 / layer4 用的三组 ----
    specs = {
        "healthy":     (0.88, 0.09, 0.03),   # 绿为主
        "overwatered": (0.42, 0.38, 0.20),   # 黄褐多（与过浇表型一致）
        "bbwv2":       (0.55, 0.14, 0.31),   # 褐为主、黄少 —— 与过浇的分布不同
    }
    for group, (g, y, b) in specs.items():
        for i in range(n):
            # 每组内部加个体差异
            gg = float(np.clip(g + rng.normal(0, 0.05), 0.05, 0.95))
            yy = float(np.clip(y + rng.normal(0, 0.04), 0.02, 0.60))
            bb = float(np.clip(b + rng.normal(0, 0.04), 0.01, 0.60))
            save(os.path.join(root, group, f"{group}_{i:03d}.jpg"),
                 make_image(gg, yy, bb, seed=hash((group, i)) % 10**6))
        print(f"  {group:12s} {n} 张")

    # ---- layer2 用的配对（每组 n 对，w6 -> w7 状态退化） ----
    pair_rows = ["plant_id,group,t1_path,t2_path"]
    for group, (g0, y0, b0), delta in [
        ("healthy",     (0.88, 0.09, 0.03), (-0.02, 0.01, 0.01)),   # 基本稳定
        ("overwatered", (0.70, 0.20, 0.10), (-0.30, 0.20, 0.10)),   # 明显恶化
    ]:
        for i in range(n):
            t1 = (float(np.clip(g0 + rng.normal(0, 0.04), 0.05, 0.95)),
                  float(np.clip(y0 + rng.normal(0, 0.03), 0.02, 0.60)),
                  float(np.clip(b0 + rng.normal(0, 0.03), 0.01, 0.60)))
            t2 = (float(np.clip(t1[0] + delta[0] + rng.normal(0, 0.03), 0.05, 0.95)),
                  float(np.clip(t1[1] + delta[1] + rng.normal(0, 0.03), 0.02, 0.60)),
                  float(np.clip(t1[2] + delta[2] + rng.normal(0, 0.03), 0.01, 0.60)))
            p1 = os.path.join(root, "pairs", f"{group}_{i:03d}_w6.jpg").replace("\\", "/")
            p2 = os.path.join(root, "pairs", f"{group}_{i:03d}_w7.jpg").replace("\\", "/")
            save(p1, make_image(*t1, seed=1000 + i))
            save(p2, make_image(*t2, seed=2000 + i))
            pair_rows.append(f"{group}_{i:03d},{group},{p1},{p2}")
    with open(os.path.join(root, "pairs.csv"), "w", encoding="utf-8") as f:
        f.write("\n".join(pair_rows) + "\n")
    print(f"  pairs.csv    {n * 2} 对")

    # ---- layer3 用的时间序列（同一株缓慢变差再恢复） ----
    days = list(range(1, 31, 3))  # 1,4,7,...,28
    for k, day in enumerate(days):
        # 抛物线：先缓慢变差，后略有恢复
        t = k / max(1, len(days) - 1)
        g = 0.90 - 0.45 * (1 - (2 * t - 1) ** 2)
        y = 0.07 + 0.25 * (1 - (2 * t - 1) ** 2)
        b = 0.03 + 0.20 * (1 - (2 * t - 1) ** 2)
        for j in range(5):
            save(os.path.join(root, "ts", f"Day{day}", f"img_{j}.jpg"),
                 make_image(float(np.clip(g + rng.normal(0, 0.03), 0.05, 0.95)),
                            float(np.clip(y + rng.normal(0, 0.02), 0.02, 0.60)),
                            float(np.clip(b + rng.normal(0, 0.02), 0.01, 0.60)),
                            seed=5000 + day * 10 + j))
    print(f"  ts/          {len(days)} 个时间点 x 5 张")

    print(f"\n合成数据已生成到：{os.path.abspath(root)}")
    print("注意：合成数据只用于验证流水线能跑通，结果没有科学意义。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
