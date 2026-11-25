# -*- coding: utf-8 -*-
"""兰花模型域偏移压力测试：把黑底特写贴到真实背景上、并缩小叶片，看还能不能认出来。

为什么需要
----------
兰花训练数据是「叶片填满画面的黑底特写」。真实用户拍的照片是：
    · 整株、带花、带花盆、带窗台背景
    · 叶片只占画面一小块（常常 20%~40%）

同源测试集 98%~100% 说明不了这些。这个脚本只做一件事：把留出测试集里的
特写贴到真实照片背景上，叶片分别缩到 100% / 60% / 40%，看准确率怎么掉。
掉得厉害就说明模型在依赖「叶片占满画面」这个训练集专属特征。

用法
----
    python stress_orchid_domain.py --model-dir experiments/data/orchid_v4_model \
        --bg-dir D:/indoor-plant-data/main --scales 1.0 0.6 0.4
"""
from __future__ import annotations

import argparse
import io
import json
import random
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "ai-service"))

from app.disease import DiseaseClassifier  # noqa: E402

EXTS = (".jpg", ".jpeg", ".png")


def load_bgs(bg_dir: Path, limit: int = 60, max_side: int = 512):
    out = []
    if not bg_dir or not bg_dir.is_dir():
        return out
    for p in bg_dir.rglob("*"):
        if p.suffix.lower() in EXTS:
            try:
                im = Image.open(p).convert("RGB")
                k = max_side / max(im.size)
                if k < 1:
                    im = im.resize((int(im.width * k), int(im.height * k)), Image.BILINEAR)
                out.append(im)
            except Exception:
                pass
            if len(out) >= limit:
                break
    return out


def composite(leaf: Image.Image, bgs, scale: float, size: int = 224) -> Image.Image:
    """把黑底叶片按 scale 贴到随机真实背景上，输出 size×size。"""
    canvas = Image.new("RGB", (size, size), (120, 120, 120))
    if bgs:
        b = random.choice(bgs)
        k = max(size / b.width, size / b.height) * random.uniform(1.0, 1.6)
        b = b.resize((max(size, int(b.width * k)), max(size, int(b.height * k))), Image.BILINEAR)
        x = random.randint(0, max(0, b.width - size))
        y = random.randint(0, max(0, b.height - size))
        canvas = b.crop((x, y, x + size, y + size))

    arr = np.asarray(leaf.convert("RGB"))
    mask = arr.max(axis=2) >= 25
    if not mask.any():
        return canvas
    ys, xs = np.where(mask)
    leaf = leaf.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))

    side = int(size * scale)
    k = side / max(leaf.size)
    leaf = leaf.resize((max(6, int(leaf.width * k)), max(6, int(leaf.height * k))), Image.BILINEAR)
    m = np.asarray(leaf.convert("RGB")).max(axis=2) >= 25
    mimg = Image.fromarray((m * 255).astype(np.uint8)).convert("L")
    canvas.paste(leaf, (random.randint(0, max(0, size - leaf.width)),
                        random.randint(0, max(0, size - leaf.height))), mimg)
    return canvas


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True, type=Path)
    ap.add_argument("--bg-dir", type=Path, default=None)
    ap.add_argument("--scales", nargs="*", type=float, default=[1.0, 0.6, 0.4])
    ap.add_argument("--repeat", type=int, default=2, help="同一张图用几个不同背景重复")
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()

    random.seed(args.seed)
    md = args.model_dir if args.model_dir.is_absolute() else (ROOT / args.model_dir)
    clf = DiseaseClassifier("orchid", md)
    split = json.loads((md / "split.json").read_text(encoding="utf-8"))
    paths = [(ROOT / p) if not Path(p).is_absolute() else Path(p) for p in split["test"]]
    # 只压测黑底特写，真实照片本来就有背景
    paths = [p for p in paths if p.name.startswith("g") and p.exists()]
    bgs = load_bgs(args.bg_dir if args.bg_dir else None)
    print("特写测试图 %d 张，背景素材 %d 张（0 = 只用纯色底）" % (len(paths), len(bgs)))

    for scale in args.scales:
        for rep in range(args.repeat):
            hit = n = 0
            wrong = Counter()
            for p in paths:
                try:
                    im = Image.open(p).convert("RGB")
                except Exception:
                    continue
                canvas = composite(im, bgs, scale)
                buf = io.BytesIO()
                canvas.save(buf, "JPEG", quality=90)
                r = clf.predict(buf.getvalue(), top_k=1)
                top = r["topK"][0]["code"]
                true = p.parent.name
                n += 1
                hit += int(top == true)
                if top != true:
                    wrong[true.split("_")[-1] + "→" + top.split("_")[-1]] += 1
            print("  叶片占画面 %3.0f%%  第%d次  top1=%.4f (%d/%d)  %s"
                  % (scale * 100, rep + 1, hit / max(n, 1), hit, n,
                     "，".join("%s×%d" % (k, v) for k, v in wrong.most_common(4))))


if __name__ == "__main__":
    main()
