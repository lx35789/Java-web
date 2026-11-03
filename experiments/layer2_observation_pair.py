"""
第 2 层验证：两次观测对比，能否判别状态变化的方向

验证假设
--------
H2：过浇组的健康指数变化量 Δ 显著低于健康组（Δ = 后一次 − 前一次）。

这一步直接验证「判别靠观测对」这条铁律。

用法（两种模式）
----------------
模式 A —— 自动配对（数据集按时间点分了文件夹时用）：
    python layer2_observation_pair.py --data D:/data/chili --pairs-from-dirs

模式 B —— 用 CSV 指定配对（推荐，最稳）：
    python layer2_observation_pair.py --pairs pairs.csv

pairs.csv 格式（表头必须是这四列）：
    plant_id,group,t1_path,t2_path
    p001,healthy,D:/data/chili/healthy/w6_a.jpg,D:/data/chili/healthy/w7_a.jpg
    ...

怎么造这个 CSV
--------------
数据集结构不确定时，最省事的办法：
  1. 先跑 layer1，看 results/layer1_metrics.csv 里的 path 列
  2. 对照文件名，用 Excel 拼出 pairs.csv
  3. 只有两个时间点（weeks 6 和 7），手工拼几十对就够了
"""
from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from common.color_metrics import analyze_image  # noqa: E402
from common.mpl_compat import boxplot as compat_boxplot  # noqa: E402

GROUP_COLORS = {"healthy": "#2F7D5D", "overwatered": "#E6A23C", "bbwv2": "#F56C6C"}

# 自动配对时用于识别时间点子目录的关键词（按顺序即时间先后）
TIME_KEYWORDS = [["w6", "week6", "week_6", "t1"], ["w7", "week7", "week_7", "t2"]]


