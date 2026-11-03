# -*- coding: utf-8 -*-
# 第9轮：六典籍【正文内容级】复核 —— 每书正文切句 -> 与「DB全文 + OWL label + SKOS pref/alt」haystack 匹配
# 目的：回答「本体是否包含六书正文的具体内容」，量化覆盖率并定位未落库区域。
import sys, io, re, os, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
HAY = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r4_db_haystack.txt"

cells = [open(HAY, encoding='utf-8').read()]
for fp in glob.glob(OWLDIR + "/*.owl"):
    cells.append(open(fp, encoding='utf-8').read())
for fp in glob.glob(OWLDIR + "/*.ttl"):
    cells.append(open(fp, encoding='utf-8').read())
raw = "\n".join(cells)

PUNCT = re.compile(r'[\s　\u200b\u3000，。、；：！？·…—－\-\.,;:()（）“”"\'‘’《》〈〉%％]')
def norm(s):
    return PUNCT.sub('', s or '')

NORM = norm(raw)
PAGE = re.compile(r'^[0-9]{1,4}$')
HEAD = re.compile(r'^\s*(子平真诠|渊海子平|三命通会|滴天髓|神峰通考|穷通宝鉴)(评注|卷一|卷二)?\s*$')

GR = 12
def probes(n):
    if len(n) < GR:
        return []
    ps = [n[:GR]]
    if len(n) >= 2*GR:
        mid = len(n)//2
        ps.append(n[mid-GR//2: mid-GR//2+GR])
    ps.append(n[-GR:])
    return [p for p in set(ps) if len(p) == GR]

def matched(seg):
    n = norm(seg)
    ps = probes(n)
    if not ps:
        # 短句：整体子串
        return len(n) >= 4 and n in NORM
    return any(p in NORM for p in ps)

def sentences(lines):
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or s == '未知' or PAGE.match(s) or HEAD.match(s):
            continue
        for seg in re.split(r'[。！？!?]+', s):
            seg = seg.strip()
            if len(norm(seg)) >= 12:
                yield (i+1, seg)

BOOKS = ["渊海子平.txt","三命通会.txt","子平真诠.txt","滴天髓.txt","神峰通考.txt","穷通宝鉴.txt"]
tot_all = hit_all = 0
for fn in BOOKS:
    lines = open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()
    tot = hit = 0
    miss = []
    for ln, seg in sentences(lines):
        tot += 1
        if matched(seg):
            hit += 1
        else:
            miss.append((ln, seg))
    tot_all += tot; hit_all += hit
    print("="*72)
    print("### %s  句数 %d  命中 %d  未命中 %d  覆盖率 %.1f%%" %
          (fn, tot, hit, len(miss), (100.0*hit/tot if tot else 0)))
    # 聚合连续未命中（相邻行号间隔 <= 5 视为同区）
    run = []
    for ln, seg in miss:
        if run and ln - run[-1][0] <= 5:
            run.append((ln, seg))
        else:
            if run:
                print("   [UNCOVERED L%d~L%d 共%d句] %s" % (run[0][0], run[-1][0], len(run), run[0][1][:50]))
            run = [(ln, seg)]
    if run:
        print("   [UNCOVERED L%d~L%d 共%d句] %s" % (run[0][0], run[-1][0], len(run), run[0][1][:50]))
print("="*72)
print("TOTAL 句数 %d 命中 %d 覆盖率 %.1f%%" % (tot_all, hit_all, 100.0*hit_all/tot_all))
