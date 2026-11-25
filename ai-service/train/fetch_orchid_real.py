# -*- coding: utf-8 -*-
"""
抓取 iNaturalist 开放许可的蝴蝶兰真实照片 —— 用来救「白花被判成日灼」的域外问题。

为什么需要这个脚本
------------------
兰花那份病害图集（KhaeruzZahra/orchid_disease）全是**黑底叶特写**，
去重后 443 张。模型在同源测试集上能到 98%~100%，但拿真实照片一试就崩：

    带白花的整株照  → Sunburn 0.78~0.99
    只有绿叶的整株照 → Healthy / Sunburn 各半

原因是训练集里从来没有「花」和「真实背景」，模型把「一大片浅色」直接
学成了「漂白状坏死」。用户拍兰花十有八九是带花拍的，这个偏差必须修。

做法：把 iNat 上的真实照片按开放许可下载下来，一部分掺进 Healthy 类训练，
一部分留作**真实照片留出集**单独评测（绝不能混进训练）。

许可
----
只取 CC0 / CC BY（CC BY 是硬义务：必须署名，所以落 attribution.csv）。
NC / ND 一律跳过 —— 商用 App 不能碰。

用法
----
    # 抓 260 张，其中 200 张进训练集 Orchid_Healthy，60 张留作真实留出评测
    python fetch_orchid_real.py --train-out experiments/data/orchid_v4/Orchid_Healthy \
        --eval-out experiments/data/orchid_real_eval/real_healthy \
        --train-n 200 --eval-n 60
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import subprocess
import sys
import time

API = "https://api.inaturalist.org/v1/observations"
TAXA = ["Phalaenopsis", "Doritaenopsis"]
UA = "GreenThumbBot/1.0 (plant disease research; contact: local)"


def curl_json(url: str, timeout: int = 40):
    """用 curl 而不是 urllib：本机 urllib 访问部分 CDN 会挂住，curl 实测稳定。"""
    r = subprocess.run(["curl", "-sL", "--max-time", str(timeout), "-A", UA, url],
                       capture_output=True)
    if r.returncode != 0 or not r.stdout:
        raise RuntimeError("curl 失败 rc=%s %s" % (r.returncode, url))
    return json.loads(r.stdout.decode("utf-8", "replace"))


def curl_file(url: str, dst: str, retries: int = 3) -> bool:
    for _ in range(retries):
        subprocess.run(["curl", "-sL", "--retry", "2", "--retry-all-errors",
                        "--max-time", "50", "-A", UA, "-o", dst, url], capture_output=True)
        if os.path.exists(dst) and os.path.getsize(dst) > 20000:
            return True
        time.sleep(0.5)
    if os.path.exists(dst):
        os.remove(dst)
    return False


def collect(max_n: int, pages: int = 25, per_page: int = 50):
    """按观察记录收集照片；同一 observation 的照片共用一个组号。"""
    items = []
    seen_photo = set()
    for taxon in TAXA:
        for page in range(1, pages + 1):
            if len(items) >= max_n:
                break
            url = ("%s?taxon_name=%s&photos=true&photo_license=cc0,cc-by"
                   "&per_page=%d&page=%d&order_by=id" % (API, taxon, per_page, page))
            try:
                d = curl_json(url)
            except Exception as e:
                print("  !! 第 %d 页失败：%r" % (page, e))
                break
            res = d.get("results") or []
            if not res:
                break
            for o in res:
                for ph in o.get("photos") or []:
                    pid = ph.get("id")
                    if pid in seen_photo:
                        continue
                    dim = ph.get("original_dimensions") or {}
                    if min(dim.get("width", 0), dim.get("height", 0)) < 500:
                        continue
                    lic = ph.get("license_code")
                    if lic not in ("cc0", "cc-by"):
                        continue
                    seen_photo.add(pid)
                    items.append({
                        "obs": o.get("id"),
                        "photo_id": pid,
                        "url": ph["url"].replace("/square.", "/large."),
                        "license": lic,
                        "attribution": ph.get("attribution") or "",
                        "obs_url": "https://www.inaturalist.org/observations/%s" % o.get("id"),
                    })
                    if len(items) >= max_n * 2:  # 多抓一些，下载失败时还有后备
                        break
                if len(items) >= max_n * 2:
                    break
            print("  %s 第 %d 页累计 %d 张" % (taxon, page, len(items)))
            if len(items) >= max_n * 2:
                break
    return items


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--train-out", default="", help="真实照片进训练集的目录（Healthy 类）")
    ap.add_argument("--eval-out", default="", help="真实照片留出评测目录（不参与训练）")
    ap.add_argument("--train-n", type=int, default=200)
    ap.add_argument("--eval-n", type=int, default=60)
    ap.add_argument("--attr-csv", default="", help="署名清单输出路径（CC BY 硬义务）")
    args = ap.parse_args()

    need = args.train_n + args.eval_n
    if need <= 0:
        print("没要求下载任何图片")
        return
    print("目标 %d 张（训练 %d / 评测 %d）" % (need, args.train_n, args.eval_n))
    items = collect(need)
    print("候选 %d 张" % len(items))
    if not items:
        print("!! 没抓到候选")
        sys.exit(1)

    for d in (args.train_out, args.eval_out):
        if d:
            os.makedirs(d, exist_ok=True)

    saved_train = saved_eval = 0
    rows = []
    for it in items:
        if saved_train >= args.train_n and saved_eval >= args.eval_n:
            break
        # 先填训练集，再填留出集 —— 留出集放在最后抓，天然带一点时间上的新鲜度
        if saved_train < args.train_n and args.train_out:
            dst = os.path.join(args.train_out,
                               "real_o%s__Orchid_Healthy_r%03d.jpg" % (it["obs"], saved_train + 1))
            bucket = "train"
        elif saved_eval < args.eval_n and args.eval_out:
            dst = os.path.join(args.eval_out,
                               "real_o%s__real_healthy_r%03d.jpg" % (it["obs"], saved_eval + 1))
            bucket = "eval"
        else:
            break
        if not curl_file(it["url"], dst):
            print("  !! 下载失败 photo %s" % it["photo_id"])
            continue
        if bucket == "train":
            saved_train += 1
        else:
            saved_eval += 1
        rows.append({"file": os.path.basename(dst), "bucket": bucket,
                     "photo_id": it["photo_id"], "license": it["license"],
                     "attribution": it["attribution"].replace("\n", " "),
                     "observation": it["obs_url"]})
        if (saved_train + saved_eval) % 20 == 0:
            print("  已下载 %d 张..." % (saved_train + saved_eval))

    print("\n完成：训练集 +%d 张，留出评测 +%d 张" % (saved_train, saved_eval))
    if args.attr_csv:
        os.makedirs(os.path.dirname(os.path.abspath(args.attr_csv)), exist_ok=True)
        with open(args.attr_csv, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=["file", "bucket", "photo_id", "license",
                                              "attribution", "observation"])
            w.writeheader()
            w.writerows(rows)
        print("署名清单：%s（CC BY 图片必须随 App 一并署名）" % args.attr_csv)


if __name__ == "__main__":
    main()
