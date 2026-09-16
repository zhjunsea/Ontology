# -*- coding: utf-8 -*-
import re, os, glob

ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'

# 搜索所有含 houpoushengjiang / houposhengjiang 的类
pat = re.compile(r'houpou?shengjiang', re.I)
for fp in sorted(glob.glob(os.path.join(ont_root, 'fangzheng', '*.owl'))):
    t = open(fp, encoding='utf-8').read()
    cls_pos = [(m.start(), m.group(1)) for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">', t)]
    for i, (pos, cls) in enumerate(cls_pos):
        if not pat.search(cls):
            continue
        end = cls_pos[i+1][0] if i+1 < len(cls_pos) else len(t)
        body = t[pos:end]
        print('=' * 100)
        print(f'文件: {os.path.basename(fp)}   类名: #{cls}')
        print('-' * 100)
        print(body.rstrip())
        print()
