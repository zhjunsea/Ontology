#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全书从头未落区间穷尽扫描（第四十三轮·从头总核）。

语料 = 全部交付物「所承载之片段」（教训⑳/㉑/㉒/㉓ 之集大成）：
  A. Java 引号内字面量：先剥注释、并相邻 "…" + "…"（㉑）。
  B. SQL 每条 INSERT 之**逐行重建**（各列拼接）＋ 单列值。
  C. OWL/TTL/SQL 单/双引号字面量。
底本 = design/_epub_dump.txt 规范化（仅 CJK，含书首/五卷/附录全）。
法：窗口扫描（W/MINGAP）求未落区间；对每区间作**接缝可解释性判定**（≤3 片段拼接）。
输出：未落区间总数、可解释（伪影）数、残余（真缺）数及明细。
"""
import io, re, glob

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))

def strip_comments(src):
    out=[]; i=0; n=len(src)
    while i<n:
        ch=src[i]
        if ch=='/' and i+1<n and src[i+1]=='/':
            j=src.find('\n',i); i=n if j<0 else j; continue
        if ch=='/' and i+1<n and src[i+1]=='*':
            j=src.find('*/',i); i=n if j<0 else j+2; continue
        if ch=='"' or ch=="'":
            q=ch; out.append(ch); i+=1
            while i<n:
                c=src[i]
                if c=='\\': out.append(src[i:i+2]); i+=2; continue
                out.append(c); i+=1
                if c==q: break
            continue
        out.append(ch); i+=1
    return "".join(out)

def java_literals(src):
    src = strip_comments(src)
    src = re.sub(r'"\s*\+\s*"', '', src)
    out=[]; i=0; n=len(src)
    while i<n:
        if src[i]=='"':
            j=i+1; buf=""
            while j<n:
                c=src[j]
                if c=='\\': buf+=src[j+1:j+2]; j+=2; continue
                if c=='"': break
                buf+=c; j+=1
            out.append(buf); i=j+1; continue
        i+=1
    return out

def parse_rows(sql, table=None):
    for m in re.finditer(r"INSERT INTO\s+`?(\w+)`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n", sql, re.S):
        t=m.group(1)
        if table and t!=table: continue
        cols=[c.strip().strip('`') for c in m.group(2).split(",")]
        body=m.group(3); rows=[]; fields=[]; buf=""; i=0; n=len(body); ins=False
        while i<n:
            ch=body[i]
            if ins:
                if ch=="'":
                    if i+1<n and body[i+1]=="'": buf+="'"; i+=2; continue
                    ins=False
                else: buf+=ch
            elif ch=="'": ins=True
            elif ch=="(": fields=[]; buf=""
            elif ch==")": fields.append(buf.strip()); rows.append(list(fields)); fields=[]
            elif ch==",": fields.append(buf.strip()); buf=""
            else: buf+=ch
            i+=1
        for r in rows:
            if len(r)==len(cols): yield t, cols, r

pieces=set()
for f in glob.glob("src/main/**/*.java", recursive=True):
    for x in java_literals(io.open(f, encoding='utf-8', errors='ignore').read()):
        pieces.add(cjk(x))
for f in glob.glob("ontology/**/*.sql", recursive=True):
    sql=io.open(f, encoding='utf-8', errors='ignore').read()
    for t, cols, vals in parse_rows(sql):
        pieces.add(cjk("".join(vals)))
        for v in vals: pieces.add(cjk(v))
    for m in re.finditer(r"'([^']*)'", sql): pieces.add(cjk(m.group(1)))
for pat in ["ontology/**/*.owl", "ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        s=io.open(f, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r"'([^']*)'", s): pieces.add(cjk(m.group(1)))
        for m in re.finditer(r'"([^"]*)"', s): pieces.add(cjk(m.group(1)))
pieces = {p for p in pieces if p}
# 分隔符为非 CJK（"\n"）→ 纯 CJK 子串不可能跨片段命中，故 `s in C` 与「逐片段 any」等价（教训㉔）
C = "\n".join(sorted(pieces))

NC = cjk(io.open('design/_epub_dump.txt', encoding='utf-8').read())

def contained(s):
    return s=="" or s in C

def explain(G):
    # 第 47 轮硬化：由「≤2 段拼接」改为「n 段拼接」DP。
    # 缘由：附录·渔樵对问等分列极细（每「曰…」各为一行 `appendix_text`），
    # 一未落区间常跨 ≥3 行边界，2-split 无法解释遂误判「真缺」（教训㉘）。
    # 语义：G 可否切为若干**连续**子串，各子串皆为一「单一片段」之子串（\n 非 CJK，故不跨片段）。
    n=len(G)
    if contained(G): return ("0-split",)
    reach=[-1]*(n+1); reach[0]=0
    for i in range(n):
        if reach[i]<0: continue
        j=i+1
        while j<=n and contained(G[i:j]):   # 子串性单调：一旦不合即止
            if reach[j]<0: reach[j]=i
            j+=1
    if reach[n]<0: return None
    # 回溯段数
    k=0; p=n
    while p>0: p=reach[p]; k+=1
    return ("n-split", k)

W, MINGAP = 14, 12
# 预建 W-gram 集合（自全部片段），使窗口扫描 O(1)/位（教训㉔）
grams=set()
for p in pieces:
    for k in range(len(p)-W+1): grams.add(p[k:k+W])
cov = bytearray(len(NC)); j=0
while j <= len(NC)-W:
    if NC[j:j+W] in grams: cov[j:j+W]=b"\x01"*W; j+=W
    else: j+=1
gaps=[]; i=0
while i < len(NC):
    if cov[i]==0:
        k=i
        while k < len(NC) and cov[k]==0: k+=1
        if k-i >= MINGAP: gaps.append((i, k-i))
        i=k
    else: i+=1

print(f"[INFO] 语料片段数 = {len(pieces)}   底本 CJK 全长 = {len(NC)}")
print(f"[INFO] 窗口 W={W} MINGAP={MINGAP}  未落区间 = {len(gaps)}")
res=[]
for off, ln in gaps:
    G = NC[off:off+ln]
    e = explain(G)
    if not e: res.append((off, ln, G))
print(f"[INFO] 可解释（伪影） = {len(gaps)-len(res)}；残余（真缺）= {len(res)}")
for off, ln, G in res:
    print(f"  !! [{off}:{off+ln}] len={ln}  {G[:70]}")
print("=" * 90)
# 附：输出所有未落区间（便于逐段裁定），含可解释者
for off, ln in gaps:
    G = NC[off:off+ln]
    e = explain(G)
    print(f"  [{off}:{off+ln}] len={ln}  {'伪影' if e else '真缺'}  {G[:60]}")
