# -*- coding: utf-8 -*-
import io, re, glob
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
                if c=='\': out.append(src[i:i+2]); i+=2; continue
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
                if c=='\': buf+=src[j+1:j+2]; j+=2; continue
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
            if len(r)==len(cols): yield t,cols,r

srcs=[]  # (tag, text)
for f in glob.glob("src/main/**/*.java", recursive=True):
    for x in java_literals(io.open(f,encoding='utf-8',errors='ignore').read()):
        srcs.append((f,x))
for f in glob.glob("ontology/**/*.sql", recursive=True):
    sql=io.open(f,encoding='utf-8',errors='ignore').read()
    for t,cols,vals in parse_rows(sql):
        srcs.append((f+"#"+t,"".join(vals)))
        for v in vals: srcs.append((f+"#"+t,v))
    for m in re.finditer(r"'([^']*)'", sql): srcs.append((f,m.group(1)))
for pat in ["ontology/**/*.owl","ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        s=io.open(f,encoding='utf-8',errors='ignore').read()
        for m in re.finditer(r"'([^']*)'", s): srcs.append((f,m.group(1)))
        for m in re.finditer(r'"([^"]*)"', s): srcs.append((f,m.group(1)))

NC=cjk(io.open('design/_epub_dump.txt',encoding='utf-8').read())

def find_piece_containing(s):
    return [ (tag,cjk(t)) for tag,t in srcs if s in cjk(t) ]

tests=[(60907,16),(61191,34),(61431,26),(61977,34),(63120,43),(65334,20)]
for off,ln in tests:
    L=NC[off-40:off]
    print("="*100)
    print(f"OFF={off} LEN={ln}  GAP={NC[off:off+ln]}")
    hits=find_piece_containing(L)
    if not hits:
        print("  左上下文未在任何片段中命中！L=",L)
        # try shorter
        for w in (20,14,10,8,6):
            hits=find_piece_containing(NC[off-w:off])
            if hits: print(f"   以 {w} 字命中 {len(hits)} 片段"); break
    for tag,p in hits[:4]:
        # 对齐：找到 L 在 p 中的位置
        idx=p.rfind(L)
        if idx<0:
            print(f"  [{tag}] 含片段 {len(p)} 字, L 未定位")
            continue
        tail=p[idx+len(L):idx+len(L)+len(ln)+20]
        print(f"  [{tag}] 片段尾随 = {tail}")
