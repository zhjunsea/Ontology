# -*- coding: utf-8 -*-
import re, io, sys
from collections import defaultdict
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
SQL = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/database/data.sql"
sql = open(SQL, encoding='utf-8').read()

ids = re.findall(r"\('(CT_[^']*)'", sql)
bybook = defaultdict(list)
for x in ids:
    parts = x.split('_')
    book = parts[1]
    num = parts[2] if len(parts) > 2 else None
    bybook[book].append((num, x))

for book, lst in bybook.items():
    nums = sorted(int(n) for n, _ in lst if n and n.isdigit())
    nonnum = [x for n, x in lst if not (n and n.isdigit())]
    print(f"=== CT_{book}: {len(lst)} rows, 编号范围 [{nums[0]}..{nums[-1]}] ===")
    gaps = [(nums[i], nums[i+1]) for i in range(len(nums)-1) if nums[i+1] != nums[i]+1]
    print(f"  数字编号数={len(nums)} 断号={gaps if gaps else '无'} 非数字id={nonnum}")
