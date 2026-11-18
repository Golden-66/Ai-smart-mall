#!/usr/bin/env python3
"""商品接口并发压测 (仅依赖标准库): 统计 QPS 与 P50/P95/P99 延迟。

用法:
  python scripts/bench_products.py --concurrency 40 --requests 2000
  python scripts/bench_products.py --path "/api/products?category=笔记本&maxPrice=6000"

提示: 先预热一次再正式压测; 商品详情走 Redis 缓存, 列表/检索走 MySQL。
      请使用自己机器上测得的真实数据, 不要直接引用别人的数字。
"""
import argparse
import statistics
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor


def one(url):
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            r.read()
            ok = 200 <= r.status < 300
    except Exception:
        ok = False
    return (time.perf_counter() - t0) * 1000, ok


def pct(sorted_vals, p):
    if not sorted_vals:
        return float("nan")
    idx = min(len(sorted_vals) - 1, max(0, int(round(p / 100 * len(sorted_vals) + 0.5)) - 1))
    return sorted_vals[idx]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--path", default="/api/products/1", help="要压测的接口路径 (默认: 商品详情)")
    ap.add_argument("--concurrency", type=int, default=40)
    ap.add_argument("--requests", type=int, default=2000)
    ap.add_argument("--warmup", type=int, default=50)
    args = ap.parse_args()

    # 允许直接传含中文的 query
    parts = urllib.parse.urlsplit(args.path)
    path = urllib.parse.urlunsplit((parts.scheme, parts.netloc, urllib.parse.quote(parts.path),
                                    urllib.parse.quote(parts.query, safe="=&"), parts.fragment))
    url = args.base + path

    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        list(pool.map(one, [url] * args.warmup))
        t0 = time.perf_counter()
        results = list(pool.map(one, [url] * args.requests))
        wall = time.perf_counter() - t0

    lat = sorted(r[0] for r in results)
    errors = sum(1 for r in results if not r[1])
    print(f"url={url}")
    print(f"requests={args.requests} concurrency={args.concurrency} errors={errors} wall={wall:.2f}s QPS={args.requests / wall:.0f}")
    print(f"latency ms: avg={statistics.mean(lat):.1f} P50={pct(lat, 50):.1f} P95={pct(lat, 95):.1f} P99={pct(lat, 99):.1f} max={lat[-1]:.1f}")


if __name__ == "__main__":
    main()
