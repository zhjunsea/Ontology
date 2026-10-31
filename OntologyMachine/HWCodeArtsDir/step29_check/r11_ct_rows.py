# -*- coding: utf-8 -*-
import re, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()

m = re.search(r"INSERT INTO\s+`?classic_text`?\s*\(([^)]*)\)\s*VALUES", sql, re.S)
print("classic_text cols:", m.group(1).strip())

# 提取 classic_text 段所有顶层 tuple 的原始文本
body_start = m.end()
# 找该 INSERT 的结束 ';'
rest = sql[body_start:]
# 顶层 tuple 解析
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
        elif c == ';' and depth == 0:
            break
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
        if c == ',':
            out.append("".join(buf).strip()); buf = []; i += 1; continue
        buf.append(c); i += 1
    out.append("".join(buf).strip())
    return out

rows = top_tuples(rest)
d = {}
for t in rows:
    f = fields(t)
    d[f[0]] = f

def show(ids):
    for x in ids:
        f = d.get(x)
        if f: print(f"  {x}: {f[1:]}")

print("--- CT_YH 缺号邻条 ---")
for n in [5, 21, 22, 148, 149, 152, 153, 192, 220, 281, 296]:
    print(f"  # 缺 CT_YH_{n:03d}，邻条：")
    show([f"CT_YH_{n-1:03d}", f"CT_YH_{n+1:03d}"])
print("--- CT_SF 缺号邻条 ---")
show(["CT_SF_072", "CT_SF_074"])
print("--- CT_YH 前6条 ---")
show([f"CT_YH_{i:03d}" for i in range(1, 7)])
