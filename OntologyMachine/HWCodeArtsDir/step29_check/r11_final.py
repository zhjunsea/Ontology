# -*- coding: utf-8 -*-
# 第十一轮·终极口径(split('\n'))：A 严格整句覆盖  B 源txt全文逐行回库
import re, io, sys, os, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"

def sql_strings(text):
    res=[];i=0;n=len(text)
    while i<n:
        if text[i]=="'":
            i+=1;buf=[]
            while i<n:
                c=text[i]
                if c==chr(92) and i+1<n: buf.append(text[i+1]); i+=2; continue
                if c=="'":
                    if i+1<n and text[i+1]=="'": buf.append("'"); i+=2; continue
                    break
                buf.append(c); i+=1
            res.append("".join(buf)); i+=1
        else: i+=1
    return res
sql=open(SQL,encoding='utf-8').read()
cells=list(sql_strings(sql))
for fp in glob.glob(OWLDIR+"/*.owl")+glob.glob(OWLDIR+"/*.ttl"):
    cells.append(open(fp,encoding='utf-8').read())
raw="\n".join(cells)
PUNCT=re.compile(r'[\s\u200b\u3000\ufeff，。、；：！？·…—－\-\.,;:()（）“”"\'‘’《》〈〉%％|]')
def norm(s): return PUNCT.sub('', s or '')
NORM=norm(raw)

PAGE=re.compile(r'^[0-9\s\|]{1,8}$')
NOISE=re.compile(r'^(未知|目录|目\s*录|卷目)$')

BOOKS=["渊海子平.txt","三命通会.txt","子平真诠.txt","滴天髓.txt","神峰通考.txt","穷通宝鉴.txt"]

print("#"*70)
print("### A. 严格整句覆盖 (长句>=12字, split('\\n'))")
tot_all=hit_all=0
for fn in BOOKS:
    lines=open(os.path.join(BASE,fn),encoding='utf-8').read().split('\n')
    tot=hit=0; miss=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if not s or NOISE.match(s) or PAGE.match(s): continue
        for seg in re.split(r'[。！？!?]+', s):
            seg=seg.strip(); n=norm(seg)
            if len(n)>=12:
                tot+=1
                if n in NORM: hit+=1
                else: miss.append((i+1,seg))
    tot_all+=tot; hit_all+=hit
    print(f"  {fn}: 长句 {tot} 命中 {hit} 覆盖 {100.0*hit/tot if tot else 0:.1f}% 未命中 {len(miss)}")
    for l,seg in miss[:12]: print(f"      [L{l}] {seg[:60]}")
print(f"  == 合计 {tot_all} / 命中 {hit_all} = {100.0*hit_all/tot_all:.1f}%")

print("#"*70)
print("### B. 源txt全文逐行回库 (行归一化须在库; 结构噪声已滤)")
for fn in BOOKS:
    lines=open(os.path.join(BASE,fn),encoding='utf-8').read().split('\n')
    bad=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if not s or NOISE.match(s) or PAGE.match(s): continue
        n=norm(s)
        if len(n)<1: continue
        if n not in NORM: bad.append((i+1,s))
    print(f"  {fn}: 源行 {len(lines)}, 未覆盖行 {len(bad)}")
    for l,s in bad[:20]: print(f"      [L{l}] {s[:66]}")
