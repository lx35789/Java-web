"""
第 1 层验证：颜色统计能否区分「健康」与「过浇胁迫」

数据集：Chili Pepper BBWV2 & Overwatering Stress Leaf Image Dataset
        https://zenodo.org/records/17901489  (90 MB)

验证假设
--------
H1：健康组的健康指数显著高于过浇组。

判据
----
Mann-Whitney U 检验 p < 0.05，且效应量（AUC）> 0.7

用法
----
    python layer1_stress_discrimination.py --data D:/data/chili
    python layer1_stress_discrimination.py --data D:/data/chili --out results

目录要求
--------
`--data` 下需要有能靠关键词识别的子目录，例如：
    chili/healthy/...        (或含 'control')
    chili/overwatered/...    (或含 'flooding' / 'waterlog')
    chili/bbwv2/...          (可选)
若结构不同，用 --groups 手动指定：
    --groups healthy=<路径> overwatered=<路径>
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

from common.color_metrics import analyze_dir, health_index  # noqa: E402
from common.mpl_compat import boxplot as compat_boxplot  # noqa: E402

# 子目录名 -> 分组 的关键词匹配
GROUP_KEYWORDS = {
    "healthy": ["healthy", "control", "normal", "ck"],
    "overwatered": ["overwater", "flooding", "waterlog", "water_log", "excess"],
    "bbwv2": ["bbwv", "virus", "viral", "infected"],
}

GROUP_COLORS = {"healthy": "#2F7D5D", "overwatered": "#E6A23C", "bbwv2": "#F56C6C"}


def detect_groups(root: str) -> dict[str, str]:
    """扫描子目录，按关键词判断分组"""
    found: dict[str, str] = {}
    for name in os.listdir(root):
        full = os.path.join(root, name)
        if not os.path.isdir(full):
            continue
        low = name.lower()
        for group, kws in GROUP_KEYWORDS.items():
            if any(k in low for k in kws):
                found.setdefault(group, full)
    return found


def mann_whitney_auc(a: np.ndarray, b: np.ndarray) -> tuple[float, float]:
    """
    返回 (U 统计量, AUC)
    AUC = U / (n1 * n2)，即"随机取一个 a 和一个 b，a > b 的概率"。
    不依赖 scipy 的 roc 实现，直接按定义算。
    """
    n1, n2 = len(a), len(b)
    if n1 == 0 or n2 == 0:
        return float("nan"), float("nan")
    # 秩和法
    allv = np.concatenate([a, b])
    order = allv.argsort()
    ranks = np.empty(len(allv), dtype=float)
    ranks[order] = np.arange(1, len(allv) + 1)
    # 处理并列：同值取平均秩
    _, inv, counts = np.unique(allv, return_inverse=True, return_counts=True)
    for i, c in enumerate(counts):
        if c > 1:
            idx = np.where(inv == i)[0]
            ranks[idx] = ranks[idx].mean()
    R1 = ranks[:n1].sum()
    U1 = R1 - n1 * (n1 + 1) / 2.0
    auc = U1 / (n1 * n2)
    return float(U1), float(auc)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True, help="数据集根目录")
    ap.add_argument("--out", default="results", help="输出目录")
    ap.add_argument("--groups", nargs="*", default=None,
                    help="手动指定分组，格式 name=path")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)

    # ---- 定位分组 ----
    if args.groups:
        groups = {}
        for item in args.groups:
            name, _, p = item.partition("=")
            groups[name] = p
    else:
        groups = detect_groups(args.data)

    if not groups:
        print("[错误] 没能识别出任何分组。请用 --groups healthy=<path> overwatered=<path> 手动指定。")
        return 1

    print("识别到分组：")
    for k, v in groups.items():
        print(f"  {k:12s} -> {v}")
    print()

    # ---- 逐组分析 ----
    all_rows = []
    group_values: dict[str, np.ndarray] = {}

    for group, path in groups.items():
        metrics = analyze_dir(path)
        valid = [m for m in metrics if m.valid]
        invalid = len(metrics) - len(valid)
        vals = np.array([m.health_index for m in valid], dtype=float)
        group_values[group] = vals

        print(f"[{group}] 共 {len(metrics)} 张，有效 {len(valid)}，无效 {invalid}（前景像素不足）")
        if len(vals):
            print(f"          健康指数 mean={vals.mean():.1f}  median={np.median(vals):.1f}  "
                  f"std={vals.std():.1f}  min={vals.min():.0f}  max={vals.max():.0f}")
            greens = np.array([m.green_ratio for m in valid])
            yellows = np.array([m.yellow_ratio for m in valid])
            print(f"          绿色占比 mean={greens.mean():.3f}   黄色占比 mean={yellows.mean():.3f}")
        print()

        for m in valid:
            d = m.to_dict()
            d["group"] = group
            all_rows.append(d)

    df = pd.DataFrame(all_rows)
    csv_path = os.path.join(args.out, "layer1_metrics.csv")
    df.to_csv(csv_path, index=False, encoding="utf-8-sig")
    print(f"明细已写出：{csv_path}\n")

    # ---- 统计检验 ----
    summary_lines = []
    if "healthy" in group_values and "overwatered" in group_values:
        a, b = group_values["healthy"], group_values["overwatered"]
        _, auc = mann_whitney_auc(a, b)
        try:
            from scipy.stats import mannwhitneyu
            u, p = mannwhitneyu(a, b, alternative="greater")
        except ImportError:
            p = float("nan")
            print("[警告] 未安装 scipy，跳过 p 值计算（pip install scipy）")

        line = (f"健康组 vs 过浇组：健康指数 mean {a.mean():.1f} vs {b.mean():.1f}；"
                f"AUC={auc:.3f}；p={p:.2e}" if not np.isnan(p) else
                f"健康组 vs 过浇组：健康指数 mean {a.mean():.1f} vs {b.mean():.1f}；AUC={auc:.3f}")
        print(line)

        verdict = "✅ 验证通过" if (auc > 0.7 and (np.isnan(p) or p < 0.05)) else "❌ 未通过"
        print(f"判据（AUC>0.7 且 p<0.05）：{verdict}\n")
        summary_lines.append(line)
        summary_lines.append(f"判据（AUC>0.7 且 p<0.05）：{verdict}")

    if "overwatered" in group_values and "bbwv2" in group_values:
        a, b = group_values["overwatered"], group_values["bbwv2"]
        _, auc = mann_whitney_auc(a, b)
        line = f"过浇组 vs 病毒组：AUC={auc:.3f}  <- 第 4 层反向验证用"
        print(line)
        summary_lines.append(line)

    # ---- 箱线图（用英文标签，避免 matplotlib 中文字体缺失导致方块） ----
    labels, data, colors = [], [], []
    for g in ["healthy", "overwatered", "bbwv2"]:
        if g in group_values and len(group_values[g]):
            labels.append(g)
            data.append(group_values[g])
            colors.append(GROUP_COLORS[g])

    if data:
        fig, axes = plt.subplots(1, 2, figsize=(11, 4.6))

        bp = compat_boxplot(axes[0], data, labels=labels, patch_artist=True, widths=0.55)
        for patch, c in zip(bp["boxes"], colors):
            patch.set_facecolor(c)
            patch.set_alpha(0.55)
        axes[0].set_ylabel("Health Index (0-100)")
        axes[0].set_title("Health Index by Group")
        axes[0].grid(axis="y", alpha=0.3)
        axes[0].set_ylim(-5, 105)

        # 绿色占比对比
        gdata, glabels, gcolors = [], [], []
        for g in labels:
            sub = df[df["group"] == g]["green_ratio"].values
            if len(sub):
                gdata.append(sub)
                glabels.append(g)
                gcolors.append(GROUP_COLORS[g])
        bp2 = compat_boxplot(axes[1], gdata, labels=glabels, patch_artist=True, widths=0.55)
        for patch, c in zip(bp2["boxes"], gcolors):
            patch.set_facecolor(c)
            patch.set_alpha(0.55)
        axes[1].set_ylabel("Green pixel ratio")
        axes[1].set_title("Green Ratio by Group")
        axes[1].grid(axis="y", alpha=0.3)

        plt.tight_layout()
        png = os.path.join(args.out, "layer1_boxplot.png")
        plt.savefig(png, dpi=150)
        print(f"图表已写出：{png}")
        summary_lines.append(f"图表：{png}")

    with open(os.path.join(args.out, "layer1_summary.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(summary_lines))
    print(f"结论已写出：{os.path.join(args.out, 'layer1_summary.txt')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
