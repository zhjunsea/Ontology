# -*- coding: utf-8 -*-
"""全书未落区间穷尽扫描 v2（第四十三轮·从头总核）。

改进：判「接缝伪影」不以「≤3 片段」为限，而以**同源有序拼接**为准——
  · Java：同一文件内全部字面量按**出现序**拼接 → 一个串；
  · SQL：同一表全部行按**行序**拼接 → 一个串；
  · 另含各单片段。
底本任一段若落于某「有序拼接串」中，即为「照录在位（含分列/分段/序异之接缝）」；
否则为真缺（或非正文，须另行裁定）。
"""
import io, re, glob
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
BS = chr(92)

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
                if c == BS: out.append(src[i:i + 2]); i += 2; continue
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
                if c == BS: buf += src[j + 1:j + 2]; j += 2; continue
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

pieces = set()
ordered = []  # 同源有序拼接串

for f in glob.glob("src/main/**/*.java", recursive=True):
    lits = java_literals(io.open(f, encoding='utf-8', errors='ignore').read())
    for x in lits: pieces.add(cjk(x))
    # 仅长字面量（CJK≥12，即「照录正文」）按出现序拼接 → 近似重建 Java 侧正文流，滤去元数据短串
    ordered.append("".join(cjk(x) for x in lits if len(cjk(x)) >= 12))

for f in glob.glob("ontology/**/*.sql", recursive=True):
    sql = io.open(f, encoding='utf-8', errors='ignore').read()
    # per (table, col): 该列各行（行序）拼接
    bycol = {}
    for t, cols, vals in parse_rows(sql):
        pieces.add(cjk("".join(vals)))
        for idx, v in enumerate(vals):
            pieces.add(cjk(v))
            bycol.setdefault((t, idx), []).append(cjk(v))
    for k, cells in bycol.items():
        ordered.append("".join(cells))
    for m in re.finditer(r"'([^']*)'", sql): pieces.add(cjk(m.group(1)))
for pat in ["ontology/**/*.owl", "ontology/**/*.ttl"]:
    for f in glob.glob(pat, recursive=True):
        s = io.open(f, encoding='utf-8', errors='ignore').read()
        for m in re.finditer(r"'([^']*)'", s): pieces.add(cjk(m.group(1)))
        for m in re.finditer(r'"([^"]*)"', s): pieces.add(cjk(m.group(1)))

pieces = {p for p in pieces if p}
ordered = [o for o in ordered if o]
NC = cjk(io.open('design/_epub_dump.txt', encoding='utf-8').read())

# W-gram 集合（自单片段，用于快速窗口扫描）
W, MINGAP = 14, 12
grams = set()
for p in pieces:
    for k in range(len(p) - W + 1): grams.add(p[k:k + W])
cov = bytearray(len(NC)); j = 0
while j <= len(NC) - W:
    if NC[j:j + W] in grams: cov[j:j + W] = b"\x01" * W; j += W
    else: j += 1
gaps = []; i = 0
while i < len(NC):
    if cov[i] == 0:
        k = i
        while k < len(NC) and cov[k] == 0: k += 1
        if k - i >= MINGAP: gaps.append((i, k - i))
        i = k
    else: i += 1

def in_ordered(s):
    return s == "" or any(s in o for o in ordered)

res = []
for off, ln in gaps:
    G = NC[off:off + ln]
    if not in_ordered(G): res.append((off, ln, G))

print("[INFO] 片段数=%d 有序串数=%d 底本CJK=%d" % (len(pieces), len(ordered), len(NC)))
print("[INFO] 未落区间=%d；有序拼接可解释=%d；残余(真缺)=%d" % (len(gaps), len(gaps) - len(res), len(res)))
print("=" * 90)
for off, ln, G in res:
    a = max(0, off - 28); b = min(len(NC), off + ln + 28)
    g = (G[:70] + "……" + G[-40:]) if ln > 140 else G
    print("  !! [%d:%d] len=%d" % (off, off + ln, ln))
    print("     左…%s" % NC[a:off])
    print("     缺 【%s】" % g)
    print("     右 %s…" % NC[off + ln:b])
