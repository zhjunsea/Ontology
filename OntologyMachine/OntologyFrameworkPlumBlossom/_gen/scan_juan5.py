#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io, re, glob
def norm(x):
    x = x.replace("&quot;", '"'); x = re.sub(r"\[\d+\]", "", x)
    x = x.replace("\u3000","").replace(" ",""); x = re.sub(r"[\r\n]","",x); x = re.sub(r"[〔〕]","",x)
    return x
ND = norm(io.open('design/_epub_dump.txt',encoding='utf-8').read())
C="\n".join(norm(re.sub(r'"\s*\+\s*"','',io.open(f,encoding='utf-8',errors='ignore').read()))
    for pat in ["src/main/**/*.java","ontology/**/*.sql","ontology/**/*.owl","ontology/**/*.ttl"]
    for f in glob.glob(pat, recursive=True))
A,B=57463,69352
seg=ND[A:B]; W=30; MINGAP=26
cov=bytearray(len(seg))
j=0
while j<=len(seg)-W:
    if seg[j:j+W] in C:
        cov[j:j+W]=b"\x01"*W; j+=W
    else: j+=1
gaps=[]; i=0
while i<len(seg):
    if cov[i]==0:
        k=i
        while k<len(seg) and cov[k]==0: k+=1
        if k-i>=MINGAP: gaps.append((A+i,k-i))
        i=k
    else: i+=1
print("GAPS:",len(gaps))
for off,ln in gaps:
    print(f"[{off}:{off+ln}] len={ln}  {ND[off:off+34]} … {ND[off+ln-16:off+ln]}")
