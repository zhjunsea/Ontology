# -*- coding: utf-8 -*-
# 终极核对：把每本书相邻篇之间的"源行区域"(a+1..b-1)逐行回库，量化未覆盖行(含短行)
import re, io, sys, os, glob
from collections import defaultdict
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/四书全文整理"
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
PUNCT=re.compile(r'[\s\u200b\u3000，。、；：！？·…—－\-\.,;:()（）“”"\'‘’《》〈〉%％|]')
def norm(s): return PUNCT.sub('', s or '')
NORM=norm(raw)

# classic_text 解析
m=re.search(r"INSERT INTO\s+`?classic_text`?\s*\(([^)]*)\)\s*VALUES", sql, re.S)
rest=sql[m.end():]
def top_tuples(s):
    res=[];depth=0;instr=False;i=0;start=None;n=len(s)
    while i<n:
        c=s[i]
        if instr:
            if c==chr(92): i+=2; continue
            if c=="'":
                if i+1<n and s[i+1]=="'": i+=2; continue
                instr=False
            i+=1; continue
        if c=="'": instr=True; i+=1; continue
        if c=='(':
            if depth==0: start=i+1
            depth+=1
        elif c==')':
            depth-=1
            if depth==0: res.append(s[start:i])
        elif c==';' and depth==0: break
        i+=1
    return res
def fields(t):
    out=[];i=0;n=len(t);instr=False;buf=[]
    while i<n:
        c=t[i]
        if instr:
            if c==chr(92): buf.append(t[i+1]); i+=2; continue
            if c=="'":
                if i+1<n and t[i+1]=="'": buf.append("'"); i+=2; continue
                instr=False; i+=1; continue
            buf.append(c); i+=1; continue
        if c=="'": instr=True; continue
        if c==',': out.append("".join(buf).strip()); buf=[]; i+=1; continue
        buf.append(c); i+=1
    out.append("".join(buf).strip()); return out
rows=[fields(t) for t in top_tuples(rest)]

BOOKFILE={"滴天髓":"滴天髓.txt","穷通宝鉴":"穷通宝鉴.txt","神峰通考":"神峰通考.txt",
          "三命通会":"三命通会.txt","渊海子平":"渊海子平.txt"}
bybook=defaultdict(list)
for f in rows:
    mm=re.search(r'·L(\d+)$', f[4])
    if mm: bybook[f[1]].append((int(mm.group(1)), f[0]))
PAGE=re.compile(r'^[0-9]{1,4}$')
NOISE=re.compile(r'^(未知|目录|目\s*录)$')
for book, lst in bybook.items():
    lst.sort()
    fn=os.path.join(BASE, BOOKFILE[book])
    if not os.path.exists(fn): 
        print(f"{book}: txt MISSING"); continue
    lines=open(fn,encoding='utf-8').read().splitlines()
    total_lines=len(lines)
    problems=[]
    for i in range(len(lst)-1):
        a=lst[i][0]; b=lst[i+1][0]
        if b-a<=1: continue
        for ln in range(a+1, b):   # 区域行(1-indexed)
            if ln>total_lines: break
            s=lines[ln-1].strip()
            if not s or NOISE.match(s) or PAGE.match(s): continue
            n=norm(s)
            if len(n)<1: continue
            if n not in NORM:
                problems.append((ln, s))
    print(f"=== {book}: 源 {total_lines} 行, 篇数 {len(lst)}, 相邻篇间未覆盖行 {len(problems)} ===")
    for ln, s in problems[:40]:
        print(f"    L{ln}: {s[:70]}")
