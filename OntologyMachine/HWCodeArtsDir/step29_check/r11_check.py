# -*- coding: utf-8 -*-
# 第十一轮：六典籍【篇章级 + 内容级】独立复核（纯只读）
# - haystack 直接由 data.sql（静态全量导出）重建，不依赖运行中的 MySQL
# - 维度1：六书候选标题/篇章 -> 是否在 DB/OWL/SKOS 中留痕
# - 维度2：六书正文句覆盖率（≥12字三探针 + 4~11字短句盲区）
import re, io, sys, os, glob, collections

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"

def sql_strings(text):
    res = []; i = 0; n = len(text)
    while i < n:
        if text[i] == "'":
            i += 1; buf = []
            while i < n:
                c = text[i]
                if c == '\\' and i + 1 < n:
                    buf.append(text[i + 1]); i += 2; continue
                if c == "'":
                    if i + 1 < n and text[i + 1] == "'":
                        buf.append("'"); i += 2; continue
                    break
                buf.append(c); i += 1
            res.append("".join(buf)); i += 1
        else:
            i += 1
    return res

sql = open(SQL, encoding='utf-8').read()
strs = sql_strings(sql)
print("data.sql 字符串字面量总数:", len(strs))

rows_count = collections.Counter()
for m in re.finditer(r"INSERT INTO `(\w+)`", sql):
    tbl = m.group(1)
    nxt = sql.find("INSERT INTO `", m.end())
    seg = sql[m.end(): nxt if nxt != -1 else len(sql)]
    cnt = len(re.findall(r"^\s*\(", seg, re.M))
    rows_count[tbl] += cnt
print("--- 各表行数 ---")
for t, c in sorted(rows_count.items(), key=lambda x: -x[1]):
    print(f"  {t}: {c}")

cells = list(strs)
for fp in glob.glob(OWLDIR + "/*.owl") + glob.glob(OWLDIR + "/*.ttl"):
    cells.append(open(fp, encoding='utf-8').read())
raw = "\n".join(cells)

PUNCT = re.compile(r'[\s\u200b\u3000，。、；：！？·…—－\-\.,;:()（）“”"\'‘’《》〈〉%％|]')
def norm(s): return PUNCT.sub('', s or '')

NORM = norm(raw)
PAGE = re.compile(r'^[0-9]{1,4}$')
SEQ = re.compile(r'^(第?[一二三四五六七八九十百零〇\d]+[、\.）\)])')
HEADNOISE = re.compile(r'^(中国古典精华文库|子平真诠评注|造化元钥评注|徐乐吾评注|目录|目\s*录|卷目|未知)$')

def clean(s):
    t = s.strip()
    t = re.sub(r'^[○●·•\s、]+', '', t)
    t = re.sub(r'^[（(]?[一二三四五六七八九十百零〇\d]+[、\.）\)]?\s*', '', t)
    return t

def is_title(s):
    t = s.strip()
    if not t or HEADNOISE.match(t): return False
    if PAGE.match(t): return False
    if '…' in t or '...' in t: return False
    n = norm(clean(t))
    if not (2 <= len(n) <= 18): return False
    if re.search(r'[。！？!?]$', t): return False
    return True

def hit_title(s):
    t = s.strip()
    for cand in (norm(clean(t)), norm(t)):
        if cand and cand in NORM:
            return True
    return False

BOOKS = ["渊海子平.txt", "三命通会.txt", "子平真诠.txt", "滴天髓.txt", "神峰通考.txt", "穷通宝鉴.txt"]
book_lines = {}
print("=" * 72)
print("### 维度1：篇章/标题级覆盖")
for fn in BOOKS:
    lines = open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()
    book_lines[fn] = lines
    tot = 0; miss = []
    for i, ln in enumerate(lines):
        if is_title(ln):
            tot += 1
            if not hit_title(ln):
                miss.append((i + 1, ln.strip()))
    print("=" * 60)
    print(f"### {fn} 候选标题 {tot}  未命中 {len(miss)}")
    for l, s in miss[:80]:
        print(f"   L{l}: {s}")

# ---- 维度2：正文句覆盖 ----
print("=" * 72)
print("### 维度2：正文句覆盖（复跑 r9 方法 + 短句盲区）")
GR = 12
def probes(n):
    if len(n) < GR: return []
    ps = [n[:GR]]
    if len(n) >= 2 * GR:
        mid = len(n) // 2
        ps.append(n[mid - GR // 2: mid - GR // 2 + GR])
    ps.append(n[-GR:])
    return [p for p in set(ps) if len(p) == GR]

tot_all = hit_all = 0
short_tot = short_miss = 0
for fn in BOOKS:
    lines = book_lines[fn]
    tot = hit = 0; miss_all = []
    s_tot = s_miss = 0; s_miss_ls = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or s == '未知' or PAGE.match(s) or HEADNOISE.match(s):
            continue
        for seg in re.split(r'[。！？!?；;]+', s):
            seg = seg.strip()
            n = norm(seg)
            if len(n) >= GR:
                tot += 1
                ps = probes(n)
                if any(p in NORM for p in ps):
                    hit += 1
                else:
                    miss_all.append((i + 1, seg))
            elif 4 <= len(n) < GR:
                s_tot += 1
                if n not in NORM:
                    s_miss += 1; s_miss_ls.append((i + 1, seg))
    tot_all += tot; hit_all += hit
    short_tot += s_tot; short_miss += s_miss
    print(f"  {fn}: 长句 {tot} 命中 {hit} 覆盖率 {100.0*hit/tot if tot else 0:.1f}% | 短句(4-11) {s_tot} 未命中 {s_miss}")
    for l, seg in miss_all[:20]:
        print(f"      [长句未命中 L{l}] {seg[:40]}")
    for l, seg in s_miss_ls[:20]:
        print(f"      [短句未命中 L{l}] {seg[:40]}")
print("=" * 72)
print(f"长句合计 {tot_all} 命中 {hit_all} 覆盖率 {100.0*hit_all/tot_all:.1f}%")
print(f"短句合计 {short_tot} 未命中 {short_miss}")
