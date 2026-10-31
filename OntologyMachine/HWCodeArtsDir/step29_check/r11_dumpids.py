# -*- coding: utf-8 -*-
import re, io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()

def top_tuples(values_body):
    """从 VALUES 体解析出每个顶层 (...) 的原始文本"""
    res = []; depth = 0; instr = False; i = 0; start = None; n = len(values_body)
    while i < n:
        c = values_body[i]
        if instr:
            if c == chr(92): i += 2; continue
            if c == "'":
                if i + 1 < n and values_body[i + 1] == "'": i += 2; continue
                instr = False
            i += 1; continue
        if c == "'": instr = True; i += 1; continue
        if c == '(':
            if depth == 0: start = i + 1
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0: res.append(values_body[start:i])
        i += 1
    return res

def first_str(t):
    m = re.search(r"'((?:[^']|'')*)'", t)
    return m.group(1).replace("''", "'") if m else None

TABLES = ['classic_text', 'ziping_clause', 'fu_clause', 'dts_clause', 'tiaohou_clause',
          'theory_clause', 'auspice_clause', 'day_hour_clause', 'pattern_catalog',
          'technique_catalog', 'deity_rule']
for tbl in TABLES:
    m = re.search(r"INSERT INTO\s+`?" + tbl + r"`?\s*\(([^)]*)\)\s*VALUES(.*?);\s*(?:\n|$)", sql, re.S)
    if not m:
        print(f"{tbl}: NOT FOUND"); continue
    tup = top_tuples(m.group(2))
    ids = [first_str(t) for t in tup]
    print(f"=== {tbl}: {len(tup)} rows ===")
    # 连号检查：提取尾部数字
    nums = []
    for x in ids:
        mm = re.search(r'(\d+)\s*$', x or '')
        if mm: nums.append(int(mm.group(1)))
    if nums:
        nums.sort()
        gaps = [(nums[i], nums[i+1]) for i in range(len(nums)-1) if nums[i+1] != nums[i]+1]
        print(f"  id数: {len(nums)} 范围[{nums[0]}..{nums[-1]}] 断号: {gaps if gaps else '无'}")
    print(f"  前3: {ids[:3]}   后3: {ids[-3:]}")
