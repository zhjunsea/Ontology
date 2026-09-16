import json
from collections import Counter

rows = json.load(open('_b_missing.json', encoding='utf-8'))
print('total rows:', len(rows))
done = {'Fulingguizhibaizhugancaotang', 'Gualouxiebaibanxiatang', 'Renshentang'}
rem = [r for r in rows if r['chufang_iri'] not in done]
print('剩余待补建:', len(rem))
print(dict(Counter(r['file'] for r in rem)))
seen = {}
for r in rem:
    seen.setdefault(r['chufang_iri'], r)
print('去重后唯一方剂:', len(seen))
