"""
第 4 层验证（反向验证）：颜色指标能否区分「生理性胁迫」与「病理性病害」

这是一个【预期的负面结果】实验，用来论证设计决策：

    若颜色指标区分不了「过浇胁迫」与「病毒侵染」，
    则说明仅靠图像颜色不足以判断病因，
    → 从而为本文「症状诊断与病害识别分流处理、并主动追问」的设计提供实证支持。

数据集：Chili Pepper BBWV2 & Overwatering Stress（同 layer1）
        overwatered = 生理性，bbwv2 = 病理性

判据
----
AUC < 0.7  →  区分能力弱  →  ✅ 支持"必须分流 + 追问"的设计
AUC > 0.85 →  区分能力强  →  说明颜色指标已够用，分流设计的必要性下降（也要如实报告）

用法
----
    python layer4_symptom_vs_disease.py --data D:/data/chili
    python layer4_symptom_vs_disease.py --data D:/data/chili --groups overwatered=<p1> bbwv2=<p2>
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

from common.color_metrics import analyze_dir  # noqa: E402
from layer1_stress_discrimination import detect_groups, mann_whitney_auc  # noqa: E402


def auc_with_ci(a: np.ndarray, b: np.ndarray, n_boot: int = 2000, seed: int = 42):
    """AUC + 自助法 95% 置信区间"""
    _, auc = mann_whitney_auc(a, b)
    rng = np.random.default_rng(seed)
    boots = []
    for _ in range(n_boot):
        sa = rng.choice(a, size=len(a), replace=True)
        sb = rng.choice(b, size=len(b), replace=True)
        _, v = mann_whitney_auc(sa, sb)
        boots.append(v)
    lo, hi = np.percentile(boots, [2.5, 97.5])
    return auc, float(lo), float(hi)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", required=True)
    ap.add_argument("--out", default="results")
    ap.add_argument("--groups", nargs="*", default=None, help="name=path ...")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)

    groups = {}
    if args.groups:
        for item in args.groups:
            n, _, p = item.partition("=")
            groups[n] = p
    else:
        groups = detect_groups(args.data)

    if "overwatered" not in groups or "bbwv2" not in groups:
        print("[错误] 需要同时提供 overwatered 和 bbwv2 两个分组。")
        print(f"       当前识别到：{list(groups.keys())}")
        print("       可用 --groups overwatered=<path> bbwv2=<path> 手动指定。")
        return 1

    print("分组：")
    for k, v in groups.items():
        print(f"  {k:12s} -> {v}")
    print()

    feats = ["health_index", "green_ratio", "yellow_ratio", "brown_ratio"]
    data: dict[str, dict[str, np.ndarray]] = {}

    for g in ["overwatered", "bbwv2"]:
        ms = [m for m in analyze_dir(groups[g]) if m.valid]
        print(f"[{g}] 有效样本 {len(ms)}")
        data[g] = {
            "health_index": np.array([m.health_index for m in ms], dtype=float),
            "green_ratio": np.array([m.green_ratio for m in ms], dtype=float),
            "yellow_ratio": np.array([m.yellow_ratio for m in ms], dtype=float),
            "brown_ratio": np.array([m.brown_ratio for m in ms], dtype=float),
        }
    print()

    rows = []
    print(f"{'指标':<16}{'AUC':>8}{'95% CI':>20}{'判别力':>12}")
    print("-" * 58)
    for f in feats:
        a, b = data["overwatered"][f], data["bbwv2"][f]
        if len(a) == 0 or len(b) == 0:
            continue
        auc, lo, hi = auc_with_ci(a, b)
        power = "弱" if auc < 0.7 else ("中" if auc < 0.85 else "强")
        print(f"{f:<16}{auc:>8.3f}{f'[{lo:.3f}, {hi:.3f}]':>20}{power:>12}")
        rows.append({"feature": f, "auc": round(auc, 4), "ci_low": round(lo, 4),
                     "ci_high": round(hi, 4), "power": power})

    df = pd.DataFrame(rows)
    csv_path = os.path.join(args.out, "layer4_auc.csv")
    df.to_csv(csv_path, index=False, encoding="utf-8-sig")
    print(f"\n明细已写出：{csv_path}")

    # ---- 结论 ----
    best = df.loc[df["auc"].idxmax()] if len(df) else None
    summary = []
    if best is not None:
        if best["auc"] < 0.7:
            verdict = ("✅ 支持分流设计：颜色指标难以区分生理性与病理性异常，"
                       "必须依靠症状追问与独立的病害识别模块")
        elif best["auc"] > 0.85:
            verdict = (f"⚠️ 颜色指标区分力较强（AUC={best['auc']:.3f}），"
                       "需重新评估分流设计的必要性 —— 如实报告即可")
        else:
            verdict = (f"中等区分力（AUC={best['auc']:.3f}），"
                       "说明颜色指标有参考价值但不充分，仍需要分流机制")
        print(f"\n最佳单一指标：{best['feature']}  AUC={best['auc']:.3f}")
        print(f"结论：{verdict}")
        summary.append(f"最佳指标 {best['feature']} AUC={best['auc']:.3f}")
        summary.append(verdict)

        fig, ax = plt.subplots(figsize=(8, 4.6))
        colors = ["#2F7D5D" if v < 0.7 else ("#E6A23C" if v < 0.85 else "#F56C6C") for v in df["auc"]]
        ax.barh(df["feature"], df["auc"], color=colors, alpha=0.75, height=0.55)
        ax.axvline(0.7, color="#909399", linestyle="--", linewidth=1.2, label="AUC = 0.7")
        ax.axvline(0.5, color="#C8CDD4", linestyle=":", linewidth=1.2, label="random (0.5)")
        ax.set_xlim(0, 1)
        ax.set_xlabel("AUC (overwatered vs BBWV2)")
        ax.set_title("Discriminative Power of Color Metrics")
        ax.grid(axis="x", alpha=0.3)
        ax.legend(fontsize=8)
        plt.tight_layout()
        png = os.path.join(args.out, "layer4_auc.png")
        plt.savefig(png, dpi=150)
        print(f"图表已写出：{png}")
        summary.append(f"图表：{png}")

    with open(os.path.join(args.out, "layer4_summary.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
