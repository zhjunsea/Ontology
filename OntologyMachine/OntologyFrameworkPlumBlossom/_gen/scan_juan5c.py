#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""卷之五 汉字层未落扫描：仅取 CJK，避免标点/引号噪声。"""
import io, re, glob
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
ND = io.open('design/_epub_dump.txt',encoding='utf-8').read()
Nc = cjk(ND)
C="".join(cjk(io.open(f,encoding='utf-8',errors='ignore').read())
    for pat in ["src/main/**/*.java","ontology/**/*.sql","ontology/**/*.owl","ontology/**/*.ttl"]
    for f in glob.glob(pat, recursive=True))
a = Nc.find("拆字杂编五行全备"); b = Nc.find("附录附录邵康节易数", a)
print("region chars", a, b)
seg = Nc[a:b]; W=20; MINGAP=18
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
print("GAPS(cjk):",len(gaps))
for off,ln in gaps:
    print(f"[{off}:{off+ln}] len={ln}  {Nc[off:off+50]}")
