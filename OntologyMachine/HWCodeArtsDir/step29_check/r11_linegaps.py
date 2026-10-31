# -*- coding: utf-8 -*-
import re, io, sys
from collections import defaultdict
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()

# classic_text 顶层 tuple 解析
m = re.search(r"INSERT INTO\s+`?classic_text`?\s*\(([^)]*)\)\s*VALUES", sql, re.S)
rest = sql[m.end():]
def top_tuples(s):
    res = []; depth = 0; instr = False; i = 0; start = None; n = len(s)
    while i < n:
        c = s[i]
        if instr:
            if c == chr(92): i += 2; continue
            if c == "'":
                if i+1 < n and s[i+1] == "'": i += 2; continue
                instr = False
            i += 1; continue
        if c == "'": instr = True; i += 1; continue
        if c == '(':
            if depth == 0: start = i+1
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0: res.append(s[start:i])
        elif c == ';' and depth == 0: break
        i += 1
    return res

def fields(t):
    out = []; i = 0; n = len(t); instr = False; buf = []
    while i < n:
        c = t[i]
        if instr:
            if c == chr(92): buf.append(t[i+1]); i += 2; continue
            if c == "'":
                if i+1 < n and t[i+1] == "'": buf.append("'"); i += 2; continue
                instr = False; i += 1; continue
            buf.append(c); i += 1; continue
        if c == "'": instr = True; continue
        if c == ',': out.append("".join(buf).strip()); buf = []; i += 1; continue
        buf.append(c); i += 1
    out.append("".join(buf).strip())
    return out

rows = [fields(t) for t in top_tuples(rest)]
bybook = defaultdict(list)
for f in rows:
    sn = f[4]
    mm = re.search(r'·L(\d+)$', sn)
    if mm:
        bybook[f[1]].append((int(mm.group(1)), f[0], f[2]))

for book, lst in bybook.items():
    lst.sort()
    print(f"=== {book} classic_text {len(lst)}条 (按源行号) ===")
    gaps = []
    for i in range(len(lst)-1):
        a, b = lst[i][0], lst[i+1][0]
        if b - a > 1:
            gaps.append((a, b, b-a-1, lst[i][1], lst[i+1][1]))
    gaps.sort(key=lambda x: -x[2])
    print(f"  源行号范围 [{lst[0][0]}..{lst[-1][0]}]，共 {len(gaps)} 处跳变")
    for g in gaps[:15]:
        print(f"    L{g[0]}->L{g[1]} 空隙{g[2]}行  ({g[3]} -> {g[4]})")
