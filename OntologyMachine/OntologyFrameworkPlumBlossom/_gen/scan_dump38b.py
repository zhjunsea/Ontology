#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 38 轮·通篇「未落区间」扫描（非抽样）。
把底本规范化为连续串 N；对「全部交付物语料」C（Java+SQL+OWL+TTL）求
每个 W 长窗口是否命中；标记得以覆盖的位置；输出未被覆盖的极大区间（=原著有文未落）。
W=30；仅报长度 ≥ MINGAP 的区间。"""
import os, re, glob, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
W = 30
MINGAP = 26


def norm(s):
    s = s.replace("&quot;", '"')
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n]", "", s)
    s = re.sub(r"[〔〕]", "", s)
    return s


files = (glob.glob(os.path.join(ROOT, "src/main/**/*.java"), recursive=True)
         + glob.glob(os.path.join(ROOT, "ontology/**/*.sql"), recursive=True)
         + glob.glob(os.path.join(ROOT, "ontology/**/*.owl"), recursive=True)
         + glob.glob(os.path.join(ROOT, "ontology/**/*.ttl"), recursive=True))
C = norm("\n".join(open(f, encoding="utf-8", errors="ignore").read() for f in files))
N = norm(open(EPUB, encoding="utf-8").read())
print(f"[corpus] files={len(files)} chars={len(C)} | dump chars={len(N)}", file=sys.stderr)

n = len(N)
cov = bytearray(n)
j = 0
while j <= n - W:
    if N[j:j + W] in C:
        cov[j:j + W] = b"\x01" * W
        j += W
    else:
        j += 1

# 极大未覆盖区间
gaps = []
i = 0
while i < n:
    if cov[i] == 0:
        k = i
        while k < n and cov[k] == 0:
            k += 1
        if k - i >= MINGAP:
            gaps.append((i, k - i, N[i:k]))
        i = k
    else:
        i += 1

gaps.sort(key=lambda x: -x[1])
print(f"[gaps] total={len(gaps)} (>= {MINGAP} chars)", file=sys.stderr)
for off, ln, txt in gaps:
    print(f"[{ln:>4}] @{off} {txt}")
