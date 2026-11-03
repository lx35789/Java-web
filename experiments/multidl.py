"""
多线程断点续传下载器 v3

v3 修的问题
-----------
v2 探测时固定伪装成 Chrome UA，结果被 Zenodo 的 WAF 判定为机器人，全部返回 403。
而 curl 用默认 UA（curl/8.x.x）反而是 200 —— 说明「伪装浏览器但特征不全是」比
「诚实地表明身份」更容易被拦。

v3 的做法：**探测阶段自动试多组请求头，哪组能过就用哪组**，
并把 403 的响应体打出来，方便定位原因。

核心特性
--------
  1. ★ 自动试探请求头（curl / 诚实标识 / 完整 Chrome 特征）
  2. 分段并发（默认 16 连接），每段独立重试
  3. ★ 进度存盘，中断后只补缺失的段
  4. 拿不到大小或不支持分块时，自动降级为单连接流式 + Range 续传
  5. 完整诊断输出（状态码 / 响应头 / 失败时的响应体）
  6. 内置 MD5 校验

用法
----
    python multidl.py <URL> <输出文件> [选项]

    -n N    并发连接数（默认 16）
    -m MD5  完成后校验
    -t SEC  单请求超时（默认 90）
    -r N    每段重试次数（默认 5）
    -f      忽略进度重新下载
    -s      强制单连接流式
    --diag  只探测打印诊断，不下载
    --ua K  强制指定请求头档位：curl / honest / chrome
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import threading
import time
import traceback

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

CHUNK = 4 * 1024 * 1024

CHROME = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

# ★ 关键：按"最可能不被拦"的顺序排列
UA_PROFILES = [
    ("curl", {"User-Agent": "curl/8.5.0"}),
    ("honest", {"User-Agent": "multidl/3.0"}),
    ("chrome", {
        "User-Agent": CHROME,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
        "Upgrade-Insecure-Requests": "1",
    }),
]

_tls = threading.local()


def human(n: float) -> str:
    for u in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.1f} {u}"
        n /= 1024
    return f"{n:.1f} TB"


def new_session(n: int = 8) -> requests.Session:
    s = requests.Session()
    retry = Retry(total=3, connect=3, read=3, backoff_factor=1.0,
                  status_forcelist=[429, 500, 502, 503, 504],
                  allowed_methods=frozenset(["GET", "HEAD"]))
    ad = HTTPAdapter(max_retries=retry, pool_connections=n, pool_maxsize=n)
    s.mount("https://", ad)
    s.mount("http://", ad)
    return s


def tls_session(n: int = 4) -> requests.Session:
    s = getattr(_tls, "s", None)
    if s is None:
        s = new_session(n)
        _tls.s = s
    return s


def snippet(r, n=400) -> str:
    try:
        if r.status_code >= 400:
            b = next(r.iter_content(n), b"")
            return b.decode("utf-8", "replace").replace("\n", " ")[:n]
    except Exception:
        pass
    return ""


def probe(session, url, timeout, force_ua=None, verbose=True):
    """返回 (size, ranges_ok, final_url, diag, headers, profile_name)

    ★ 重要：判定「是否支持分块」必须用真实的 GET + Range 请求，
      不能只看 HEAD 的 Accept-Ranges 头。
      很多 CDN 的 HEAD 响应不带 Accept-Ranges，但实际完全支持 Range ——
      只信 HEAD 会导致白白降级成单连接（实测慢 10 倍以上）。
    """
    diag = []

    def log(s):
        diag.append(s)
        if verbose:
            print("    " + s)

    profiles = ([p for p in UA_PROFILES if p[0] == force_ua] if force_ua else UA_PROFILES)
    if not profiles:
        profiles = UA_PROFILES

    for pname, headers in profiles:
        log(f"--- 试请求头 [{pname}] ---")
        size, ranges, final = 0, False, url

        # ---------- 1) 先做 Range 测试（判定是否支持分块的唯一可靠依据） ----------
        try:
            r = session.get(url, headers={**headers, "Range": "bytes=0-0"},
                            timeout=timeout, stream=True, allow_redirects=True)
            cr = r.headers.get("Content-Range")
            cl = r.headers.get("Content-Length")
            final = r.url
            log(f"  GET Range -> {r.status_code}  Content-Range={cr}  Content-Length={cl}")
            if r.status_code == 206 and cr and "/" in cr:
                size = int(cr.split("/")[-1])
                ranges = True
                log("  → 确认支持分块（206 + Content-Range）")
            elif r.status_code == 200:
                if cl:
                    size = int(cl)
                log("  → 服务器忽略 Range（返回 200），不支持分块")
            elif r.status_code in (403, 401):
                sn = snippet(r)
                log(f"  ⚠ [{pname}] 被拒（{r.status_code}）" + (f"：{sn[:200]}" if sn else ""))
            else:
                sn = snippet(r)
                if sn:
                    log(f"  响应体: {sn}")
            r.close()
        except Exception as e:
            log(f"  GET Range 异常: {type(e).__name__}: {str(e)[:140]}")

        # ---------- 2) Range 拿不到大小时，退回 HEAD ----------
        if size <= 0:
            try:
                r = session.head(url, headers=headers, timeout=timeout, allow_redirects=True)
                cl = r.headers.get("Content-Length")
                ar = r.headers.get("Accept-Ranges")
                final = r.url
                log(f"  HEAD -> {r.status_code}  Content-Length={cl}  Accept-Ranges={ar}")
                if r.status_code < 400 and cl and int(cl) > 0:
                    size = int(cl)
            except Exception as e:
                log(f"  HEAD 异常: {type(e).__name__}: {str(e)[:140]}")

        if size > 0:
            log(f"  ✅ [{pname}] 探测成功：大小 {human(size)}，"
                f"{'支持分块' if ranges else '不支持分块'}")
            return size, ranges, final, diag, headers, pname

        log("")
        log("")

    return 0, False, url, diag, UA_PROFILES[0][1], None


def download_chunk(url, path, start, end, timeout, retries, lock, counters, headers):
    expect = end - start + 1
    last = None
    for attempt in range(1, retries + 1):
        try:
            s = tls_session()
            r = s.get(url, headers={**headers, "Range": f"bytes={start}-{end}"},
                      timeout=timeout, stream=True)
            if r.status_code not in (200, 206):
                raise IOError(f"HTTP {r.status_code}")
            buf = bytearray()
            for block in r.iter_content(262144):
                if block:
                    buf.extend(block)
            r.close()
            if len(buf) != expect:
                raise IOError(f"长度不符: 期望 {expect}, 实际 {len(buf)}")
            with lock:
                with open(path, "r+b") as f:
                    f.seek(start)
                    f.write(buf)
                counters["done"] += 1
            return
        except Exception as e:
            last = e
            if attempt < retries:
                time.sleep(min(2 * attempt, 12))
    raise IOError(f"区间 {start}-{end} 重试 {retries} 次仍失败: {last}")


def simple_download(url, path, timeout, retries, headers):
    for attempt in range(1, retries + 1):
        have = os.path.getsize(path) if os.path.exists(path) else 0
        h = dict(headers)
        if have > 0:
            h["Range"] = f"bytes={have}-"
            print(f"    已有 {human(have)}，尝试续传")
        try:
            s = tls_session()
            r = s.get(url, headers=h, timeout=timeout, stream=True)
            if have > 0 and r.status_code != 206:
                print(f"    服务器不支持续传 (HTTP {r.status_code})，从头下载")
                r.close()
                have = 0
                if os.path.exists(path):
                    os.remove(path)
                continue
            if r.status_code >= 400:
                print(f"    HTTP {r.status_code}：{snippet(r, 200)}")
                r.close()
                return False
            cl = r.headers.get("Content-Length")
            total = int(cl) + have if cl else None
            if total:
                print(f"    总大小 {human(total)}")
            mode = "ab" if have > 0 else "wb"
            got = have
            t0 = time.time()
            mark = got
            with open(path, mode) as f:
                for block in r.iter_content(262144):
                    if not block:
                        continue
                    f.write(block)
                    got += len(block)
                    el = time.time() - t0
                    if el > 1.0:
                        spd = (got - mark) / el
                        pct = f"{got/total*100:5.1f}%" if total else human(got)
                        rem = f"  剩余约 {int((total-got)/spd)}s" if (total and spd > 0) else ""
                        sys.stdout.write(f"\r    {pct}  {human(got)}  {human(spd)}/s{rem}   ")
                        sys.stdout.flush()
                        t0, mark = time.time(), got
            print()
            return True
        except Exception as e:
            print(f"\n    中断: {type(e).__name__}: {str(e)[:120]}")
            if attempt < retries:
                print(f"    {3*attempt} 秒后重试（{attempt}/{retries}）...")
                time.sleep(3 * attempt)
    return False


def md5_of(path, bufsize=1 << 20):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(bufsize), b""):
            h.update(b)
    return h.hexdigest()


def verify(out, want_md5, prog):
    print()
    actual = md5_of(out)
    print(f"  文件大小: {human(os.path.getsize(out))}")
    print(f"  实际 MD5: {actual}")
    if want_md5:
        print(f"  期望 MD5: {want_md5.lower()}")
        if actual == want_md5.lower():
            print("\n  ✅ 校验通过，下载完整！")
            try:
                os.remove(prog)
            except Exception:
                pass
            return 0
        print("\n  ❌ 校验失败。重跑同一条命令可补齐缺失分段。")
        return 3
    try:
        os.remove(prog)
    except Exception:
        pass
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("url")
    ap.add_argument("output")
    ap.add_argument("-n", "--connections", type=int, default=16)
    ap.add_argument("-m", "--md5", default=None)
    ap.add_argument("-t", "--timeout", type=int, default=90)
    ap.add_argument("-r", "--retries", type=int, default=5)
    ap.add_argument("-f", "--force", action="store_true")
    ap.add_argument("-s", "--simple", action="store_true")
    ap.add_argument("--diag", action="store_true")
    ap.add_argument("--ua", default=None, choices=["curl", "honest", "chrome"])
    args = ap.parse_args()

    out = os.path.abspath(args.output)
    prog = out + ".progress.json"

    print("=" * 62)
    print("  多线程断点续传下载器 v3")
    print("=" * 62)
    print(f"  输出: {out}")
    print()
    print("  [探测服务器]（会自动尝试多组请求头）")

    if args.force:
        for p in (out, prog):
            if os.path.exists(p):
                os.remove(p)

    session = new_session(max(8, args.connections))
    size, ranges, final_url, diag, headers, used_profile = probe(
        session, args.url, args.timeout, force_ua=args.ua)
    session.close()

    print()
    if final_url != args.url:
        print(f"  跳转到: {final_url[:100]}...")

    if size <= 0:
        print("  ❌ 所有请求头组合都失败了。")
        print()
        print("  下一步建议：")
        print("   1. 先跑 probe_matrix.py，它会试 3 个 URL × 4 组请求头，看哪个通")
        print("   2. 如果全 403，说明 IP 被限流 —— 等一会儿再试，或换个网络")
        print("   3. 直接用浏览器打开这个链接下载（浏览器带完整 Cookie，通常能过）")
        print()
        return 1

    print(f"  文件大小: {human(size)} ({size} 字节)")
    print(f"  支持分块: {'是' if ranges else '否'}")
    print(f"  可用请求头: [{used_profile}]")

    if args.diag:
        print("\n  [仅诊断模式，未下载]")
        return 0
    print()

    if args.simple or not ranges:
        reason = "指定 --simple" if args.simple else "服务器不支持分块"
        print(f"  → 单连接流式下载（原因：{reason}）\n")
        if not simple_download(args.url, out, args.timeout, args.retries, headers):
            print("\n[失败] 文件已保留，重跑同一条命令可续传。")
            return 2
        return verify(out, args.md5, prog)

    chunks, pos = [], 0
    while pos < size:
        e = min(pos + CHUNK - 1, size - 1)
        chunks.append((pos, e))
        pos = e + 1
    total = len(chunks)

    done = set()
    if os.path.exists(prog) and os.path.exists(out):
        try:
            with open(prog, encoding="utf-8") as f:
                st = json.load(f)
            if st.get("size") == size and st.get("url") == args.url:
                done = set(st.get("done", []))
                print(f"  ★ 发现进度：已完成 {len(done)}/{total} 段，继续下载")
            else:
                print("  旧进度不匹配，重新开始")
        except Exception:
            print("  进度文件损坏，重新开始")
    else:
        print("  无进度文件，从头开始")

    if not os.path.exists(out):
        with open(out, "wb") as f:
            f.truncate(size)
    elif os.path.getsize(out) != size:
        with open(out, "r+b") as f:
            f.truncate(size)

    pending = [(s, e, i) for i, (s, e) in enumerate(chunks) if i not in done]
    print(f"  待下载 {len(pending)}/{total} 段，并发 {args.connections}\n")

    if pending:
        lock = threading.Lock()
        counters = {"done": 0}
        base = len(done)
        t0 = time.time()
        stop = {"v": False}

        def save():
            with lock:
                try:
                    with open(prog, "w", encoding="utf-8") as f:
                        json.dump({"url": args.url, "size": size,
                                   "done": sorted(done), "ts": time.time()}, f)
                except Exception:
                    pass

        def show():
            with lock:
                fin = base + counters["done"]
            el = time.time() - t0
            spd = (counters["done"] * CHUNK) / el if el > 0 else 0
            frac = fin / total
            bar = "#" * int(frac * 30) + "-" * (30 - int(frac * 30))
            rem = (size * (1 - frac)) / spd if spd > 0 else 0
            sys.stdout.write(f"\r  [{bar}] {frac*100:5.1f}%  {fin}/{total}段  "
                             f"{human(spd)}/s  剩余约 {int(rem)}s   ")
            sys.stdout.flush()

        def ticker():
            while not stop["v"]:
                show()
                time.sleep(1.5)

        threading.Thread(target=ticker, daemon=True).start()
        failed = False
        try:
            from concurrent.futures import ThreadPoolExecutor, as_completed
            with ThreadPoolExecutor(max_workers=args.connections) as ex:
                futs = {ex.submit(download_chunk, args.url, out, s, e, args.timeout,
                                  args.retries, lock, counters, headers): i
                        for (s, e, i) in pending}
                for fu in as_completed(futs):
                    idx = futs[fu]
                    try:
                        fu.result()
                        with lock:
                            done.add(idx)
                        save()
                    except Exception as e:
                        print(f"\n\n[错误] 第 {idx} 段失败: {e}")
                        failed = True
                        break
        finally:
            stop["v"] = True
            time.sleep(1.8)
            show()
            print()

        if failed:
            print("\n  进度已保存。重跑同一条命令即可继续。")
            return 2

    return verify(out, args.md5, prog)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n\n  已中断。进度已保存，重跑同一条命令可继续。")
        sys.exit(130)
    except Exception:
        print("\n[未捕获异常]")
        traceback.print_exc()
        sys.exit(1)
