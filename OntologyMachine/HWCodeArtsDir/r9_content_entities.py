# -*- coding: utf-8 -*-
# 第9轮：六典籍【正文实体级】抽取 -> 对照全库（DB 名录 + OWL label + SKOS pref/altLabel）
import sys, io, re, os, subprocess, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/四书全文整理"
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
MYSQL = r"D:/work/Ontology/mysql-26.7.0-winx64/bin/mysql.exe"
DB = "sizhubazidb"

def q(sql):
    p = subprocess.run([MYSQL, "-uroot", DB, "-N", "-e", sql], capture_output=True)
    return p.stdout.decode("utf-8", "replace")

# ---- 全库命名实体集 ----
NAME_COLS = [
    ("technique_catalog","tech_name"),("pattern_catalog","pattern_name"),
    ("lady_fortune_catalog","lady_name"),("theory_clause","subject"),
    ("auspice_clause","subject"),("dts_clause","subject"),("fu_clause","subject"),
    ("deity_rule","deity_name"),("deity_rule","deity_alias"),
    ("solar_term","solar_term_name"),("month_order","month_order_name"),
    ("nayin","nayin_name"),("ganzhi_stem","stem_name"),("ganzhi_branch","branch_name"),
    ("ten_god_map","ten_god"),("dts_clause","clause_group"),("theory_clause","clause_group"),
    ("fu_clause","clause_group"),("auspice_clause","clause_group"),
]
ENTRY = set()
for t, c in NAME_COLS:
    for r in q("select `%s` from `%s`;" % (c, t)).splitlines():
        r = r.strip()
        if r: ENTRY.add(r)

OWLRE = re.compile(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', re.S)
TTLPREF = re.compile(r'skos:prefLabel\s+"(.*?)"@zh', re.S)
TTLALT = re.compile(r'skos:altLabel\s+"(.*?)"@zh', re.S)
for fp in glob.glob(OWLDIR + "/*.owl"):
    for m in OWLRE.findall(open(fp, encoding='utf-8').read()): ENTRY.add(m)
for fp in glob.glob(OWLDIR + "/*.ttl"):
    txt = open(fp, encoding='utf-8').read()
    for m in TTLPREF.findall(txt): ENTRY.add(m)
    for m in TTLALT.findall(txt): ENTRY.add(m)

def norm(s):
    s = re.sub(r'[\s　\u200b\u3000]', '', s or '')
    s = s.replace('（','(').replace('）',')')
    s = s.replace('殺','杀').replace('煞','杀')
    return s

EN = set(norm(e) for e in ENTRY if e)

def covered(term):
    n = norm(term)
    if len(n) < 2: return False
    if n in EN: return True
    # 允许后缀剥离（格/煞/贵人 等）
    a = re.sub(r'(格|篇|煞|杀|贵人|神|星|马|刃|禄|德|空亡|空)$', '', n)
    if a and a in EN: return True
    for e in EN:
        if n == e: return True
    return False

# ---- 书名 -> 文 ----
BOOKS = ["渊海子平.txt","三命通会.txt","子平真诠.txt","滴天髓.txt","神峰通考.txt","穷通宝鉴.txt"]

# 正文实体候选：限定强命名后缀
PATTERNS = [
    (re.compile(r'[\u4e00-\u9fff]{2,6}贵人'), '贵人'),
    (re.compile(r'[\u4e00-\u9fff]{2,5}(煞|杀)(?![手气])'), '煞'),
    (re.compile(r'[\u4e00-\u9fff]{2,4}空亡'), '空亡'),
    (re.compile(r'[\u4e00-\u9fff]{2,4}(马|刃|禄|德|星)$'), '神煞类'),
]

seen_global = {}
for fn in BOOKS:
    lines = open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()
    cands = {}
    for i, ln in enumerate(lines):
        s = ln.strip()
        if not s or s == '未知': continue
        for rx, tag in PATTERNS:
            for m in rx.findall(s):
                pass
            for m in rx.finditer(s):
                t = m.group(0)
                cands.setdefault(t, (i+1, tag))
    miss = [(t, v) for t, v in cands.items() if not covered(t)]
    print("="*72)
    print("### %s  正文候选实体 %d  未覆盖 %d" % (fn, len(cands), len(miss)))
    for t, (ln, tag) in sorted(miss, key=lambda x: x[1][0]):
        print("   GAP L%-6d [%s] %s" % (ln, tag, t))
