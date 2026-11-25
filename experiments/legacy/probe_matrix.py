"""
HTTP 探测矩阵：找出「哪个 URL + 哪个请求头」能拿到文件

背景
----
curl 用默认 UA（curl/8.x.x）访问 Zenodo 能拿到 200，
但 Python requests 伪装成 Chrome（却没带 Chrome 的其他特征头）会拿到 403。
说明是请求头被 WAF 判定为机器人。

本脚本会：
  1. 对若干 (URL, 请求头组合) 逐一尝试
  2. 打印每一项的状态码、关键响应头
  3. ★ 失败时打印响应体前 400 字符（WAF 通常会说明原因）
  4. 输出「可用组合」，供下载器使用

结果写入 _probe_matrix.txt
"""
import os
import sys
import traceback

BASE = r"C:\Users\lenovo\AppData\Roaming\CherryStudio\Data\Agents\system\2026-09-15\f4832600-b484-4f60-bf06-1544e39f858e\greenthumb\experiments"
LOG = os.path.join(BASE, "_probe_matrix.txt")

lines = []
def w(s):
    lines.append(str(s))
    try:
        open(LOG, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    except Exception:
        pass

REC = "17901489"
FN = "Chili_Pepper_BBWV2_Flooding.zip"

URLS = [
    ("原始带参数", f"https://zenodo.org/records/{REC}/files/{FN}?download=1"),
    ("去掉参数",   f"https://zenodo.org/records/{REC}/files/{FN}"),
    ("API content", f"https://zenodo.org/api/records/{REC}/files/{FN}/content"),
]

CHROME = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

AGENTS = [
    ("curl-UA", {"User-Agent": "curl/8.5.0"}),
    ("诚实-UA", {"User-Agent": "multidl/3.0 (+research)"}),
    ("Chrome-完整",
     {"User-Agent": CHROME,
      "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
      "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
      "Sec-Fetch-Dest": "document",
      "Sec-Fetch-Mode": "navigate",
      "Sec-Fetch-Site": "none",
      "Upgrade-Insecure-Requests": "1"}),
    ("无-UA", {}),
]

try:
    import requests
    w("requests %s" % requests.__version__)
    w("")

    ok = []
    for uname, url in URLS:
        for aname, headers in AGENTS:
            tag = f"{uname} | {aname}"
            try:
                r = requests.get(url, headers=headers, timeout=30,
                                 stream=True, allow_redirects=True)
                code = r.status_code
                cl = r.headers.get("Content-Length")
                cr = r.headers.get("Content-Range")
                ar = r.headers.get("Accept-Ranges")
                ct = r.headers.get("Content-Type", "")
                final = r.url
                # 取一小段响应体用于诊断
                snippet = b""
                if code >= 400:
                    try:
                        snippet = next(r.iter_content(500), b"")
                    except Exception:
                        pass
                r.close()

                w(f"[{code}] {tag}")
                w(f"      Content-Length={cl}  Accept-Ranges={ar}  Content-Type={ct}")
                w(f"      Content-Range={cr}")
                if final != url:
                    w(f"      跳转到: {final[:100]}")
                if snippet:
                    txt = snippet.decode("utf-8", "replace").replace("\n", " ")
                    w(f"      响应体: {txt[:400]}")
                w("")

                if code in (200, 206):
                    ok.append((tag, url, headers, cl, ar))

            except Exception as e:
                w(f"[EXC] {tag}")
                w(f"      {type(e).__name__}: {str(e)[:200]}")
                w("")

    w("=" * 62)
    w("可用组合：%d 个" % len(ok))
    for tag, url, headers, cl, ar in ok:
        w(f"  ✅ {tag}")
        w(f"     URL: {url}")
        w(f"     Content-Length={cl}  Accept-Ranges={ar}")
    if not ok:
        w("  ❌ 全部失败 —— 可能是 IP 被限流，或者需要浏览器 Cookie")
    w("=" * 62)

except Exception:
    w("异常:")
    w(traceback.format_exc())
