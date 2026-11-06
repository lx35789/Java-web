# -*- coding: utf-8 -*-
"""用 DashScope 录音文件识别（paraformer-v2）转写本地音频。

流程（官方推荐的本地文件路径）：
  1. getPolicy 拿到 OSS 临时上传凭证
  2. 直接 POST 到 OSS 拿到 oss:// URL
  3. 提交异步转写任务
  4. 轮询任务状态，下载 transcription JSON
"""
import json
import os
import sys
import time

import requests

sys.stdout.reconfigure(encoding="utf-8")

KEY = sys.argv[1]
AUDIO = sys.argv[2]
OUT = sys.argv[3]

BASE = "https://dashscope.aliyuncs.com/api/v1"
H = {"Authorization": "Bearer " + KEY}


def log(*a):
    print(*a, flush=True)


# ---------- 1) 上传 ----------
log("[1] 申请上传凭证 …")
r = requests.get(f"{BASE}/uploads", params={"action": "getPolicy", "model": "paraformer-v2"},
                 headers=H, timeout=60)
r.raise_for_status()
p = r.json()["data"]
log("    upload_host:", p.get("upload_host"))
log("    upload_dir :", p.get("upload_dir"))

fname = os.path.basename(AUDIO)
key = f"{p['upload_dir']}/{fname}"

log("[2] 上传文件（%.1f MB）…" % (os.path.getsize(AUDIO) / 1e6))
with open(AUDIO, "rb") as f:
    files = {
        "OSSAccessKeyId": (None, p["oss_access_key_id"]),
        "Signature": (None, p["signature"]),
        "policy": (None, p["policy"]),
        "x-oss-object-acl": (None, p.get("x_oss_object_acl", "private")),
        "x-oss-forbid-overwrite": (None, p.get("x_oss_forbid_overwrite", "true")),
        "key": (None, key),
        "success_action_status": (None, "200"),
        "file": (fname, f, "audio/mp4"),
    }
    up = requests.post(p["upload_host"], files=files, timeout=600)
up.raise_for_status()
# OSS 的 PostObject 返回 XML（不是 JSON），需从 <RequestId> 或响应头取 oss 地址
raw = up.text.strip()
log("    响应状态:", up.status_code)
log("    响应前 200 字:", raw[:200].replace("\n", " "))

import re
m = re.search(r"<RequestId>([^<]+)</RequestId>", raw)
if m:
    oss_url = m.group(1).strip()
else:
    # 有些情况下返回体为空，只用 key 拼 oss:// 也能被 paraformer 接受
    oss_url = f"oss://{key}"
    log("    (未找到 RequestId，改用 key 拼接)")
log("    oss url:", oss_url)

# ---------- 2) 提交任务 ----------
log("[3] 提交转写任务 …")
body = {
    "model": "paraformer-v2",
    "input": {"file_urls": [oss_url]},
    "parameters": {"language_hints": ["zh", "en"], "channel_id": [0]},
}
r = requests.post(f"{BASE}/services/audio/asr/transcription", headers={**H,
                  "X-DashScope-Async": "enable", "Content-Type": "application/json"},
                  data=json.dumps(body), timeout=120)
r.raise_for_status()
task_id = r.json()["output"]["task_id"]
log("    task_id:", task_id)

# ---------- 3) 轮询 ----------
log("[4] 等待完成 …")
t0 = time.time()
while True:
    time.sleep(6)
    r = requests.get(f"{BASE}/tasks/{task_id}", headers=H, timeout=60)
    r.raise_for_status()
    d = r.json()
    st = d["output"].get("task_status")
    log("    %5.0fs  %s" % (time.time() - t0, st))
    if st in ("SUCCEEDED", "FAILED", "UNKNOWN"):
        break
    if time.time() - t0 > 1800:
        log("!! 超时")
        break

if st != "SUCCEEDED":
    log("!! 任务失败:", json.dumps(d, ensure_ascii=False)[:600])
    sys.exit(1)

res = d["output"].get("results") or []
log("[5] 下载转写结果（%d 个文件）…" % len(res))

lines = []
for item in res:
    url = item.get("transcription_url")
    if not url:
        log("    跳过（无 transcription_url）:", item.get("subtask_status"), item.get("message"))
        continue
    j = requests.get(url, timeout=120).json()
    for t in j.get("transcripts") or []:
        txt = (t.get("text") or "").strip()
        if txt:
            lines.append(txt)

full = "\n".join(lines)
with open(OUT, "w", encoding="utf-8") as f:
    f.write(full)

log("\n===== 转写完成，%d 字 =====" % len(full))
log(full[:2000])
