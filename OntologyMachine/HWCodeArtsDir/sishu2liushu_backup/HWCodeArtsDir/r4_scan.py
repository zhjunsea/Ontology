# -*- coding: utf-8 -*-
import sys, io, re, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
BASE = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/design/四书全文整理"
COV = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r4_coverage.txt"
DBH = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r4_db_haystack.txt"

def read(fn):
    return open(os.path.join(BASE, fn), encoding='utf-8').read().splitlines()

hay = []
for p in (COV, DBH):
    hay += [l.strip() for l in open(p, encoding='utf-8').read().splitlines() if l.strip()]
HAY = "\n".join(hay)

def norm(s):
    return re.sub(r'[\s　]', '', s)

def hit(name):
    n = norm(name)
    return n and n in norm(HAY)

def sections_yuanhai():
    lines = read("渊海子平.txt")
    out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if s=="未知":
            for j in range(i+1,min(i+4,len(lines))):
                t=lines[j].strip()
                if t and t!="未知":
                    out.append((j+1,t)); break
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
        if s.startswith('通神论') or s.startswith('六亲论'):
            out.append((i+1, s))
        elif re.match(r'^[一二三四五六七八九十]+、', s) and len(s)<=14:
            out.append((i+1, re.sub(r'^[一二三四五六七八九十]+、','',s)))
    return out

def sections_shenfeng():
    lines=read("神峰通考.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if not s or s=="未知" or re.match(r'^\d+$', s): continue
        if len(s)<=20 and re.search(r'(说|类|论|诀|赋|歌|篇|式|法|格|辨|图|序)$', s) and not s.endswith('。'):
            out.append((i+1, s))
    return out

def sections_qiongtong():
    lines=read("穷通宝鉴.txt"); out=[]
    for i,ln in enumerate(lines):
        s=ln.strip()
        if re.match(r'^[0-9]+、', s) or '总论' in s or re.match(r'^论[木火土金水]$', s):
            out.append((i+1, s))
    return out

BOOKS = [
    ("渊海子平", sections_yuanhai()),
    ("三命通会", sections_sanming()),
    ("子平真诠", sections_ziping()),
    ("滴天髓",   sections_dts()),
    ("神峰通考", sections_shenfeng()),
    ("穷通宝鉴", sections_qiongtong()),
]

for bn, secs in BOOKS:
    seen=set(); uniq=[]
    for ln,t in secs:
        if t in seen: continue
        seen.add(t); uniq.append((ln,t))
    miss=[(ln,t) for ln,t in uniq if not hit(t)]
    print("="*72)
    print("### %s  章节 %d  0命中 %d" % (bn, len(uniq), len(miss)))
    for ln,t in miss:
        print("   MISS L%d  %s" % (ln, t[:60]))
