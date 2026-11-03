# -*- coding: utf-8 -*-
"""
把训练输出转换成 AI 服务能直接加载的格式，并拷贝到 ai-service/models/。

训练产物（train_indoor.py 输出）：
    best.pt  labels.json  meta.json  disease.onnx  metrics.json

服务要求（ai-service/app/disease.py）：
    models/disease.onnx
    models/labels.json   {"0": {"code","cn","species","disease"}, ...}
    models/meta.json     {"input_size", "arch", "num_classes", ...}

用法：
    python export_to_service.py --src D:\\indoor-plant-data\\model_mnv3 \
        --dst C:\\Users\\lenovo\\greenthumb\\ai-service\\models
兼容旧格式 labels.json（{"classes":[...], "cn":[...]}）。
"""
import argparse
import json
import os
import shutil
import time

SPECIES_PREFIX = ["Aloe", "Chrysanthemum", "Hibiscus", "Money_Plant", "Rose", "Turmeric",
                  "Snake_Plant", "Spider_Plant", "Cactus"]


def split_code(code):
    for sp in sorted(SPECIES_PREFIX, key=len, reverse=True):
        if code.startswith(sp + "_"):
            return sp.replace("_", " "), code[len(sp) + 1:].replace("_", " ")
    return code, code


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", required=True)
    ap.add_argument("--dst", required=True)
    ap.add_argument("--onnx-name", default="disease.onnx")
    args = ap.parse_args()

    src, dst = args.src, args.dst
    os.makedirs(dst, exist_ok=True)

    labels_raw = json.load(open(os.path.join(src, "labels.json"), encoding="utf-8"))

    # 统一成 {"0": {"code","cn","species","disease"}}
    labels, cn_map = {}, {}
    if "classes" in labels_raw:                      # 旧格式
        classes = labels_raw["classes"]
        cns = labels_raw.get("cn") or classes
        for i, (c, cn) in enumerate(zip(classes, cns)):
            sp, dis = split_code(c)
            labels[str(i)] = {"code": c, "cn": cn, "species": sp, "disease": dis}
    else:                                            # 已经是新格式
        for k, v in labels_raw.items():
            if isinstance(v, dict):
                labels[str(k)] = {
                    "code": v.get("code", "class_%s" % k),
                    "cn": v.get("cn") or v.get("nameCn") or v.get("code"),
                    "species": v.get("species", ""),
                    "disease": v.get("disease", ""),
                }
            else:
                sp, dis = split_code(str(v))
                labels[str(k)] = {"code": str(v), "cn": str(v), "species": sp, "disease": dis}

    meta_src = os.path.join(src, "meta.json")
    meta = json.load(open(meta_src, encoding="utf-8")) if os.path.exists(meta_src) else {}
    img_size = meta.get("input_size") or labels_raw.get("img_size") or 224
    arch = meta.get("arch") or labels_raw.get("arch") or "unknown"

    onnx_src = os.path.join(src, args.onnx_name)
    if not os.path.exists(onnx_src):
        alt = os.path.join(src, "model.onnx")
        if os.path.exists(alt):
            onnx_src = alt
        else:
            raise SystemExit("找不到 onnx 文件：%s" % onnx_src)

    shutil.copyfile(onnx_src, os.path.join(dst, "disease.onnx"))
    with open(os.path.join(dst, "labels.json"), "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=1)
    with open(os.path.join(dst, "meta.json"), "w", encoding="utf-8") as f:
        json.dump({"input_size": int(img_size), "arch": arch,
                   "num_classes": len(labels),
                   "exported_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "source": src}, f, ensure_ascii=False, indent=1)

    print("导出完成 -> %s" % dst)
    print("  类别数 = %d，input_size = %d，arch = %s" % (len(labels), int(img_size), arch))
    print("  disease.onnx = %.2f MB" % (os.path.getsize(os.path.join(dst, "disease.onnx")) / 1e6))
    for k in sorted(labels, key=lambda x: int(x))[:5]:
        v = labels[k]
        print("    [%s] %-38s %s" % (k, v["code"], v["cn"]))


if __name__ == "__main__":
    main()
