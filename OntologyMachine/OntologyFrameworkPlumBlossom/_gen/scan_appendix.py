#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""附录 汉字层未落扫描（仅取字面量语料）。"""
import io, re, glob
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
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
lits=[]
for f in glob.glob("src/main/**/*.java", recursive=True):
    lits += java_literals(io.open(f,encoding='utf-8',errors='ignore').read())
for pat in ["ontology/**/*.sql","ontology/**/*.owl","ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        lits += re.findall(r"'((?:[^']|'')*)'", io.open(f,encoding='utf-8',errors='ignore').read())
C = cjk("\n".join(lits))
ND = cjk(io.open('design/_epub_dump.txt',encoding='utf-8').read())
a = ND.find("附录附录邵康节易数"); b=len(ND)
print("region",a,b,"len",b-a)
seg=ND[a:b]; W=20; MINGAP=18
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
    print(f"[{off}:{off+ln}] len={ln}  {ND[off:off+70]}")