def auto_pairs(root: str) -> pd.DataFrame:
    """在同一分组目录下，按时间点关键词找出两批文件并配对"""
    rows = []
    for group in os.listdir(root):
        gdir = os.path.join(root, group)
        if not os.path.isdir(gdir):
            continue
        # 在分组目录及其子目录里找两批文件
        buckets = [[], []]
        for dirpath, _, filenames in os.walk(gdir):
            low = dirpath.lower()
            for i, kws in enumerate(TIME_KEYWORDS):
                if any(k in low for k in kws):
                    buckets[i].extend(os.path.join(dirpath, f) for f in sorted(filenames))
        if not buckets[0] or not buckets[1]:
            continue
        # 按文件名排序后一一配对（数量不等时取较小值）
        n = min(len(buckets[0]), len(buckets[1]))
        for i in range(n):
            rows.append({
                "plant_id": f"{group}_{i:03d}",
                "group": group,
                "t1_path": buckets[0][i],
                "t2_path": buckets[1][i],
            })
    return pd.DataFrame(rows)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", help="pairs.csv 路径")
    ap.add_argument("--data", help="数据集根目录（配合 --pairs-from-dirs）")
    ap.add_argument("--pairs-from-dirs", action="store_true", help="自动按时间点目录配对")
    ap.add_argument("--out", default="results")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)

    # ---- 取得配对表 ----
    if args.pairs:
        df = pd.read_csv(args.pairs)
        need = {"plant_id", "group", "t1_path", "t2_path"}
        if not need.issubset(df.columns):
            print(f"[错误] {args.pairs} 缺少列：{need - set(df.columns)}")
            return 1
    elif args.pairs_from_dirs and args.data:
        df = auto_pairs(args.data)
        if df.empty:
            print("[错误] 自动配对失败：没找到含 w6/w7 之类关键词的时间点目录。")
            print("       请改用 --pairs pairs.csv 手动指定。")
            return 1
        df.to_csv(os.path.join(args.out, "auto_pairs.csv"), index=False, encoding="utf-8-sig")
        print(f"自动配对了 {len(df)} 组，已写出 auto_pairs.csv（请人工核对一下）")
    else:
        ap.error("需要 --pairs 或 --data + --pairs-from-dirs")

    print(f"共 {len(df)} 组观测对\n")

    # ---- 逐对计算 Δ ----
    rows = []
    for _, r in df.iterrows():
        m1 = analyze_image(str(r["t1_path"]))
        m2 = analyze_image(str(r["t2_path"]))
        if not m1.valid or not m2.valid:
            rows.append({
                "plant_id": r["plant_id"], "group": r["group"], "valid": False,
                "hi1": np.nan, "hi2": np.nan, "delta": np.nan,
                "d_green": np.nan, "d_yellow": np.nan, "d_brown": np.nan,
            })
            continue
        rows.append({
            "plant_id": r["plant_id"], "group": r["group"], "valid": True,
            "hi1": m1.health_index, "hi2": m2.health_index,
            "delta": m2.health_index - m1.health_index,
            "d_green": round(m2.green_ratio - m1.green_ratio, 4),
            "d_yellow": round(m2.yellow_ratio - m1.yellow_ratio, 4),
            "d_brown": round(m2.brown_ratio - m1.brown_ratio, 4),
        })

    out = pd.DataFrame(rows)
    valid = out[out["valid"]].copy()
    dropped = len(out) - len(valid)
    if dropped:
        print(f"[注意] {dropped} 组因图像无效被丢弃\n")

    csv_path = os.path.join(args.out, "layer2_pairs.csv")
    valid.to_csv(csv_path, index=False, encoding="utf-8-sig")
    print(f"明细已写出：{csv_path}\n")

    # ---- 按组统计 Δ ----
    summary = []
    groups_present = [g for g in ["healthy", "overwatered", "bbwv2"] if g in set(valid["group"])]
    deltas: dict[str, np.ndarray] = {}

    for g in groups_present:
        d = valid[valid["group"] == g]["delta"].values.astype(float)
        deltas[g] = d
        worsen = int((d < 0).sum())
        line = (f"[{g:12s}] n={len(d):3d}  Δ mean={d.mean():+7.2f}  median={np.median(d):+7.2f}  "
                f"std={d.std():5.2f}  恶化比例={worsen}/{len(d)}={worsen / len(d):.0%}")
        print(line)
        summary.append(line)

    print()
    if "healthy" in deltas and "overwatered" in deltas:
        a, b = deltas["healthy"], deltas["overwatered"]
        try:
            from scipy.stats import mannwhitneyu
            u, p = mannwhitneyu(a, b, alternative="greater")
            line = f"健康组 Δ vs 过浇组 Δ：{a.mean():+.2f} vs {b.mean():+.2f}，p={p:.2e}"
            print(line)
            summary.append(line)
            verdict = "✅ 验证通过" if (a.mean() > b.mean() and p < 0.05) else "❌ 未通过"
        except ImportError:
            verdict = "⚠️ 未装 scipy，仅看均值差" if a.mean() > b.mean() else "❌ 未通过"
        line2 = f"判据（健康组 Δ > 过浇组 Δ 且 p<0.05）：{verdict}"
        print(line2)
        summary.append(line2)

    # ---- 图 ----
    if groups_present:
        fig, axes = plt.subplots(1, 2, figsize=(11, 4.6))

        data = [deltas[g] for g in groups_present]
        bp = compat_boxplot(axes[0], data, labels=groups_present, patch_artist=True, widths=0.55)
        for patch, g in zip(bp["boxes"], groups_present):
            patch.set_facecolor(GROUP_COLORS.get(g, "#909399"))
            patch.set_alpha(0.55)
        axes[0].axhline(0, color="#909399", linestyle="--", linewidth=1)
        axes[0].set_ylabel("Health Index change (t2 - t1)")
        axes[0].set_title("Change in Health Index")
        axes[0].grid(axis="y", alpha=0.3)

        for g in groups_present:
            d = deltas[g]
            axes[1].scatter(np.full(len(d), groups_present.index(g)) + np.random.uniform(-0.15, 0.15, len(d)),
                            d, color=GROUP_COLORS.get(g, "#909399"), alpha=0.7, s=28)
        axes[1].axhline(0, color="#909399", linestyle="--", linewidth=1)
        axes[1].set_xticks(range(len(groups_present)))
        axes[1].set_xticklabels(groups_present)
        axes[1].set_ylabel("Delta")
        axes[1].set_title("Per-plant Change")
        axes[1].grid(axis="y", alpha=0.3)

        plt.tight_layout()
        png = os.path.join(args.out, "layer2_delta.png")
        plt.savefig(png, dpi=150)
        print(f"图表已写出：{png}")
        summary.append(f"图表：{png}")

    with open(os.path.join(args.out, "layer2_summary.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
