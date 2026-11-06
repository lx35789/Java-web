# -*- coding: utf-8 -*-
"""本地音频转写：ffmpeg 切段 + qwen3-asr-flash（base64 直传，不走 OSS）。

为什么不用 paraformer-v2 的文件识别接口：
  那条路要先上传到 DashScope 的 OSS 再提交异步任务，实测无论 oss:// 用哪种
  桶名组合都返回 SERVER_ERROR，而公开 URL 的样例却正常 —— 说明是上传后的
  可见性/路径问题，排查成本高。
  qwen3-asr-flash 支持 base64 data URI，同步返回，小文件省事。

用法：
  python asr_chunked.py <api_key> <audio_file> <out_txt> [chunk_sec]
"""
import base64
import json
import os
import subprocess
import sys
import time

import imageio_ffmpeg
import requests

sys.stdout.reconfigure(encoding="utf-8")

KEY = sys.argv[1]
AUDIO = sys.argv[2]
OUT = sys.argv[3]
CHUNK = int(sys.argv[4]) if len(sys.argv) > 4 else 120

FF = imageio_ffmpeg.get_ffmpeg_exe()
URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
H = {"Authorization": "Bearer " + KEY, "Content-Type": "application/json"}

TMP = os.path.join(os.path.dirname(OUT) or ".", "_asr_chunks")
os.makedirs(TMP, exist_ok=True)


def log(*a):
    print(*a, flush=True)


def duration(path):
    p = subprocess.run([FF, "-i", path], capture_output=True, text=True, errors="ignore")
    for line in p.stderr.splitlines():
        if "Duration:" in line:
            t = line.split("Duration:")[1].split(",")[0].strip()
            h, m, s = t.split(":")
            return int(h) * 3600 + int(m) * 60 + float(s)
    return 0.0


def cut(src, start, dur, dst):
    subprocess.run([FF, "-y", "-ss", str(start), "-t", str(dur), "-i", src,
                    "-ac", "1", "-ar", "16000", "-b:a", "48k", dst],
                   capture_output=True)


def transcribe(path, retries=3):
    b64 = base64.b64encode(open(path, "rb").read()).decode()
    body = {
        "model": "qwen3-asr-flash",
        "input": {"messages": [{"role": "user", "content": [{"audio": "data:audio/mpeg;base64," + b64}]}]},
    }
    last = ""
    for i in range(retries):
        try:
            r = requests.post(URL, headers=H, data=json.dumps(body), timeout=300)
            if r.status_code != 200:
                last = f"HTTP {r.status_code} {r.text[:200]}"
                time.sleep(3)
                continue
            j = r.json()
            msg = j["output"]["choices"][0]["message"]
            parts = []
            for c in msg.get("content") or []:
                if isinstance(c, dict) and c.get("text"):
                    parts.append(c["text"])
            return "".join(parts).strip()
        except Exception as e:
            last = str(e)
            time.sleep(3)
    raise RuntimeError(last)


total = duration(AUDIO)
n = max(1, int(total // CHUNK) + (1 if total % CHUNK else 0))
log("总时长 %.1f 秒 → 切成 %d 段（每段 %ds）" % (total, n, CHUNK))

out = []
for i in range(n):
    start = i * CHUNK
    dst = os.path.join(TMP, "part_%03d.mp3" % i)
    cut(AUDIO, start, CHUNK, dst)
    size = os.path.getsize(dst) / 1024
    t0 = time.time()
    try:
        txt = transcribe(dst)
    except Exception as e:
        log("  [%d/%d] 失败: %s" % (i + 1, n, e))
        continue
    out.append(txt)
    log("  [%d/%d] %5.0fs  %4.0fKB  %.1fs  %d 字 | %s" % (
        i + 1, n, start, size, time.time() - t0, len(txt), txt[:40]))

full = "\n".join(out)
with open(OUT, "w", encoding="utf-8") as f:
    f.write(full)
log("\n===== 完成，共 %d 字 → %s =====" % (len(full), OUT))
