# -*- coding: utf-8 -*-
import re, io, sys

java_path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\src\main\java\com\ocean\ontologyframework\ziweidoushu\ZiWeiDouShuOntologyJobWorker.java"
with io.open(java_path, encoding="utf-8") as f:
    lines = f.readlines()
lines = [l.rstrip("\n") for l in lines]

fields = ["ONTO_BRIGHTNESS","ONTO_SIHUA","ONTO_ZIWEI","ONTO_LUCUN","ONTO_LIUYANG","ONTO_LIUTUO",
"ONTO_DEITY_12","ONTO_WUHU","ONTO_TONGYUAN","ONTO_STAR_CHARACTER","ONTO_STAR_XINGXING",
"ONTO_STAR_QA_CHARACTER","ONTO_VERIFY_STAR_TRAITS","ONTO_VERIFY_BUREAU_TRAITS",
"ONTO_TAISUI_XIAOXIAN","ONTO_PALACE_ZHI_GE","ONTO_PALACE_ZHI_PO","ONTO_PALACE_FUGUI",
"ONTO_PALACE_PINJIAN","ONTO_IDLE_VERSE","ONTO_IDLE_STAR","ONTO_BRIGHTNESS_VARIANTS",
"ONTO_LIMIT_TABOO","ONTO_SIHUA_QA","ONTO_STAR_BLOCK_ALL","ONTO_CLASSIC_CORPUS",
"ONTO_PATTERN_VERSE"]

# self-check line ranges (1-based inclusive)
def in_selfcheck(n):
    return (824 <= n <= 889) or (895 <= n <= 1030)

print("FIELD | decl | assign | selfcheck | OTHER(biz refs) -> lines")
for fld in fields:
    pat = re.compile(r"\b" + re.escape(fld) + r"\b")
    decl=[]; assign=[]; sc=[]; biz=[]
    for i,l in enumerate(lines, start=1):
        if not pat.search(l):
            continue
        if "private static volatile" in l and fld in l:
            decl.append(i); continue
        # assignment: FIELD = (not ==, not !=)
        if re.search(r"\b"+re.escape(fld)+r"\b\s*=(?!=)", l):
            assign.append(i); continue
        if in_selfcheck(i):
            sc.append(i); continue
        biz.append(i)
    print("%-26s d=%d a=%d sc=%d biz=%d  %s" % (fld, len(decl),len(assign),len(sc),len(biz), biz))
