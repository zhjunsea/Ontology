# -*- coding: utf-8 -*-
"""对残余未落区间，取「含其左侧上下文」的片段，做逐字对齐，定位分叉点。"""
import io, re, glob
exec(open('_gen/scan_whole2.py', encoding='utf-8').read().split('pieces = set()')[0])

srcs = []
for f in glob.glob("src/main/**/*.java", recursive=True):
    for x in java_literals(io.open(f, encoding='utf-8', errors='ignore').read()):
        srcs.append((f, cjk(x)))
for f in glob.glob("ontology/**/*.sql", recursive=True):
    sql = io.open(f, encoding='utf-8', errors='ignore').read()
    for t, cols, vals in parse_rows(sql):
        srcs.append((f + "#" + t, cjk("".join(vals))))
        for v in vals:
            srcs.append((f + "#" + t, cjk(v)))
    for m in re.finditer(r"'([^']*)'", sql):
        srcs.append((f, cjk(m.group(1))))
for pat in ["ontology/**/*.owl", "ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        s = io.open(f, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r"'([^']*)'", s):
            srcs.append((f, cjk(m.group(1))))
        for m in re.finditer(r'"([^"]*)"', s):
            srcs.append((f, cjk(m.group(1))))

NC = cjk(io.open('design/_epub_dump.txt', encoding='utf-8').read())

resid = [(0,435),(953,521),(6331,4100),(10445,55),(10534,12),(10560,14),(10610,84),
(10988,18),(15567,15),(17329,129),(18056,12),(19528,12),(21230,16),(22852,38),
(25267,19),(34306,164),(45438,183),(55502,15),(55629,16),(60863,16)]

for off, ln in resid:
    print("=" * 100)
    print("OFF=%d LEN=%d" % (off, ln))
    best = None
    for w in (30, 24, 20, 16, 14, 12, 10):
        cand = [(tag, p) for tag, p in srcs if p and NC[off - w:off] in p]
        if cand:
            best = (w, cand); break
    if not best:
        print("  左侧(<=10)未命中任何片段"); continue
    w, cand = best
    L = NC[off - w:off]
    print("  左侧%d字命中 %d 片段" % (w, len(cand)))
    for tag, p in cand[:2]:
        idx = p.rfind(L)
        ptail = p[idx + w:]
        # 计算 ptail 与 NC[off:] 的最长公共前缀
        k = 0
        while k < len(ptail) and off + k < len(NC) and ptail[k] == NC[off + k]:
            k += 1
        print("    [%s]" % tag.replace(chr(92), '/').split('/')[-1])
        print("      片段继以 : %s" % ptail[:44])
        print("      底本继以 : %s" % NC[off:off + 44])
        print("      公共前缀长度 = %d" % k)
