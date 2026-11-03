#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 38 轮·通篇「有文未落」扫描（非抽样）。
底本逐段（连续非空行成段）→ 对 Java+SQL 语料求「最长连续命中」(maxrun)；
maxrun < THRESH 者列为候选「未落/仅转述」。排除类象表段与脚注残段。"""
import os, re, sys, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EPUB = os.path.join(ROOT, "design/_epub_dump.txt")
THRESH = 40


def norm(s):
    s = s.replace("&quot;", '"')
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("\u3000", "").replace(" ", "")
    s = re.sub(r"[\r\n]", "", s)
    s = re.sub(r"[〔〕]", "", s)
    return s


raw = open(EPUB, encoding="utf-8").read()
# 段落：以空行/脚注行分割
lines = raw.replace("\r\r\n", "\n").replace("\r", "\n").split("\n")

corpus = []
for f in glob.glob(os.path.join(ROOT, "src/main/**/*.java"), recursive=True) + \
         glob.glob(os.path.join(ROOT, "src/main/**/*.sql"), recursive=True) + \
         glob.glob(os.path.join(ROOT, "src/main/**/*.owl"), recursive=True):
    corpus.append(open(f, encoding="utf-8", errors="ignore").read())
CORPUS = norm("\n".join(corpus))
print(f"[corpus] {len(CORPUS)} chars", file=sys.stderr)

paras = []
buf = []
for ln in lines:
    t = ln.strip()
    if t == "":
        if buf:
            paras.append("\n".join(buf)); buf = []
    else:
        buf.append(ln)
if buf:
    paras.append("\n".join(buf))


def pmax(para, thresh):
    n = len(para)
    if n == 0:
        return 0
    if n <= thresh:
        return n if para in CORPUS else 0
    # P(thresh)?
    any_hit = any(para[i:i + thresh] in CORPUS for i in range(n - thresh + 1))
    if any_hit:
        return thresh  # treat as landed (>= thresh)
    # exact maxrun via binary search below thresh
    lo, hi = 0, thresh
    while lo < hi:
        mid = (lo + hi + 1) // 2
        found = any(para[i:i + mid] in CORPUS for i in range(n - mid + 1))
        if found:
            lo = mid
        else:
            hi = mid - 1
    return lo


cands = []
foot = re.compile(r"^\s*(\[\d+\]\s*)+$")
for p in paras:
    np = norm(p)
    if len(np) < 12:
        continue
    if np.startswith("［") and "］" in np[:12]:
        continue  # 类象表段（tokenized in OWL）
    if all(foot.match(x) for x in p.split("\n")):
        continue
    mr = pmax(np, THRESH)
    if mr < THRESH:
        cands.append((mr, np))

cands.sort()
print(f"[paras] total={len(paras)}  cands(maxrun<{THRESH})={len(cands)}", file=sys.stderr)
for mr, np in cands:
    print(f"[{mr:>3}] {np[:110]}")
