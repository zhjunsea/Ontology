# -*- coding: utf-8 -*-
import re, os

ONT = '../ontology/fangzheng'
targets = {
    'zabing.owl': ['Dahuangzhechongwanzheng', 'Xiayuxuetangzheng', 'Zhishishaoyaosanzheng'],
    'duli.owl': ['Dahuanggansuitangzheng'],
    'shaoyang_yangming.owl': ['Chaihuguizhitangzheng', 'Chaihuqubanxiajiagualoutangzheng',
                              'Sinisanzheng', 'Dachengqitangzheng', 'Xiexintangzheng',
                              'Xiaochaihutangzheng', 'Xiaochengqitangzheng'],
}

def top_blocks(text):
    starts = [(m.start(), m.group(1)) for m in
              re.finditer(r'^\s*<owl:Class\s+rdf:about="#([^"]+)">', text, re.M)]
    res = {}
    for i, (pos, name) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(text)
        res[name] = text[pos:end]
    return res

for fname, names in targets.items():
    p = os.path.join(ONT, fname)
    text = open(p, encoding='utf-8').read()
    blocks = top_blocks(text)
    for n in names:
        print('=' * 70)
        print(f'### {fname} :: {n}')
        b = blocks.get(n)
        if not b:
            print('  [NOT FOUND]')
            continue
        # 提取 label
        lm = re.search(r'<rdfs:label[^>]*>([^<]+)</rdfs:label>', b)
        print('label:', lm.group(1) if lm else '?')
        # 提取 you_zhengzhuang / you_maixiang / you_chufang / requiredCount
        for prop in ['you_zhengzhuang', 'you_maixiang', 'you_chufang', 'requiredCount', 'clinicalPriority']:
            vals = re.findall(r'#' + prop + r'"[^>]*?(?:rdf:resource="#([^"]+)"|>([^<]+)<)', b)
            flat = [a or c for a, c in vals]
            if flat:
                print(f'  {prop}: {flat}')
