# -*- coding: utf-8 -*-
"""
构建扩充后的合并数据集（37 类 / 9 物种）

数据来源
--------
1. main/FinalDataset     6 物种 29 类 49,868 张（主体）
2. cross/indoor/*        新增 3 物种 8 类（仙人掌 / 虎皮兰 / 吊兰）
                          + 绿萝 3 类的补充数据

为什么不能直接把两个目录混在一起
-------------------------------
cross 里 Snake_Plant / Spider_Plant / Money_Plant 的每张原图被 Roboflow
增广成了 3 份（文件名形如 X_jpg.rf.<hash>.jpg）。若按文件随机切分训练/测试，
同一张原图的 3 个副本会同时出现在两边 —— 测试集准确率会虚高几个点，
而且这个错误不会报任何警。

因此这里做三件事：
  1. 按「原图」分组：剥掉 .rf.<hash> 后缀得到 group_key
  2. 跨来源 MD5 去重（cross 里的 Aloe 与 main 完全重复，直接不取）
  3. 输出时给每组打上 group 标记（文件名前缀 = group id），
     让训练脚本按 group 切分（见 train_indoor.py --group-split）

输出
----
<out>/<class_code>/<groupid>__<原名>.<ext>     ← 硬链接，不占额外空间
<out>/_manifest.json                           ← 每类的文件数 / 原图数 / 来源
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import time
from collections import defaultdict

from PIL import Image

EXTS = (".jpg", ".jpeg", ".png", ".bmp", ".webp")

# Roboflow 增广图：Snake_Plant_Healthy_001_jpg.rf.dad37eb5....jpg
RF_RE = re.compile(r"^(.*?)_(?:jpg|jpeg|png)\.rf\.[0-9a-f]+\.(?:jpg|jpeg|png)$", re.I)

# 只从 cross 取这些类（Aloe 与 main 完全重复，见文件头说明）
CROSS_CLASSES = [
    "Cactus_Dactylopius_Opuntia", "Cactus_Healthy",
    "Money_Plant_Bacterial_wilt_disease", "Money_Plant_Healthy",
    "Money_Plant_Manganese_Toxicity",
    "Snake_Plant_Anthracnose", "Snake_Plant_Healthy", "Snake_Plant_Leaf_Withering",
    "Spider_Plant_Fungal_leaf_spot", "Spider_Plant_Healthy",
    "Spider_Plant_Leaf_Tip_Necrosis",
]

# cross 的类名 -> main 的类名（大小写/后缀不一致，必须归一，否则同一病害会分裂成两类）
CODE_ALIAS = {
    "Money_Plant_Bacterial_wilt_disease": "Money_Plant_Bacterial_Wilt",
}

CN = {
    "Cactus_Healthy": "仙人掌·健康",
    "Cactus_Dactylopius_Opuntia": "仙人掌·胭脂虫危害",
    "Snake_Plant_Healthy": "虎皮兰·健康",
    "Snake_Plant_Anthracnose": "虎皮兰·炭疽病",
    "Snake_Plant_Leaf_Withering": "虎皮兰·叶片枯萎",
    "Spider_Plant_Healthy": "吊兰·健康",
    "Spider_Plant_Fungal_leaf_spot": "吊兰·真菌性叶斑病",
    "Spider_Plant_Leaf_Tip_Necrosis": "吊兰·叶尖坏死",
}


def md5(path, buf=1 << 20):
    h = hashlib.md5()
    with open(path, "rb") as f:
        while True:
            b = f.read(buf)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def group_key(fname):
    """同一张原图的多个增广副本 → 同一个 key"""
    m = RF_RE.match(fname)
    if m:
        return m.group(1)
    return os.path.splitext(fname)[0]


def _resize_one(task):
    """单张图缩放。必须写成模块级函数，才能被 multiprocessing 腌到子进程。"""
    src, dst, max_side, quality = task
    try:
        with Image.open(src) as im:
            im = im.convert("RGB")
            w, h = im.size
            m = max(w, h)
            if m > max_side:
                k = max_side / float(m)
                im = im.resize((max(1, round(w * k)), max(1, round(h * k))), Image.LANCZOS)
            im.save(dst, "JPEG", quality=quality, subsampling=1)
        return None
    except Exception as e:
        return (src, "%s: %s" % (type(e).__name__, e))


def scan_flat(root, only=None):
    """扫描 <root>/<class>/*.jpg 结构；only 非空时只取指定类"""
    out = defaultdict(list)
    if not os.path.isdir(root):
        return out
    for cls in sorted(os.listdir(root)):
        d = os.path.join(root, cls)
        if not os.path.isdir(d):
            continue
        if only is not None and cls not in only:
            continue
        for f in sorted(os.listdir(d)):
            if f.lower().endswith(EXTS):
                out[cls].append(os.path.join(d, f))
    return out


def scan_recursive(root, only=None):
    """递归扫描，以「包含图片的那个目录名」作为类别。

    main/FinalDataset 是两级结构 <物种>/<类别>/*.jpg，
    直接用 scan_flat 会把「物种目录」当成类别、结果一张图也扫不到。
    """
    out = defaultdict(list)
    if not os.path.isdir(root):
        return out
    for dirpath, _, filenames in os.walk(root):
        imgs = [f for f in filenames if f.lower().endswith(EXTS)]
        if not imgs:
            continue
        cls = os.path.basename(dirpath.rstrip("\\/"))
        if only is not None and cls not in only:
            continue
        for f in sorted(imgs):
            out[cls].append(os.path.join(dirpath, f))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--main", default=r"D:\indoor-plant-data\main\FinalDataset")
    ap.add_argument("--cross", default=r"D:\indoor-plant-data\cross\indoor")
    ap.add_argument("--out", default=r"D:\indoor-plant-data\combined_v2")
    ap.add_argument("--cap-groups", type=int, default=0,
                    help="每类最多保留多少个原图组（0=不限）。增广副本整组保留")
    ap.add_argument("--dedupe", type=int, default=1, help="跨来源 MD5 去重 1=开")
    ap.add_argument("--clean", type=int, default=1, help="输出目录已存在时先删除")
    ap.add_argument("--max-side", type=int, default=448,
                    help="预处理缩放到长边多少像素（0=不缩放，仅建硬链）。"
                         "必须缩放：原始图有 3000x3000 / 3072x4080 的，直接训练时"
                         "每步解码大图会把 GPU 利用率拉到 10% 以下，一轮慢 6 倍")
    ap.add_argument("--quality", type=int, default=92, help="缩放后 JPEG 质量")
    ap.add_argument("--jobs", type=int, default=0, help="并行进程数 0=CPU 核数")
    args = ap.parse_args()

    if args.clean and os.path.isdir(args.out):
        print("清理旧输出: %s" % args.out)
        shutil.rmtree(args.out, ignore_errors=True)
    os.makedirs(args.out, exist_ok=True)

    # ---------- 1. 收集 ----------
    print("=" * 72)
    print("扫描 main:", args.main)
    src = scan_recursive(args.main)

    print("扫描 cross:", args.cross)
    for split in ("train", "valid", "test"):
        part = scan_flat(os.path.join(args.cross, split), only=set(CROSS_CLASSES))
        for cls, paths in part.items():
            src[cls].extend(paths)

    # 类名归一
    norm = defaultdict(list)
    for cls, paths in src.items():
        norm[CODE_ALIAS.get(cls, cls)].extend(paths)
    src = dict(norm)

    print("\n合并后 %d 类，共 %d 个文件" % (len(src), sum(len(v) for v in src.values())))

    # ---------- 2. 跨来源 MD5 去重 ----------
    # 只对「来自 cross 的候选」做 MD5，main 是基准不做去重（它内部已清洗过）。
    # 判据：文件名含 .rf. 的必是 cross；另外 cross 路径里的文件也标记。
    cross_marker = os.path.normcase(os.path.abspath(args.cross))
    seen = {}          # md5 -> path（仅记录已保留的）
    dropped = []
    kept = defaultdict(list)

    print("\n计算指纹并去重（只对 cross 来源）...")
    t = 0
    for cls, paths in src.items():
        for p in paths:
            is_cross = ".rf." in os.path.basename(p).lower() or \
                       os.path.normcase(os.path.abspath(p)).startswith(cross_marker)
            if not is_cross or not args.dedupe:
                kept[cls].append(p)
                continue
            t += 1
            try:
                h = md5(p)
            except Exception as e:
                dropped.append((p, "读取失败 %s" % e))
                continue
            if h in seen:
                dropped.append((p, "与 %s 重复" % os.path.basename(seen[h])))
                continue
            seen[h] = p
            kept[cls].append(p)

    print("  cross 文件做指纹: %d 个，丢弃重复/失败: %d 个" % (t, len(dropped)))
    for p, why in dropped[:5]:
        print("    - %s (%s)" % (os.path.basename(p), why))
    if len(dropped) > 5:
        print("    ... 其余 %d 条省略" % (len(dropped) - 5))

    # ---------- 3. 按组归并 + 限流 ----------
    manifest = {}
    total_out = 0
    total_groups = 0
    tasks = []

    for cls in sorted(kept):
        groups = defaultdict(list)
        for p in kept[cls]:
            groups[group_key(os.path.basename(p))].append(p)

        keys = sorted(groups.keys())
        if args.cap_groups > 0 and len(keys) > args.cap_groups:
            step = len(keys) / float(args.cap_groups)
            keys = [keys[int(i * step)] for i in range(args.cap_groups)]

        d = os.path.join(args.out, cls)
        os.makedirs(d, exist_ok=True)
        n_files = 0
        for gi, gk in enumerate(keys):
            for p in groups[gk]:
                stem = gk.replace(os.sep, "_")[:80]
                dst = os.path.join(d, "g%04d__%s_%04d.jpg" % (gi, stem, n_files))
                tasks.append((p, dst, args.max_side, args.quality))
                n_files += 1
        manifest[cls] = {
            "cn": CN.get(cls, ""),
            "files": n_files,
            "groups": len(keys),
            "aug_per_group": round(n_files / max(1, len(keys)), 2),
        }
        total_out += n_files
        total_groups += len(keys)

    # ---------- 4. 并行写出（硬链 或 缩放）----------
    print("\n写出 %d 张（%s）..." % (
        len(tasks), "缩放到长边 %d" % args.max_side if args.max_side > 0 else "硬链接"))
    jobs = args.jobs if args.jobs > 0 else (os.cpu_count() or 4)
    fails = []
    t0 = time.time()
    if args.max_side > 0:
        from multiprocessing import Pool
        with Pool(processes=jobs) as pool:
            for i, err in enumerate(pool.imap_unordered(_resize_one, tasks, chunksize=16), 1):
                if err:
                    fails.append(err)
                if i % 2000 == 0 or i == len(tasks):
                    el = time.time() - t0
                    print("  %5d/%d  %.1f%%  %.0fs（%.1f 张/s）" % (
                        i, len(tasks), 100.0 * i / len(tasks), el, i / max(1e-6, el)))
    else:
        for p, dst, _, _ in tasks:
            try:
                os.link(p, dst)
            except Exception:
                try:
                    os.symlink(p, dst)
                except Exception:
                    shutil.copy2(p, dst)

    if fails:
        print("\n!! 失败 %d 张（已跳过）:" % len(fails))
        for s, why in fails[:5]:
            print("   %s  <- %s" % (os.path.basename(s), why))

    with open(os.path.join(args.out, "_manifest.json"), "w", encoding="utf-8") as f:
        json.dump({
            "classes": len(manifest),
            "total_files": total_out,
            "total_groups": total_groups,
            "cap_groups": args.cap_groups,
            "max_side": args.max_side,
            "detail": manifest,
        }, f, ensure_ascii=False, indent=1)

    print("\n" + "=" * 72)
    for cls in sorted(manifest):
        m = manifest[cls]
        print("  %-38s %6d 文件  %5d 组  %4.1fx/组" % (
            cls, m["files"], m["groups"], m["aug_per_group"]))
    print("\n完成: %d 类 / %d 文件 / %d 个独立原图组" % (len(manifest), total_out, total_groups))
    print("输出:", args.out)
    print("下一步: train_indoor.py --data %s --group-split 1" % args.out)


if __name__ == "__main__":
    sys.exit(main())
