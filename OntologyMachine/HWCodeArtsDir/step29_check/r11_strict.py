# -*- coding: utf-8 -*-
# 第十一轮【严格整句】复核：长句必须"整句(归一化)"在库，而非三探针任一（防截断/丢句盲区）
import re, io, sys, os, glob, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/四书全文整理"
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"

def sql_strings(text):
    res = []; i = 0; n = len(text)
    while i < n:
        if text[i] == "'":
            i += 1; buf = []
            while i < n:
                c = text[i]
                if c == '\\' and i + 1 < n:
                    buf.append(text[i + 1]); i += 2; continue
                if c == "'":
                    if i + 1 < n and text[i + 1] == "'":
                        buf.append("'"); i += 2; continue
                    break
                buf.append(c); i += 1
            res.append("".join(buf)); i += 1
        else:
            i += 1
    return res

sql = open(SQL, encoding='utf-8').read()
strs = sql_strings(sql)

# classic_text 每书篇章数
ct = collections.Counter()
for m in re.finditer(r"\('CT_[^']*',\s*'([^']*)',\s*'([^']*)'", sql):
    ct[m.group(1)] += 1
print("--- classic_text 各书篇章数 ---")
for b, c in ct.items():
    print(f"  {b}: {c}")

cells = list(strs)
for fp in glob.glob(OWLDIR + "/*.owl") + glob.glob(OWLDIR + "/*.ttl"):
    cells.append(open(fp, encoding='utf-8').read())
raw = "\n".join(cells)

PUNCT = re.compile(r'[\s\u200b\u3000，。、；：！？·…—－\-\.,;:()（）“”"\'‘’《》〈〉%％|]')
def norm(s): return PUNCT.sub('', s or '')
NORM = norm(raw)

PAGE = re.compile(r'^[0-9]{1,4}$')
HEADNOISE = re.compile(r'^(中国古典精华文库|子平真诠评注|造化元钥评注|徐乐吾评注|目录|目\s*录|卷目|未知)$')

BOOKS = ["渊海子平.txt", "三命通会.txt", "子平真诠.txt", "滴天髓.txt", "神峰通考.txt", "穷通宝鉴.txt"]
print("=" * 72)
print("### 严格整句覆盖（长句>=12字，整句归一化后须在库）")
tot_all = hit_all = 0
for fn in BOOKS:
    lines = open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()
    tot = hit = 0; miss = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or s == '未知' or PAGE.match(s) or HEADNOISE.match(s):
            continue
        for seg in re.split(r'[。！？!?]+', s):
            seg = seg.strip()
            n = norm(seg)
            if len(n) >= 12:
                tot += 1
                if n in NORM:
                    hit += 1
                else:
                    miss.append((i + 1, seg))
    tot_all += tot; hit_all += hit
    print(f"  {fn}: 长句 {tot} 严格命中 {hit} 严格覆盖率 {100.0*hit/tot if tot else 0:.1f}% 未命中 {len(miss)}")
    for l, seg in miss[:15]:
        print(f"      [未命中 L{l}] {seg[:56]}")
print("=" * 72)
print(f"严格整句合计 {tot_all} 命中 {hit_all} 覆盖率 {100.0*hit_all/tot_all:.1f}%")
