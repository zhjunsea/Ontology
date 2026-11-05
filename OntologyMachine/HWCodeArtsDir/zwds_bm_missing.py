# -*- coding: utf-8 -*-
import re, io
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\ontology\database\data.sql"
lines = io.open(p, encoding="utf-8").read().splitlines()
all_branches = ["Zi","Chou","Yin","Mao","Chen","Si","Wu","Wei","Shen","You","Xu","Hai"]
present = {}
in_bm = False
for l in lines:
    if l.startswith("INSERT INTO `brightness_map`"):
        in_bm = True; continue
    if not in_bm: continue
    s = l.strip()
    m = re.match(r"\('BM_[A-Za-z]+_[A-Za-z]+','([A-Za-z]+)','Branch_([A-Za-z]+)'", s)
    if m:
        present.setdefault(m.group(1), set()).add(m.group(2))
    if s.endswith(";"): break
for star in ["LuCun","QingYang","TuoLuo"]:
    got = present.get(star, set())
    miss = [b for b in all_branches if b not in got]
    print(star, "has", sorted(got, key=all_branches.index), "| MISSING:", miss)
