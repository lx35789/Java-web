# -*- coding: utf-8 -*-
"""
兰花（蝴蝶兰属）病害数据集整理 —— GreenThumb v4

数据来源
--------
**KhaeruzZahra/orchid_disease（GitHub，440+ 张唯一图，4 类）**

    train/ train2/ test2/
        kolektrikum/   炭疽病（Colletotrichum，印尼语拼写）
        normal/        健康
        sunburn/       日灼
        tungau/        螨害（tungau = 螨，红蜘蛛类）

为什么不用原计划的 Roboflow
--------------------------
原方案是 ``ib811aaronkao/orchid_leaf_disease``（695 张 / 5 类 / 检测框），
但 ``universe.roboflow.com`` 与 ``api.roboflow.com`` 在本机均返回 403，
Zenodo、HuggingFace 同样不可达 —— 只有 GitHub / iNaturalist 图片 CDN 通。
这份 GitHub 图集是绕开墙之后找到的、唯一带病害标注且能直接下载的兰花数据。

必须先做的事：去重
------------------
**880 张文件里只有 443 张是真正不同的。** train/ 与 train2/ 是同一批图的
两次导出，437 个文件内容完全重复。若三个目录直接合起来训练，同一张图
既在训练集又在测试集，指标虚高十几个点且不报任何错。
这里按 md5 去重，并把 md5 前 10 位当「原图组」写进文件名：

    <out>/<class_code>/g<md5[:10]>__<class_code>_<nnn>.jpg

``train_indoor.py --group-split 1`` 按 ``__`` 前的组号切分，
同图副本不会被切开。

类别命名
--------
``Orchid_`` 前缀 + 英文病害名，与 labels.json 的 code 一一对应；
中文名同时写进 train_indoor.py 的 CN 字典（此处也落一份到 manifest.json 便于核对）。

用法
----
    python prepare_orchid_v4.py --src C:\\path\\to\\orchid_disease-master \\
        --out experiments/data/orchid_v4

    # 只看统计不写盘
    python prepare_orchid_v4.py --src ... --out ... --dry-run 1
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import os
import shutil
import sys

from PIL import Image

EXTS = (".jpg", ".jpeg", ".png", ".bmp", ".webp")

# 原目录名 -> (类别码, 中文名, 说明)
CLASS_MAP = {
    "kolektrikum": ("Orchid_Anthracnose", "兰花·炭疽病",
                    "Colletotrichum 引起的炭疽病，叶片出现紫褐色凹陷病斑并伴随黄化"),
    "normal": ("Orchid_Healthy", "兰花·健康", "健康叶片"),
    "sunburn": ("Orchid_Sunburn", "兰花·日灼",
                "强光灼伤，叶尖或叶缘出现大片漂白状坏死，边缘带紫褐色晕圈"),
    "tungau": ("Orchid_Mite_Damage", "兰花·螨害",
               "螨类（红蜘蛛）吸食，叶面出现密集锈褐色细小斑点或结痂"),
}


def md5_of(path: str, chunk: int = 1 << 20) -> str:
    h = hashlib.md5()
    with open(path, "rb") as f:
        while True:
            b = f.read(chunk)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


def log(msg: str) -> None:
    print(msg, flush=True)


def collect(src: str):
    """递归找图并按 md5 去重，保留首次出现的路径。"""
    seen = {}
    dups = 0
    for dirpath, _, filenames in os.walk(src):
        for f in sorted(filenames):
            if not f.lower().endswith(EXTS):
                continue
            p = os.path.join(dirpath, f)
            try:
                h = md5_of(p)
            except Exception as e:
                log("  !! 读取失败 %s (%r)" % (p, e))
                continue
            if h in seen:
                dups += 1
                continue
            seen[h] = p
    log("扫描到唯一图片 %d 张（跳过内容重复 %d 张）" % (len(seen), dups))
    return seen


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True, help="orchid_disease 仓库解压后的目录")
    ap.add_argument("--out", required=True, help="输出目录，喂给 train_indoor.py --data")
    ap.add_argument("--max-side", type=int, default=768,
                    help="长边上限（原图最大 5312px，缩到 768 省 IO；训练输入 224）")
    ap.add_argument("--dry-run", type=int, default=0)
    args = ap.parse_args()

    src = os.path.abspath(args.src)
    if not os.path.isdir(src):
        log("!! 源目录不存在：%s" % src)
        sys.exit(1)

    uniq = collect(src)
    if not uniq:
        log("!! 没找到图片")
        sys.exit(1)

    buckets = collections.defaultdict(list)
    unknown = collections.Counter()
    for h, p in uniq.items():
        cls_dir = os.path.basename(os.path.dirname(p)).lower()
        if cls_dir in CLASS_MAP:
            buckets[CLASS_MAP[cls_dir][0]].append((h, p))
        else:
            unknown[cls_dir] += 1
    if unknown:
        log("!! 未识别目录（已跳过）：%s" % dict(unknown))

    log("\n去重后各类：")
    for code in sorted(buckets):
        log("  %-24s %4d" % (code, len(buckets[code])))
    total = sum(len(v) for v in buckets.values())
    log("  合计 %d 张" % total)

    if args.dry_run:
        return

    if os.path.isdir(args.out):
        shutil.rmtree(args.out)
    manifest = {
        "source": "KhaeruzZahra/orchid_disease (GitHub)",
        "source_note": "仓库无 LICENSE 文件；仅建议内部验证/演示使用，商用前需确认授权",
        "dedup_unique": total,
        "max_side": args.max_side,
        "classes": {},
    }

    rev = {v[0]: (k, v[1], v[2]) for k, v in CLASS_MAP.items()}
    for code in sorted(buckets):
        dst = os.path.join(args.out, code)
        os.makedirs(dst, exist_ok=True)
        saved = 0
        for i, (h, p) in enumerate(sorted(buckets[code]), 1):
            dstp = os.path.join(dst, "g%s__%s_%03d.jpg" % (h[:10], code, i))
            try:
                im = Image.open(p).convert("RGB")
                k = args.max_side / max(im.size)
                if k < 1:
                    im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))),
                                   Image.LANCZOS)
                im.save(dstp, "JPEG", quality=92)
                saved += 1
            except Exception as e:
                log("  !! 转换失败 %s (%r)" % (p, e))
        _, cn, desc = rev[code]
        manifest["classes"][code] = {"count": saved, "cn": cn, "desc": desc}
        log("  %-24s -> %4d 张  %s" % (code, saved, cn))

    with open(os.path.join(args.out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    log("\n完成，输出目录：%s" % args.out)
    log("manifest.json 不含图片，scan() 会当作无图目录跳过，不影响训练。")


if __name__ == "__main__":
    main()
