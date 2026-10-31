# -*- coding: utf-8 -*-
import sys, io, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
OLD = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r8_scan.out"
NEW = r"D:/work/Ontology/OntologyMachine/HWCodeArtsDir/_r8_scan_exact.out"

def parse(p, tag):
    books={}; cur=None
    for ln in open(p, encoding='utf-8').read().splitlines():
        m=re.match(r'### (\S+)\s+章节', ln)
        if m: cur=m.group(1); books[cur]=[]; continue
        if tag in ln and cur:
            t=re.sub(r'^\s*'+re.escape(tag)+r'\s*L\d+\s+','',ln).strip()
            books[cur].append(t)
    return books

old=parse(OLD,"MISS"); new=parse(NEW,"EXACT-MISS")
for b in new:
    o=set(old.get(b,[])); n=new[b]
    nn=[t for t in n if t not in o]
    if nn:
        print("### %s  NEW-REVEALED %d" % (b,len(nn)))
        for t in nn: print("   ", t[:70])
