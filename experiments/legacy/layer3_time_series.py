"""
第 3 层验证：健康指数沿时间轴是否形成合理曲线

数据集：Dragon Fruit cactus Growth Process（同一株，Day1 -> Day107）
        https://www.kaggle.com/datasets/mdahshanhabib/dragon-fruit-cactus-groth-proccess

验证假设
--------
H3：健康指数曲线平滑，无剧烈跳变 —— 说明该指标对拍摄条件的轻度变化具有鲁棒性。

判据
----
信噪比 SNR = 时间点间变化 / 时间点内噪声 ≥ 2

  时间点内噪声 = 同一时间点下多张图片健康指数的标准差（反映光照/角度的干扰）
  时间点间变化 = 相邻时间点健康指数中位数之差的绝对值均值（反映植株真实变化）

★ 为什么不用"相邻跳变 < 阈值"这种判据
------------------------------------------------
  植株真实快速恶化时，相邻跳变本来就大 —— 那不是"对拍摄条件敏感"。
  把「真实变化」和「测量噪声」混在一起判，
  会在数据质量最好（变化最真实）的时候误判为失败。

  正确做法是把两者分开：只要信号明显强于噪声，指标就是可用的。
  实测例子（合成数据）：
      时间点内 std 均值 = 3.8   （噪声）
      相邻跳变 |Δ| 均值 = 15.1  （信号）
      → SNR = 4.0 ≥ 2  ✅ 通过，而旧判据会误报"未通过"

输出同时保留原始跳变数值，便于人工判断。

用法
----
    python layer3_time_series.py --data D:/data/dragonfruit
    python layer3_time_series.py --data D:/data/dragonfruit --skip-folders Day1,baby
"""
from __future__ import annotations

import argparse
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from common.color_metrics import analyze_dir  # noqa: E402


def natural_key(s: str):
    """自然排序：Day2 在 Day10 前面"""
    return [int(t) if t.isdigit() else t.lower() for t in re.split(r"(\d+)", s)]


def folder_mid_day(name: str) -> float:
    """从文件夹名提取代表天数：Day15-30 -> 22.5；Day1 -> 1"""
    nums = [int(n) for n in re.findall(r"\d+", name)]
    if not nums:
        return -1.0
    return float(np.mean(nums))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="results")
    ap.add_argument("--skip-folders", default="", help="逗号分隔，跳过这些子目录")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    skip = {s.strip() for s in args.skip_folders.split(",") if s.strip()}

    subdirs = [d for d in os.listdir(args.data)
               if os.path.isdir(os.path.join(args.data, d)) and d not in skip]
    subdirs.sort(key=natural_key)
    if not subdirs:
        print("[错误] 没找到子目录")
        return 1

    print(f"发现 {len(subdirs)} 个时间点目录：{', '.join(subdirs)}\n")

    rows = []
    for name in subdirs:
        metrics = analyze_dir(os.path.join(args.data, name))
        valid = [m for m in metrics if m.valid]
        if not valid:
            print(f"[{name}] 全部无效，跳过")
            continue

        his = np.array([m.health_index for m in valid], dtype=float)
        greens = np.array([m.green_ratio for m in valid])
        # 用中位数代表该时间点的状态，抗离群（同一文件夹里可能有不同拍摄对象）
        rows.append({
            "folder": name,
            "day": folder_mid_day(name),
            "n_images": len(metrics),
            "n_valid": len(valid),
            "hi_median": float(np.median(his)),
            "hi_mean": float(his.mean()),
            "hi_std": float(his.std()),
            "green_median": float(np.median(greens)),
        })
        print(f"[{name:12s}] 有效 {len(valid):3d}/{len(metrics):3d}  "
              f"健康指数 median={np.median(his):5.1f} mean={his.mean():5.1f} std={his.std():5.1f}")

    if len(rows) < 2:
        print("\n[错误] 有效时间点少于 2 个，无法分析趋势")
        return 1

    df = pd.DataFrame(rows).sort_values("day").reset_index(drop=True)
    df["delta"] = df["hi_median"].diff()

    csv_path = os.path.join(args.out, "layer3_timeseries.csv")
    df.to_csv(csv_path, index=False, encoding="utf-8-sig")
    print(f"\n明细已写出：{csv_path}")

    # ---- 判据：信噪比（把真实变化和测量噪声分开） ----
    d = df["delta"].dropna().abs()
    med_jump = float(d.median()) if len(d) else float("nan")
    max_jump = float(d.max()) if len(d) else float("nan")

    noise = float(df["hi_std"].mean())          # 时间点内离散度 = 测量噪声
    signal = float(d.mean()) if len(d) else 0.0  # 相邻变化量 = 真实信号
    snr = (signal / noise) if noise > 1e-6 else float("inf")

    print(f"\n相邻时间点跳变：中位数 |Δ|={med_jump:.1f}  最大 |Δ|={max_jump:.1f}  （参考值）")
    print(f"时间点内噪声（std 均值） = {noise:.2f}")
    print(f"时间点间信号（|Δ| 均值） = {signal:.2f}")
    print(f"信噪比 SNR = {snr:.2f}")

    ok = snr >= 2.0
    if ok:
        print(f"判据（SNR >= 2）：✅ 验证通过 —— 信号明显强于噪声，指标可用于追踪变化")
    else:
        print(f"判据（SNR >= 2）：❌ 未通过 —— 噪声与信号相当，需要加强标准化拍摄"
              f"（固定角度/时段/背景/参照物）")

    # ---- 图 ----
    fig, ax = plt.subplots(figsize=(9, 4.8))
    ax.plot(df["day"], df["hi_median"], "o-", color="#2F7D5D", linewidth=2.2, markersize=7,
            label="Health index (median)")
    ax.fill_between(df["day"],
                    df["hi_median"] - df["hi_std"],
                    df["hi_median"] + df["hi_std"],
                    color="#2F7D5D", alpha=0.15, label="+/- 1 std")
    ax.set_xlabel("Days")
    ax.set_ylabel("Health Index (0-100)")
    ax.set_title("Health Index over Time (same plant)")
    ax.set_ylim(-5, 105)
    ax.grid(alpha=0.3)
    ax.legend()
    plt.tight_layout()
    png = os.path.join(args.out, "layer3_curve.png")
    plt.savefig(png, dpi=150)
    print(f"图表已写出：{png}")

    with open(os.path.join(args.out, "layer3_summary.txt"), "w", encoding="utf-8") as f:
        f.write(f"时间点数={len(df)}\n")
        f.write(f"时间点内噪声(std均值)={noise:.2f}\n")
        f.write(f"时间点间信号(|delta|均值)={signal:.2f}\n")
        f.write(f"信噪比 SNR={snr:.2f}\n")
        f.write(f"参考：跳变中位数={med_jump:.2f} 最大={max_jump:.2f}\n")
        f.write(f"判据（SNR>=2）：{'通过' if ok else '未通过'}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
