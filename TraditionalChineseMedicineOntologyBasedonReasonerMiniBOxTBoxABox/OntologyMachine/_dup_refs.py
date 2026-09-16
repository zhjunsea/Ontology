# -*- coding: utf-8 -*-
import re, os, glob

base = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox'
ont_root = os.path.join(base, 'ontology')
java_root = os.path.join(base, 'OntologyMachine', 'OntologyFramework', 'src')

targets = ['Dahuanggansuitangzheng', 'Houpoqiwutangzheng', 'Yuebijiazhutangzheng',
           'Houpoushengjiangbanxiagancaorenshentangzheng', 'Houposhengjiangbanxiagancaorenshentangzheng']

print('===== OWL 中对方证类名的引用（排除定义本身）=====')
for fp in sorted(glob.glob(os.path.join(ont_root, '**', '*.owl'), recursive=True)):
    if '_rename_backup' in fp:
        continue
    t = open(fp, encoding='utf-8').read()
    for tg in targets:
        # 找所有 #tg 出现（含定义行）
        hits = [m.start() for m in re.finditer(r'#' + re.escape(tg) + r'(?=["<>])', t)]
        if hits:
            print(f'  {os.path.relpath(fp, ont_root)}: #{tg} x{len(hits)}')
            for h in hits:
                ctx = t[max(0, h-60):h+len(tg)+5].replace('\n', ' ')
                print(f'      ...{ctx}')

print('\n===== Java 中对方证/方剂的引用 =====')
for fp in glob.glob(os.path.join(java_root, '**', '*.java'), recursive=True):
    t = open(fp, encoding='utf-8').read()
    for tg in targets:
        c = len(re.findall(r'\b' + re.escape(tg) + r'\b', t))
        if c:
            print(f'  {os.path.relpath(fp, java_root)}: {tg} x{c}')
            for m in re.finditer(r'.{0,50}\b' + re.escape(tg) + r'\b.{0,50}', t):
                print(f'      {m.group(0).strip()}')
