# -*- coding: utf-8 -*-
import sys, io, re, os, subprocess
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
MYSQL = r"D:/work/Ontology/mysql-26.7.0-winx64/bin/mysql.exe"
DB = "sizhubazidb"

def q(sql):
    p = subprocess.run([MYSQL, "-uroot", DB, "-N", "-e", sql], capture_output=True)
    return p.stdout.decode("utf-8", "replace")

NAME_COLS = [
    ("technique_catalog", "tech_name"),
    ("pattern_catalog", "pattern_name"),
    ("lady_fortune_catalog", "lady_name"),
    ("theory_clause", "subject"),
    ("auspice_clause", "subject"),
    ("dts_clause", "subject"),
    ("fu_clause", "subject"),
    ("deity_rule", "deity_name"),
    ("deity_rule", "deity_alias"),
    ("solar_term", "solar_term_name"),
    ("month_order", "month_order_name"),
    ("nayin", "nayin_name"),
    ("ganzhi_stem", "stem_name"),
    ("ganzhi_branch", "branch_name"),
    ("ten_god_map", "ten_god"),
    ("dts_clause", "clause_group"),
    ("theory_clause", "clause_group"),
    ("fu_clause", "clause_group"),
    ("auspice_clause", "clause_group"),
    ("ziping_clause", "subject"),
]

def norm(s):
    return re.sub(r'[\s　\u200b]', '', s or '')

ENTRY = set()
for t, c in NAME_COLS:
    for r in q("select `%s` from `%s`;" % (c, t)).splitlines():
        r = r.strip()
        if r:
            ENTRY.add(norm(r))
# OWL concept labels (rdfs:label xml:lang="zh") + SKOS prefLabel
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
import glob
OWL_RE = re.compile(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', re.S)
TTL_RE = re.compile(r'skos:prefLabel\s+"(.*?)"@zh', re.S)
for fp in glob.glob(os.path.join(OWLDIR, "*.owl")):
    txt = open(fp, encoding='utf-8').read()
    for m in OWL_RE.findall(txt):
        ENTRY.add(norm(m))
for fp in glob.glob(os.path.join(OWLDIR, "*.ttl")):
    txt = open(fp, encoding='utf-8').read()
    for m in TTL_RE.findall(txt):
        ENTRY.add(norm(m))
# day-hour titles synthesized from day_hour_clause
for r in q("select day_pillar,hour_pillar from day_hour_clause;").splitlines():
    p = r.split("\t")
    if len(p) >= 2:
        ENTRY.add(norm(p[0]) + "日" + norm(p[1]) + "时断")

def read(fn):
    return open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()

ENUM = re.compile(r'^([0-9]+、|[一二三四五六七八九十]+、|卷[一二三四五六七八九十]+)')
PREFIX = re.compile(r'^(论|释|又|附|总论|并|新|增|重)')
SUFFIX = re.compile(r'(格|篇|说|类|赋|歌|诀|图|法|例|式|论|章|断法|总诀|奥诀|要诀|秘诀|口诀|妙诀|注解)$')

def cands(name):
    n = norm(name)
    ks = {n}
    a = PREFIX.sub('', n); ks.add(a)
    ks.add(SUFFIX.sub('', a))
    ks.add(SUFFIX.sub('', n))
    # strip trailing 附XXX
    ks.add(re.split(r'附', n)[0])
    return {k for k in ks if len(k) >= 2}

def covered(name):
    ks = cands(name)
    for e in ENTRY:
        for k in ks:
            if k == e or k in e or e in k:
                return True
    return False

def sections_yuanhai():
    lines = read("渊海子平.txt"); out=[]
    for i,ln in enumerate(lines):
        if ln.strip()=="未知":
            for j in range(i+1,min(i+4,len(lines))):
                t=lines[j].strip()
                if t and t!="未知": out.append((j+1,t)); break
    return out
def sections_sanming():
    lines=read("三命通会.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if not s or s=="未知": continue
        if re.match(r'^卷[一二三四五六七八九十]', s) or s.startswith('○'):
            out.append((i+1, s.lstrip('○')))
    return out
def sections_ziping():
    lines=read("子平真诠.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if re.match(r'^[一二三四五六七八九十]+、', s):
            out.append((i+1, re.sub(r'^[一二三四五六七八九十]+、','',s)))
    return out
def sections_dts():
    lines=read("滴天髓.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if s.startswith('通神论') or s.startswith('六亲论'): out.append((i+1,s))
        elif re.match(r'^[一二三四五六七八九十]+、', s) and len(s)<=14:
            out.append((i+1, re.sub(r'^[一二三四五六七八九十]+、','',s)))
    return out
def sections_shenfeng():
    lines=read("神峰通考.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if not s or s=="未知" or re.match(r'^\d+$', s): continue
        if len(s)<=20 and re.search(r'(说|类|论|诀|赋|歌|篇|式|法|格|辨|图|序)$', s) and not s.endswith('。'):
            out.append((i+1,s))
    return out
def sections_qiongtong():
    lines=read("穷通宝鉴.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if re.match(r'^[0-9]+、', s) or '总论' in s or re.match(r'^论[木火土金水]$', s): out.append((i+1,s))
    return out

BOOKS=[("渊海子平",sections_yuanhai()),("三命通会",sections_sanming()),
       ("子平真诠",sections_ziping()),("滴天髓",sections_dts()),
       ("神峰通考",sections_shenfeng()),("穷通宝鉴",sections_qiongtong())]

print("ENTRY names:", len(ENTRY))
tot_miss=0
for bn,secs in BOOKS:
    seen=set(); uniq=[]
    for ln,t in secs:
        if t in seen: continue
        seen.add(t); uniq.append((ln,t))
    miss=[(ln,t) for ln,t in uniq if not covered(t)]
    tot_miss+=len(miss)
    print("="*72)
    print("### %s  章节 %d  未精确命中 %d" % (bn,len(uniq),len(miss)))
    for ln,t in miss:
        print("   EXACT-MISS L%d  %s" % (ln,t[:60]))
print("TOTAL EXACT-MISS:", tot_miss)
