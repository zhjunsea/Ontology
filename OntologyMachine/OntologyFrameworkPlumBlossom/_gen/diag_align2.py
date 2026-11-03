# -*- coding: utf-8 -*-
"""定位候选区间的片段分叉点：左上下文命中哪个片段，其尾随文本为何（自包含版）。"""
import io, re, glob
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))

BS = chr(92)  # backslash

def strip_comments(src):
    out = []; i = 0; n = len(src)
    while i < n:
        ch = src[i]
        if ch == '/' and i + 1 < n and src[i + 1] == '/':
            j = src.find(chr(10), i); i = n if j < 0 else j; continue
        if ch == '/' and i + 1 < n and src[i + 1] == '*':
            j = src.find('*/', i); i = n if j < 0 else j + 2; continue
        if ch == '"' or ch == "'":
            q = ch; out.append(ch); i += 1
            while i < n:
                c = src[i]
                if c == BS:
                    out.append(src[i:i + 2]); i += 2; continue
                out.append(c); i += 1
                if c == q: break
            continue
        out.append(ch); i += 1
    return "".join(out)

def java_literals(src):
    src = strip_comments(src); src = re.sub(r'"\s*\+\s*"', '', src)
    out = []; i = 0; n = len(src)
    while i < n:
        if src[i] == '"':
            j = i + 1; buf = ""
            while j < n:
                c = src[j]
                if c == BS:
                    buf += src[j + 1:j + 2]; j += 2; continue
                if c == '"': break
                buf += c; j += 1
            out.append(buf); i = j + 1; continue
        i += 1
    return out

def parse_rows(sql, table=None):
    for m in re.finditer(r"INSERT INTO\s+`?(\w+)`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n", sql, re.S):
        t = m.group(1)
        if table and t != table: continue
        cols = [c.strip().strip('`') for c in m.group(2).split(",")]
        body = m.group(3); rows = []; fields = []; buf = ""; i = 0; n = len(body); ins = False
        while i < n:
            ch = body[i]
            if ins:
                if ch == "'":
                    if i + 1 < n and body[i + 1] == "'": buf += "'"; i += 2; continue
                    ins = False
                else: buf += ch
            elif ch == "'": ins = True
            elif ch == "(": fields = []; buf = ""
            elif ch == ")": fields.append(buf.strip()); rows.append(list(fields)); fields = []
            elif ch == ",": fields.append(buf.strip()); buf = ""
            else: buf += ch
            i += 1
        for r in rows:
            if len(r) == len(cols): yield t, cols, r

srcs = []
for f in glob.glob("src/main/**/*.java", recursive=True):
    for x in java_literals(io.open(f, encoding='utf-8', errors='ignore').read()):
        srcs.append((f, x))
for f in glob.glob("ontology/**/*.sql", recursive=True):
    sql = io.open(f, encoding='utf-8', errors='ignore').read()
    for t, cols, vals in parse_rows(sql):
        srcs.append((f + "#" + t, "".join(vals)))
        for v in vals:
            srcs.append((f + "#" + t, v))
    for m in re.finditer(r"'([^']*)'", sql):
        srcs.append((f, m.group(1)))
for pat in ["ontology/**/*.owl", "ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        s = io.open(f, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r"'([^']*)'", s):
            srcs.append((f, m.group(1)))
        for m in re.finditer(r'"([^"]*)"', s):
            srcs.append((f, m.group(1)))

NC = cjk(io.open('design/_epub_dump.txt', encoding='utf-8').read())

def hits_containing(s):
    out = []
    for tag, t in srcs:
        p = cjk(t)
        if s and s in p:
            out.append((tag, p))
    return out

tests = [(60907, 16), (61191, 34), (61431, 26), (61977, 34), (63120, 43), (65334, 20)]
for off, ln in tests:
    print("=" * 100)
    print("OFF=%d LEN=%d  GAP=%s" % (off, ln, NC[off:off + ln]))
    hit = None
    for w in (40, 30, 20, 14, 10, 8):
        h = hits_containing(NC[off - w:off])
        if h:
            hit = (w, h); break
    if not hit:
        print("  左上下文(<=8字)未命中任何片段"); continue
    w, h = hit
    print("  以 %d 字左上下文命中 %d 个片段:" % (w, len(h)))
    for tag, p in h[:5]:
        L = NC[off - w:off]
        idx = p.rfind(L)
        tail = p[idx + len(L): idx + len(L) + ln + 25] if idx >= 0 else "<L未定位>"
        print("    [%s] 尾随= %s" % (tag.replace(chr(92), '/'), tail))
