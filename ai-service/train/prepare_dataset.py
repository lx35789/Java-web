"""
植语助手 · 数据集合并与切分
============================

把从不同来源下载的原始数据集，合并成 train_disease.py 需要的 ImageFolder 结构。

为什么需要这个脚本
------------------
我们的数据来自 4 个公开源，目录结构各不相同：

    Aloe Vera (Mendeley)   ->  可能是 "Aloe Vera Leaf Disease Detection Dataset/<类别>/*.jpg"
    Cactus (Mendeley)      ->  可能是 "Cactus/<类别>/*.jpg"
    Roboflow 导出          ->  "train/<类别>/*.jpg" + "valid/..." + "test/..."
    PlantVillage (天池)    ->  "color/<物种>___<病害>/*.jpg"，本身已规范

本脚本用「自动发现」处理这些差异：递归扫描，把**所有直接含图片的目录**当作一个类别，
用目录名推断类别，再做名称归一化，最后统一重新切分。

⚠️ 为什么要重新切分而不是沿用原始划分：
   Roboflow 的导出自带 train/valid/test，如果把多个源混在一起直接用它们的划分，
   同一株植物的照片可能同时出现在训练集和测试集里 —— 这叫数据泄漏，
   会让测试准确率虚高。统一重新切分可以避免。

用法
----
# 1) 先只看看会识别出哪些类别（不复制文件，秒出）
python prepare_dataset.py --scan-only ^
    --src aloe=D:/datasets/aloe ^
    --src cactus=D:/datasets/cactus ^
    --src roboflow=D:/datasets/roboflow

# 2) 确认类别名没问题后，正式执行
python prepare_dataset.py ^
    --src aloe=D:/datasets/aloe ^
    --src cactus=D:/datasets/cactus ^
    --src roboflow=D:/datasets/roboflow ^
    --out D:/datasets/indoor-plant ^
    --dedupe

# 3) PlantVillage 单独出一份，做对照实验
python prepare_dataset.py ^
    --src pv=D:/datasets/plantvillage/color ^
    --out D:/datasets/plantvillage-split

输出结构
--------
    <out>/
      train/<Species>___<Disease>/xxx.jpg
      valid/<Species>___<Disease>/xxx.jpg
      test/<Species>___<Disease>/xxx.jpg
      dataset_report.txt         每个类别的样本数与来源
"""
from __future__ import annotations

import argparse
import hashlib
import os
import random
import re
import shutil
import sys
from collections import defaultdict
from pathlib import Path

IMG_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}
SEED = 42

# ---------------------------------------------------------------------------
# 目录名归一化：把各来源五花八门的写法统一成 `Species___Disease`
# 规则按顺序匹配，命中就停。
# ---------------------------------------------------------------------------
SPECIES_PATTERNS = [
    (r"aloe[\s_-]*vera|^aloe$", "Aloe_Vera"),
    (r"cactus|opuntia|prickly", "Cactus"),
    (r"money[\s_-]*plant|pothos|epipremnum|devil", "Pothos"),      # 绿萝
    (r"snake[\s_-]*plant|sansevieria|dracaena", "Snake_Plant"),    # 虎皮兰
    (r"spider[\s_-]*plant|chlorophytum", "Spider_Plant"),          # 吊兰
]

DISEASE_PATTERNS = [
    (r"healthy|normal|health", "Healthy"),
    (r"rust", "Rust"),
    (r"anthracnose", "Anthracnose"),
    (r"leaf[\s_-]*spot|fungal[\s_-]*leaf", "Leaf_Spot"),
    (r"sun[\s_-]*burn|sunburn", "SunBurn"),
    (r"cochineal|dactylopius", "Cochineal"),
    (r"wither", "Leaf_Withering"),
    (r"tip[\s_-]*necrosis|necrosis", "Leaf_Tip_Necrosis"),
    (r"bacterial[\s_-]*wilt|wilt", "Bacterial_Wilt"),
    (r"manganese", "Manganese_Toxicity"),
]


