# -*- coding: utf-8 -*-
import re, io
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\ontology\database\data.sql"
with io.open(p, encoding="utf-8") as f:
    lines = [l.rstrip("\n") for l in f]

# collect brightness_map rows
from collections import Counter, OrderedDict
stars = Counter()
branches = Counter()
in_bm = False
for l in lines:
    if l.startswith("INSERT INTO `brightness_map`"):
        in_bm = True; continue
    if in_bm:
        s = l.strip()
        if s.endswith(";"):
            # process then stop
            pass
        m = re.match(r"\('BM_([A-Za-z]+)_([A-Za-z]+)','([A-Za-z]+)','Branch_([A-Za-z]+)'", s)
        if m:
            stars[m.group(3)] += 1
            branches[m.group(4)] += 1
        if s.endswith(";"):
            break

expected_stars_local = ["ZiWei","TianJi","TaiYang","WuQu","TianTong","LianZhen","TianFu","TaiYin",
"TanLang","JuMen","TianXiang","TianLiang","QiSha","PoJun","WenChang","WenQu","LuCun","QingYang",
"TuoLuo","HuoXing","LingXing"]
print("star_local counts in brightness_map:")
for s in expected_stars_local:
    print("  %-12s %d" % (s, stars.get(s,0)))
print("total rows:", sum(stars.values()))
print("distinct stars:", len(stars))
print("branches coverage:", dict(branches))
