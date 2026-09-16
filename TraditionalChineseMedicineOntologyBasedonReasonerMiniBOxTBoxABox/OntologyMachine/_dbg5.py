# -*- coding: utf-8 -*-
import re, os
ont_root = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology'
fp = os.path.join(ont_root, 'fangzheng', 'shaoyang_yangming.owl')
t = open(fp, encoding='utf-8').read()
print('===== 所有含 houpou/houpo 的类定义 =====')
for m in re.finditer(r'<owl:Class rdf:about="#([^"]*[Hh]oupo[^"]*)">', t):
    print('  ', m.group(1), 'at', m.start())
print('\n===== 所有 houpou/houpo 出现位置 =====')
for m in re.finditer(r'[Hh]oupo\w*', t):
    print(f'  {m.group(0)} at {m.start()}')
print('\n===== 备份文件对比 =====')
bak = os.path.join(ont_root, '..', 'OntologyMachine', '_dup_delete_backup', 'shaoyang_yangming.owl')
bak = os.path.normpath(bak)
bt = open(bak, encoding='utf-8').read()
print('备份中 houpou/houpo 类:')
for m in re.finditer(r'<owl:Class rdf:about="#([^"]*[Hh]oupo[^"]*)">', bt):
    print('  ', m.group(1))
