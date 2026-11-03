#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""附录 结构化覆盖：仅取 DB 文本列（按行序）拼接，与底本汉字层比对。"""
import io, re
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
def parse(sql, table):
    m = re.search(r"INSERT INTO\s+`?"+table+r"`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n", sql, re.S)
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
ND=cjk(io.open('design/_epub_dump.txt',encoding='utf-8').read())
a=ND.find("附录附录邵康节易数"); b=len(ND)
# 顺序：附录头 + 爻诀 + 八卦诀 + 渔樵 + 宋史（依底本）
parts=[]
for r in parse(sql,"appendix_text"):
    parts.append(r.get("text_body",""))
ap=parse(sql,"appendix_text"); print("appendix_text rows",len(ap),list(ap[0].keys()) if ap else None)
yj=parse(sql,"yicuo_jin_yao"); print("yicuo_jin_yao rows",len(yj),list(yj[0].keys()) if yj else None)

def scan(seg, C):
    W=20; MINGAP=18
    cov=bytearray(len(seg)); j=0
    while j<=len(seg)-W:
        if seg[j:j+W] in C: cov[j:j+W]=b"\x01"*W; j+=W
        else: j+=1
    gaps=[]; i=0
    while i<len(seg):
        if cov[i]==0:
            k=i
            while k<len(seg) and cov[k]==0: k+=1
            if k-i>=MINGAP: gaps.append((i,k))
            i=k
        else: i+=1
    return gaps

seg=ND[a:b]
C_all = cjk("".join(r["dictum_text"] for r in yj)) + "\n" + cjk("".join(r["text_body"] for r in ap))
print("=== 结构化语料(爻诀+附录文) 未落 ===")
g=scan(seg, C_all); print("GAPS",len(g))
for s,e in g: print(f"[{a+s}:{a+e}] len={e-s}  {ND[a+s:a+s+60]}")
