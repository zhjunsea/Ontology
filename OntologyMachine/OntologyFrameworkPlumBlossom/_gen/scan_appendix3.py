#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""附录 结构化覆盖（爻诀=卦名+爻位+爻名+等级+辞；附录文=text_body）。"""
import io, re, glob
CJK=re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
def parse(sql, table):
    m=re.search(r"INSERT INTO\s+`?"+table+r"`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n", sql, re.S)
    cols=[c.strip().strip('`') for c in m.group(1).split(",")]
    body=m.group(2); rows=[]; fields=[]; buf=""; i=0; n=len(body); ins=False
    while i<n:
        ch=body[i]
        if ins:
            if ch=="'":
                if i+1<n and body[i+1]=="'": buf+="''"; i+=2; continue
                ins=False; buf+=ch
            else: buf+=ch
        elif ch=="'": ins=True; buf+=ch
        elif ch=="(": fields=[]; buf=""
        elif ch==")": fields.append(buf.strip()); rows.append(list(fields)); fields=[]; buf=""
        elif ch==",": fields.append(buf.strip()); buf=""
        else: buf+=ch
        i+=1
    out=[]
    for r in rows:
        vals=[]
        for f in r:
            f=f.strip()
            if f.startswith("'") and f.endswith("'"): f=f[1:-1].replace("''","'")
            vals.append(f)
        if len(vals)==len(cols): out.append(dict(zip(cols,vals)))
    return out
sql=io.open("ontology/database/data.sql",encoding="utf-8").read()
N=io.open('design/_epub_dump.txt',encoding='utf-8').read(); NC=cjk(N)
a=NC.find("邵康节易数一撮金",50000); b=len(NC)
seg=NC[a:b]
yj=parse(sql,"yicuo_jin_yao"); ap=parse(sql,"appendix_text")
print("yj rows",len(yj),"ap rows",len(ap))
C=cjk("".join((r["hexagram_name"]+r["line_position"]+r["yao_label"]+r["grade"]+r["dictum_text"]) for r in yj))
C+="\n"+cjk("".join(r["text_body"] for r in ap))
W=16; MINGAP=14
cov=bytearray(len(seg)); j=0
while j<=len(seg)-W:
    if seg[j:j+W] in C: cov[j:j+W]=b"\x01"*W; j+=W
    else: j+=1
gaps=[]; i=0
while i<len(seg):
    if cov[i]==0:
        k=i
        while k<len(seg) and cov[k]==0: k+=1
        if k-i>=MINGAP: gaps.append((a+i,k-i))
        i=k
    else: i+=1
print("GAPS:",len(gaps))
for off,ln in gaps:
    print(f"[{off}:{off+ln}] len={ln}  {NC[off:off+60]}")
# 一撮金起卦法 是否在语料
k="其法以楷书字数其笔画以起数得卦"
print("起卦法片段在语料:", k in C or cjk(k) in C)

# 补：Java 全字面量（含 YICUOJIN_ALLOC / APPENDIX_TEXT / BA_GUA_JUE 内置兜底）
def java_literals(src):
    out=[]; i=0; n=len(src)
    while i<n:
        ch=src[i]
        if ch=='/' and i+1<n and src[i+1]=='/':
            j=src.find('\n',i); i=n if j<0 else j; continue
        if ch=='/' and i+1<n and src[i+1]=='*':
            j=src.find('*/',i); i=n if j<0 else j+2; continue
        if ch=='"':
            j=i+1; buf=""
            while j<n:
                c=src[j]
                if c=='\\': buf+=src[j+1:j+2]; j+=2; continue
                if c=='"' or c=='\n': break
                buf+=c; j+=1
            out.append(buf); i=j+1; continue
        i+=1
    return out
jl=[]
for f in glob.glob("src/main/**/*.java", recursive=True):
    jl += java_literals(io.open(f,encoding='utf-8',errors='ignore').read())
C2 = cjk("".join(jl)) + C
cov=bytearray(len(seg)); j=0
while j<=len(seg)-W:
    if seg[j:j+W] in C2: cov[j:j+W]=b"\x01"*W; j+=W
    else: j+=1
gaps=[]; i=0
while i<len(seg):
    if cov[i]==0:
        k=i
        while k<len(seg) and cov[k]==0: k+=1
        if k-i>=MINGAP: gaps.append((a+i,k-i))
        i=k
    else: i+=1
print("=== 合并(DB结构+Java字面量) GAPS:",len(gaps))
for off,ln in gaps:
    print(f"[{off}:{off+ln}] len={ln}  {NC[off:off+60]}")
