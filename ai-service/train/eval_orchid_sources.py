# -*- coding: utf-8 -*-
"""兰花模型分源评测：把「黑底特写」和「真实照片」分开算，避免一个好数字掩盖另一个崩掉。

为什么必须分开
--------------
兰花数据有两个来源，域完全不同：

    黑底特写   来自 KhaeruzZahra/orchid_disease，443 张，同源同拍摄条件
    真实照片   来自 iNaturalist（CC0/CC BY），带花、带真实背景

合在一起算一个准确率会出事：真实照片若 100% 判成「日灼」，只要特写够多，
总分照样漂亮。所以这里按文件名前缀拆开（`real_` = iNat，`g` = 特写），
另外单独跑一个「只含健康真实照片」的留出目录，看误报率。

用法
----
    python eval_orchid_sources.py --model-dir experiments/data/orchid_v4_model \
        --real-dir experiments/data/orchid_real_eval/Orchid_Healthy
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-service"))

from app.disease import DiseaseClassifier  # noqa: E402


def source_of(path: Path) -> str:
    n = path.name
    if n.startswith("real_"):
        return "real"
    if n.startswith("g") and "__" in n:
        return "closeup"
    return "other"


def run(clf, paths, expect=None):
    """返回 [(true_label, pred_code, conf, source)]"""
    rows = []
    for p in paths:
        try:
            r = clf.predict(p.read_bytes(), top_k=1)
            top = r["topK"][0]
            rows.append((expect or p.parent.name, top["code"], top["confidence"], source_of(p)))
        except Exception as e:
            print("  !! %s 推理失败 %r" % (p.name, e))
    return rows


def report(rows, title):
    if not rows:
        print("\n%s：无样本" % title)
        return
    by_src = defaultdict(list)
    for r in rows:
        by_src[r[3]].append(r)
    print("\n%s（%d 张）" % (title, len(rows)))
    for src in sorted(by_src):
        rs = by_src[src]
        hit = sum(1 for t, p, c, s in rs if t == p)
        conf = sum(c for _, _, c, _ in rs) / len(rs)
        print("  %-9s n=%3d  top1=%.4f  mean_conf=%.3f" % (src, len(rs), hit / len(rs), conf))
        wrong = Counter("%s→%s" % (t.split("_")[-1], p.split("_")[-1])
                        for t, p, c, s in rs if t != p)
        if wrong:
            print("        错判：%s" % "，".join("%s×%d" % (k, v) for k, v in wrong.most_common(5)))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True, type=Path)
    ap.add_argument("--real-dir", type=Path, default=None,
                    help="只含健康真实照片的留出目录（不进训练）")
    args = ap.parse_args()

    md = args.model_dir if args.model_dir.is_absolute() else (ROOT / args.model_dir)
    clf = DiseaseClassifier("orchid", md)
    if not clf.available:
        print("!! 模型不可用：%s" % clf.reason)
        sys.exit(1)

    split = json.loads((md / "split.json").read_text(encoding="utf-8"))
    test_paths = [(ROOT / p) if not Path(p).is_absolute() else Path(p) for p in split["test"]]
    test_paths = [p for p in test_paths if p.exists()]
    report(run(clf, test_paths), "留出测试集（模型自带切分）")

    if args.real_dir:
        d = args.real_dir if args.real_dir.is_absolute() else (ROOT / args.real_dir)
        paths = sorted(p for p in d.glob("*") if p.suffix.lower() in (".jpg", ".jpeg", ".png"))
        rows = run(clf, paths, expect="Orchid_Healthy")
        report(rows, "真实照片留出集（全部为健康植株，误报率越低越好）")
        fp = sum(1 for t, p, c, s in rows if p != t)
        if rows:
            print("\n  → 健康误报 %d/%d = %.1f%%（被判成病害的比例，越低越好）"
                  % (fp, len(rows), 100.0 * fp / len(rows)))


if __name__ == "__main__":
    main()
