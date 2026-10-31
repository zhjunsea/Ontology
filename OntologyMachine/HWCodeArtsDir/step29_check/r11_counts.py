# -*- coding: utf-8 -*-
import re, io, sys
from collections import Counter
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()

def count_tuples(stmt):
    n = 0; depth = 0; instr = False; i = 0
    while i < len(stmt):
        c = stmt[i]
        if instr:
            if c == chr(92):
                i += 2; continue
            if c == "'":
                if i + 1 < len(stmt) and stmt[i + 1] == "'":
                    i += 2; continue
                instr = False
            i += 1; continue
        if c == "'":
            instr = True; i += 1; continue
        if c == '(':
            depth += 1
            if depth == 1: n += 1
        elif c == ')':
            depth -= 1
        i += 1
    return n

for tbl in ['classic_text', 'ziping_clause', 'fu_clause', 'dts_clause', 'tiaohou_clause',
            'theory_clause', 'auspice_clause', 'day_hour_clause']:
    m = re.search(r"INSERT INTO\s+`?" + tbl + r"`?\s*\([^)]*\)\s*VALUES(.*?);\s*\n", sql, re.S)
    if m:
        print(f"{tbl}: tuples={count_tuples(m.group(1))}")
    else:
        print(f"{tbl}: NOT FOUND")

print("--- classic_text id 前缀分布 ---")
ids = re.findall(r"\('(CT_[^']*)'", sql)
print("total CT_ ids:", len(ids))
pref = Counter(x.split('_')[1] for x in ids if '_' in x)
print(dict(pref))

print("--- ziping_clause id/行样例 ---")
for m in re.finditer(r"\('([^']*)'", sql):
    pass
# 抓 ziping_clause 段
mz = re.search(r"INSERT INTO\s+`?ziping_clause`?\s*\(([^)]*)\)\s*VALUES(.*?);\s*\n", sql, re.S)
if mz:
    print("cols:", mz.group(1))
    vals = re.findall(r"\('([^']*)'", mz.group(2))
    print("ziping ids:", vals[:60])
