#!/usr/bin/env python3
"""商品知识问答评测 (仅依赖 Python 标准库)。

指标:
  Hit@K    : 检索到的前 K 个片段里, 是否有片段包含期望关键词之一
  MRR      : 第一个命中片段的排名倒数的平均值
  Accuracy : (加 --answer) 生成的答案是否包含期望关键词之一 (关键词匹配, 是粗略代理指标)

数据集 (jsonl) 每行: {"question": "...", "modelNo": "LT-1001", "expected": ["关键词1", "关键词2"]}

用法:
  python eval/run_eval.py --user admin --password admin123
  python eval/run_eval.py --user admin --password admin123 --answer --k 5
"""
import argparse
import json
import sys
import urllib.parse
import urllib.request


def call(base, path, token=None, body=None, timeout=120):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method="POST" if data else "GET")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def norm(s):
    return "".join(s.split())  # 去掉所有空白, 避免 "2 年" / "2年" 因空格不一致而误判


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--dataset", default="eval/dataset.jsonl")
    ap.add_argument("--user", required=True)
    ap.add_argument("--password", required=True)
    ap.add_argument("--k", type=int, default=5)
    ap.add_argument("--answer", action="store_true", help="同时评测生成答案 (会调用大模型)")
    args = ap.parse_args()

    token = call(args.base, "/api/users/login", body={"username": args.user, "password": args.password})["data"]["token"]
    with open(args.dataset, encoding="utf-8") as f:
        items = [json.loads(line) for line in f if line.strip()]
    if not items:
        sys.exit("数据集为空")

    hit = rr = acc = 0.0
    for it in items:
        qs = urllib.parse.urlencode({"q": it["question"], "modelNo": it.get("modelNo", ""), "topK": args.k})
        chunks = call(args.base, "/api/ai/knowledge/search?" + qs, token)["data"][: args.k]
        expected = [norm(e) for e in it["expected"]]
        rank = next((i for i, c in enumerate(chunks, 1) if any(e in norm(c["text"]) for e in expected)), None)
        hit += rank is not None
        rr += 1.0 / rank if rank else 0.0

        ok = ""
        if args.answer:
            ans = call(args.base, "/api/ai/knowledge/ask", token,
                       {"question": it["question"], "modelNo": it.get("modelNo")})["data"]["answer"]
            good = any(e in norm(ans) for e in expected)
            acc += good
            ok = "  answer=" + ("OK" if good else "WRONG")
        print(f"{'HIT ' if rank else 'MISS'} rank={rank}{ok}  {it['question']}")

    n = len(items)
    line = f"\nsamples={n}  Hit@{args.k}={hit / n:.3f}  MRR={rr / n:.3f}"
    if args.answer:
        line += f"  Accuracy={acc / n:.3f}"
    print(line)


if __name__ == "__main__":
    main()
