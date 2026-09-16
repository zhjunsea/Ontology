# -*- coding: utf-8 -*-
import json, re

rows = json.load(open('_b_missing.json', encoding='utf-8'))
done = {'Fulingguizhibaizhugancaotang', 'Gualouxiebaibanxiatang', 'Renshentang'}
rem = [r for r in rows if r['chufang_iri'] not in done]

with open('_missing81.txt', 'w', encoding='utf-8') as f:
    for i, r in enumerate(rem, 1):
        f.write(f"{i}\t{r['file']}\t{r['chufang_iri']}\t{r['base_label']}\n")
print('导出', len(rem), '个')

p = '../ontology/fangzheng/shaoyin_taiyin.owl'
text = open(p, encoding='utf-8').read()
for key in ['Lingganwuweijiajiangxinbanxingdahuangtang']:
    print('\n===', key, '===')
    for m in re.finditer(re.escape(key), text):
        s = max(0, m.start()-400)
        print(text[s:m.end()+150].replace('\n', ' '))
        print('---')
