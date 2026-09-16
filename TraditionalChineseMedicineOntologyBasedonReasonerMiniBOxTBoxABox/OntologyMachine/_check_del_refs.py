# -*- coding: utf-8 -*-
import re, os, glob
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'

# 检查被删类是否被其他类作为父类/等价类引用（除定义自身外）
targets = ['Dahuanggansuitangzheng', 'Houpoqiwutangzheng', 'Yuebijiazhutangzheng',
           'Houpoushengjiangbanxiagancaorenshentangzheng']
for fp in glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True):
    if '_rename_backup' in fp:
        continue
    t = open(fp, encoding='utf-8').read()
    for tg in targets:
        # 找 rdf:resource="#tg" （即被引用，而非定义）
        for m in re.finditer(r'rdf:resource="#' + re.escape(tg) + r'"', t):
            ctx = t[max(0, m.start()-80):m.start()+len(tg)+20].replace('\n', ' ')
            print(f'{os.path.relpath(fp, ont_root)}: {ctx}')
print('--- 检查完毕 ---')
