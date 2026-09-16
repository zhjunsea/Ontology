# -*- coding: utf-8 -*-
import csv, io, sys
csv.field_size_limit(10**7)
rows = list(csv.reader(io.open('../伤寒杂病论及金匮要略方剂.csv', encoding='utf-8-sig')))
hdr = rows[0]
print('列:', hdr)
name_idx = None
comp_idx = None
for i, h in enumerate(hdr):
    if h.strip() == '方剂':
        name_idx = i
    if h.strip() == '组成的药方':
        comp_idx = i
print('方剂列:', name_idx, '组成列:', comp_idx)
data = {}
for r in rows[1:]:
    if len(r) > max(name_idx, comp_idx):
        n = r[name_idx].strip()
        if n:
            data[n] = r[comp_idx].strip()
print('CSV方剂数:', len(data))
missing = [l.split('\t') for l in io.open('_missing81.txt', encoding='utf-8').read().strip().split('\n')]
mn = [m[2] for m in missing]
cov = [n for n in mn if n in data]
print('81中CSV覆盖:', len(cov))
for n in mn:
    mark = 'OK ' if n in data else 'MISS'
    print(mark, n, '=>', data.get(n, ''))