def norm(name: str, patterns, fallback: str) -> str:
    low = name.lower()
    for pat, out in patterns:
        if re.search(pat, low):
            return out
    # 兜底：去掉非法字符，保留原意
    cleaned = re.sub(r"[^0-9A-Za-z]+", "_", name).strip("_")
    return cleaned or fallback


def class_name_from_dir(dirname: str) -> str:
    """把目录名变成 `Species___Disease`。已经带 ___ 的直接沿用（PlantVillage 就是这种）。"""
    if "___" in dirname:
        sp, _, dz = dirname.partition("___")
        return f"{norm(sp, SPECIES_PATTERNS, 'Unknown')}___{norm(dz, DISEASE_PATTERNS, 'Unknown')}"

    # 形如 "Aloe Vera - Rust" / "aloe_rust" / "Healthy Aloe"
    for sep in [" - ", "-", "_", " "]:
        if sep in dirname:
            parts = [p for p in dirname.split(sep) if p]
            sp = next((p for p in parts if re.search(SPECIES_PATTERNS[0][0], p.lower())
                       or any(re.search(pat, p.lower()) for pat, _ in SPECIES_PATTERNS)), None)
            if sp:
                rest = " ".join(p for p in parts if p != sp)
                return f"{norm(sp, SPECIES_PATTERNS, 'Unknown')}___{norm(rest or dirname, DISEASE_PATTERNS, 'Unknown')}"

    # 只有一个词（比如 "Healthy"），物种交给 --species-prefix
    return f"__ONLY__{norm(dirname, DISEASE_PATTERNS, 'Unknown')}"


def find_class_dirs(root: Path):
    """递归找出所有「直接包含图片」的目录。"""
    hits = []
    for dirpath, _, filenames in os.walk(root):
        imgs = [f for f in filenames if Path(f).suffix.lower() in IMG_EXT]
        if imgs:
            hits.append((Path(dirpath), imgs))
    return hits


