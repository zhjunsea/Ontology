#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""区域诊断：给定 CJK 子串，报告是否落在交付物语料（片段）中，并列出命中片段与承载源。"""
import io, re, glob, sys
sys.path.insert(0,'_gen')
CJK=re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
def strip_comments(src):
    out=[];i=0;n=len(src)
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
    src=strip_comments(src); src=re.sub(r'"\s*\+\s*"','',src)
    out=[];i=0;n=len(src)
    while i<n:
        if src[i]=='"':
            j=i+1;buf=""
            while j<n:
                c=src[j]
                if c=='\\': buf+=src[j+1:j+2]; j+=2; continue
                if c=='"': break
                buf+=c; j+=1
            out.append(buf); i=j+1; continue
        i+=1
    return out
srcs=[]
for f in glob.glob("src/main/**/*.java",recursive=True):
    for x in java_literals(io.open(f,encoding='utf-8',errors='ignore').read()):
        srcs.append((cjk(x),f))
for f in glob.glob("ontology/**/*.sql",recursive=True):
    s=io.open(f,encoding='utf-8',errors='ignore').read()
    for m in re.finditer(r"'([^']*)'",s): srcs.append((cjk(m.group(1)),f))
for pat in ["ontology/**/*.owl","ontology/**/*.ttl"]:
    for f in glob.glob(pat,recursive=True):
        s=io.open(f,encoding='utf-8',errors='ignore').read()
        for m in re.finditer(r"'([^']*)'",s): srcs.append((cjk(m.group(1)),f))
        for m in re.finditer(r'"([^"]*)"',s): srcs.append((cjk(m.group(1)),f))
srcs=[(p,f) for p,f in srcs if p]
def query(q, show=3):
    q=cjk(q)
    hits=[(p,f) for p,f in srcs if q and q in p]
    print(f"  query «{q}»  len={len(q)}  hits={len(hits)}")
    for p,f in hits[:show]:
        i=p.find(q); s=max(0,i-8); e=min(len(p),i+len(q)+8)
        print(f"      …{p[s:i]}【{q}】{p[i+len(q):e]}…   <{f.split('/')[-1]}>")
    return hits
if __name__=="__main__":
    for line in sys.argv[1:]:
        query(line)
