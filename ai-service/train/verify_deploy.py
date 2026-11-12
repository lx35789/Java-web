# -*- coding: utf-8 -*-
"""部署自检：确认「模型文件 / AI 服务 / 后端接口 / 前端清单」四处一致。

为什么需要这个脚本
------------------
曾经发生过一次事故：模型从 6 物种 29 类扩到 9 物种 37 类，
AI 模型、后端 SpeciesKeys、数据库知识库都更新了，但**前端还留着一份
硬编码的「支持物种」清单（只有 6 种）**。结果用户放仙人掌照片时，
界面直接显示「暂无病害识别数据」并跳过，压根不调接口 ——
表现就是「新加的三类根本没上线」。

这类问题的共同点是：**每一处单独看都是对的，合起来才是错的**，
而且不报任何错误。所以必须有一个命令能把四处串起来核对。

用法
----
    python train/verify_deploy.py                 # 本机默认端口
    python train/verify_deploy.py --ai http://1.2.3.4:8000 --api http://1.2.3.4:8080

退出码 0 = 一致；1 = 有不一致项（可直接接进 CI / 启动脚本）。
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(os.path.dirname(HERE), "models")

# 后端 SpeciesKeys 里的映射（用于核对中文名是否齐全）
EXPECTED_CN = {
    "Aloe": "芦荟",
    "Money_Plant": "绿萝",
    "Rose": "月季",
    "Turmeric": "姜黄",
    "Hibiscus": "扶桑",
    "Chrysanthemum": "菊花",
    "Cactus": "仙人掌",
    "Snake_Plant": "虎皮兰",
    "Spider_Plant": "吊兰",
}


def ok(msg):
    print("  [OK]   " + msg)


def bad(msg):
    print("  [FAIL] " + msg)


def warn(msg):
    print("  [warn] " + msg)


def get_json(url, timeout=10):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return json.loads(r.read())


def check_local_files():
    print("\n=== 1. 本地模型文件 ===")
    f = os.path.join(MODEL_DIR, "disease.onnx")
    l = os.path.join(MODEL_DIR, "labels.json")
    problems = 0
    if not os.path.exists(f):
        bad("缺少 disease.onnx —— 服务会以 available=false 启动，病害识别整体不可用")
        return None, problems + 1
    ok("disease.onnx  %.1f MB" % (os.path.getsize(f) / 1e6))
    if not os.path.exists(l):
        bad("缺少 labels.json —— 模型无法解释输出")
        return None, problems + 1
    labels = json.load(open(l, encoding="utf-8"))
    species = sorted({v["species"] for v in labels.values()})
    ok("labels.json  %d 类 / %d 物种" % (len(labels), len(species)))
    print("        物种: " + "、".join(species))
    return {"classes": len(labels), "species": species}, problems


def check_ai_service(base):
    print("\n=== 2. AI 服务 /health  (%s) ===" % base)
    try:
        h = get_json(base.rstrip("/") + "/health")
    except Exception as e:
        bad("连不上 AI 服务：%s" % e)
        return None, 1
    dm = h.get("diseaseModel", {})
    if not dm.get("available"):
        bad("模型未就绪：%s" % dm.get("reason"))
        return None, 1
    sp = dm.get("species")
    ok("numClasses=%s" % dm.get("numClasses"))
    if sp is None:
        bad("health 未返回 species 字段 —— 前端无法得知模型支持哪些物种，"
            "会退回硬编码清单，新物种可能被误跳过")
        return {"classes": dm.get("numClasses"), "species": None}, 1
    ok("species 字段存在：%d 个" % len(sp))
    return {"classes": dm.get("numClasses"), "species": sorted(sp)}, 0


def check_backend(api, token=None):
    print("\n=== 3. 后端 /diagnoses/model-status  (%s) ===" % api)
    url = api.rstrip("/") + "/diagnoses/model-status"
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            body = json.loads(r.read())
    except urllib.error.HTTPError as e:
        bad("HTTP %s —— 需要登录态。先登录拿 token 再带 --token 运行" % e.code)
        return None, 1
    except Exception as e:
        bad("连不上后端：%s" % e)
        return None, 1

    data = body.get("data") or {}
    cn = data.get("coveredSpecies")
    if cn is None:
        bad("响应里没有 coveredSpecies —— 后端还是旧版本，需重新构建部署")
        return None, 1
    ok("numClasses=%s" % data.get("numClasses"))
    ok("coveredSpecies（中文）：%d 个 → %s" % (len(cn), "、".join(cn)))
    return {"classes": data.get("numClasses"), "cn": sorted(cn)}, 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ai", default="http://127.0.0.1:8000")
    ap.add_argument("--api", default="http://127.0.0.1:8080/api/v1")
    ap.add_argument("--token", default="", help="后端登录 token（查 model-status 需要）")
    args = ap.parse_args()

    print("=" * 68)
    print("植语助手 · 部署自检")
    print("=" * 68)

    local, p1 = check_local_files()
    ai, p2 = check_ai_service(args.ai)
    be, p3 = check_backend(args.api, args.token or None)

    problems = p1 + p2 + p3
    print("\n=== 4. 一致性核对 ===")

    if local and ai:
        if local["classes"] != ai["classes"]:
            bad("本地模型 %s 类 ≠ AI 服务加载 %s 类 → AI 服务没重启，或跑的是别的目录"
                % (local["classes"], ai["classes"]))
            problems += 1
        else:
            ok("本地文件与 AI 服务加载的模型一致（%d 类）" % local["classes"])
        if ai["species"] and sorted(s.lower().replace(" ", "_") for s in ai["species"]) != \
                sorted(s.lower().replace(" ", "_") for s in local["species"]):
            bad("AI 服务返回的物种与 labels.json 不一致")
            problems += 1

    if ai and ai["species"]:
        missing = [s for s in ai["species"] if s.replace(" ", "_") not in EXPECTED_CN]
        if missing:
            warn("后端 SpeciesKeys 缺少这些物种的中文映射：%s" % missing)
        else:
            ok("AI 服务的所有物种在后端 SpeciesKeys 里都有中文映射")

    if be and ai and ai["species"]:
        expect_cn = {EXPECTED_CN.get(s.replace(" ", "_"), s) for s in ai["species"]}
        got = set(be["cn"])
        lack = expect_cn - got
        extra = got - expect_cn
        if lack:
            bad("后端 coveredSpecies 少了：%s" % "、".join(sorted(lack)))
            problems += 1
        elif extra:
            warn("后端 coveredSpecies 多了（模型里没有）：%s" % "、".join(sorted(extra)))
        else:
            ok("后端 coveredSpecies 与模型完全对齐（%d 个）" % len(got))

    print("\n" + "=" * 68)
    if problems == 0:
        print("结论：四处一致，可以上线。")
    else:
        print("结论：发现 %d 处不一致，请不要上线。" % problems)
    print("=" * 68)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
