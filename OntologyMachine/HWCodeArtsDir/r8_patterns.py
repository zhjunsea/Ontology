# -*- coding: utf-8 -*-
import sys, io, re, subprocess
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/六书全文整理"
MYSQL = r"D:/work/Ontology/mysql-26.7.0-winx64/bin/mysql.exe"

def q(sql):
    p = subprocess.run([MYSQL, "-uroot", "sizhubazidb", "-N", "-e", sql], capture_output=True)
    return p.stdout.decode("utf-8", "replace")

def norm(s):
    return re.sub(r'[\s　（）()]', '', s or '')

CAT = set()
for r in q("select pattern_name from pattern_catalog;").splitlines():
    if r.strip(): CAT.add(norm(r))
# OWL pattern labels
OWLDIR = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology"
import glob
OWL_RE = re.compile(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', re.S)
for fp in glob.glob(OWLDIR + "/*.owl"):
    for m in OWL_RE.findall(open(fp, encoding='utf-8').read()):
        CAT.add(norm(m))

def scan_titles(fn):
    lines = open(BASE + "/" + fn, encoding='utf-8').read().splitlines()
    out = []
    for i, ln in enumerate(lines):
        s = ln.strip()
        # a格 title: short, ends with 格 (possibly 格(..)), no 。，,
        if 1 <= len(s) <= 12 and s.endswith('格') and not re.search(r'[。，,；：]', s):
            out.append((i+1, s))
        elif 1 <= len(s) <= 16 and re.match(r'^[\u4e00-\u9fff]+格（.*）$', s):
            out.append((i+1, s))
    return out

def covered(name):
    n = norm(name)
    n2 = re.sub(r'格$', '', n)
    for e in CAT:
        if e == n or e == n2 or n2 in e or e in n2:
            return True
    return False

for fn, book in [("渊海子平.txt", "渊海子平"), ("神峰通考.txt", "神峰通考"), ("三命通会.txt", "三命通会")]:
    ts = scan_titles(fn)
    miss = [(l, t) for l, t in ts if not covered(t)]
    print("="*60)
    print("### %s 具名格候选 %d 未覆盖 %d" % (book, len(ts), len(miss)))
    for l, t in miss:
        print("   GAP L%d  %s" % (l, t))