def sha1(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", action="append", required=True,
                    help="来源，格式 name=目录，可重复。例：--src aloe=D:/datasets/aloe")
    ap.add_argument("--out", help="输出目录（--scan-only 时可省略）")
    ap.add_argument("--species", action="append", default=[],
                    help="给「某个来源」补物种名，格式 来源名=物种名，可重复。\n"
                         "用于该来源的文件夹名里只有病害名（如 Healthy / Cochineal）的情况。\n"
                         "例：--species cactus=Cactus")
    ap.add_argument("--scan-only", action="store_true", help="只扫描并打印类别，不复制文件")
    ap.add_argument("--dedupe", action="store_true", help="按内容哈希去重（慢，但能清掉增强图重复）")
    ap.add_argument("--train", type=float, default=0.7)
    ap.add_argument("--valid", type=float, default=0.15)
    ap.add_argument("--seed", type=int, default=SEED)
    args = ap.parse_args()

    random.seed(args.seed)

    # 来源 -> 物种名（仅用于「文件夹名里只有病害名」的来源）
    species_by_src: dict[str, str] = {}
    for kv in args.species:
        k, _, v = kv.partition("=")
        if not v.strip():
            sys.exit(f"--species 格式应为 来源名=物种名，收到：{kv}")
        species_by_src[k.strip()] = v.strip()

    # ---------------- 1. 扫描 ----------------
    buckets: dict[str, list[tuple[str, Path]]] = defaultdict(list)   # 类别 -> [(来源, 文件)]
    unlabeled: list[str] = []      # 只有病害名、又没配物种名的目录

    for spec in args.src:
        if "=" not in spec:
            sys.exit(f"--src 格式应为 name=目录，收到：{spec}")
        name, path = spec.split("=", 1)
        name = name.strip()
        root = Path(path)
        if not root.exists():
            print(f"[跳过] 来源 {name} 目录不存在：{root}")
            continue

        prefix = species_by_src.get(name)
        for d, files in find_class_dirs(root):
            cls = class_name_from_dir(d.name)
            if cls.startswith("__ONLY__"):
                if not prefix:
                    unlabeled.append(f'来源「{name}」的 {d}   (文件夹名「{d.name}」只有病害名)')
                    continue
                cls = f"{prefix}___{cls[len('__ONLY__'):]}"
            for f in files:
                buckets[cls].append((name, d / f))

    if unlabeled:
        print("\n[错误] 下面这些文件夹的名字里只有病害名，推断不出物种：")
        for u in unlabeled:
            print("   " + u)
        names = sorted({u.split("「")[1].split("」")[0] for u in unlabeled})
        print("\n请给这些来源补 --species 参数，例如：")
        for n in names:
            print(f"   --species {n}=<这个来源的物种名>")
        sys.exit(1)

    if not buckets:
        sys.exit("没有扫到任何图片，检查 --src 路径")

    print(f"\n{'类别':<42}{'总数':>7}   来源分布")
    print("-" * 78)
    for cls in sorted(buckets):
        per_src = defaultdict(int)
        for s, _ in buckets[cls]:
            per_src[s] += 1
        src_txt = "  ".join(f"{k}:{v}" for k, v in sorted(per_src.items()))
        print(f"{cls:<42}{len(buckets[cls]):>7}   {src_txt}")
    print("-" * 78)
    print(f"{'合计':<42}{sum(len(v) for v in buckets.values()):>7}")

    if args.scan_only:
        print("\n[scan-only] 未复制任何文件。确认类别名无误后去掉 --scan-only 再跑一次。")
        return

    if not args.out:
        sys.exit("缺少 --out")

    out = Path(args.out)
    if out.exists() and any(out.iterdir()):
        sys.exit(f"--out 目录 {out} 非空。换个目录，或先手动清空（避免混入上次的残留）。")

    # ---------------- 2. 按内容去重 ----------------
    if args.dedupe:
        print("\n[dedupe] 计算哈希中……")
        seen: dict[str, str] = {}
        removed = 0
        for cls in list(buckets):
            kept = []
            for s, p in buckets[cls]:
                try:
                    h = sha1(p)
                except OSError:
                    continue
                if h in seen:
                    removed += 1
                    continue
                seen[h] = f"{s}:{p}"
                kept.append((s, p))
            buckets[cls] = kept
        print(f"[dedupe] 去掉重复图片 {removed} 张")

    # ---------------- 3. 切分并复制 ----------------
    print(f"\n[split] train={args.train} valid={args.valid} test={1 - args.train - args.valid:.2f}")
    report = []
    total = 0
    for cls in sorted(buckets):
        items = buckets[cls][:]
        random.shuffle(items)
        n = len(items)
        n_train = int(n * args.train)
        n_valid = int(n * args.valid)
        # 小类保护：样本够多时保证 valid 至少 1 张，否则验证指标无法计算
        if n >= 3 and n_valid == 0:
            n_valid = 1
            n_train = max(1, n_train - 1)
        parts = {
            "train": items[:n_train],
            "valid": items[n_train:n_train + n_valid],
            "test": items[n_train + n_valid:],
        }
        for split, chunk in parts.items():
            target = out / split / cls
            target.mkdir(parents=True, exist_ok=True)
            for i, (_, src_file) in enumerate(chunk):
                dst = target / f"{cls}_{i:05d}{src_file.suffix.lower()}"
                if not dst.exists():
                    shutil.copy2(src_file, dst)
        total += n
        report.append(f"{cls:<42} total={n:<6} train={len(parts['train']):<6} "
                      f"valid={len(parts['valid']):<5} test={len(parts['test'])}")
        print(f"  [done] {cls}  共 {n} 张")

    (out / "dataset_report.txt").write_text(
        "\n".join([f"总计 {total} 张", ""] + report), encoding="utf-8")
    print(f"\n[完成] 共 {total} 张，已写入 {out}")
    print(f"[报告] {out / 'dataset_report.txt'}")
    print(f"\n[next] 开始训练：")
    print(f'       python train_disease.py --data "{out}" --out ..\\models --epochs 15')


if __name__ == "__main__":
    main()
