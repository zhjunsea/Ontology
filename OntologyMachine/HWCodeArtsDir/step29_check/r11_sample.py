# -*- coding: utf-8 -*-
import re, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()
m = re.search(r"INSERT INTO\s+`?classic_text`?\s*\(([^)]*)\)\s*VALUES", sql, re.S)
rest = sql[m.end():]
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
d={}
for t in top_tuples(rest):
    f=fields(t); d[f[0]]=f
for cid in ['CT_SMTH_006','CT_SMTH_007','CT_YH_190','CT_YH_191','CT_YH_193',
            'CT_DTS_07','CT_DTS_08']:
    f=d.get(cid)
    if not f: print(f"{cid}: MISSING"); continue
    txt=f[3]
    print(f"{cid} [{f[2]}] len={len(txt)} 首60={txt[:60]} ... 尾60={txt[-60:]}")
