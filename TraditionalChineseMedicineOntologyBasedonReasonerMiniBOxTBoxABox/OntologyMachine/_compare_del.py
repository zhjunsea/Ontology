# -*- coding: utf-8 -*-
import re, os, difflib

cur_p = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng\shaoyang_yangming.owl'
bak_p = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\_dup_delete_backup\shaoyang_yangming.owl'

cur = open(cur_p, encoding='utf-8').read()
bak = open(bak_p, encoding='utf-8').read()

print('当前文件大小:', len(cur), ' 删除前备份大小:', len(bak))

def houpo_classes(t):
    return re.findall(r'<owl:Class rdf:about="#([^"]*[Hh]oupo[^"]*)">', t)

print('\n删除前备份中的 houpo 类:', houpo_classes(bak))
print('当前文件中的 houpo 类:', houpo_classes(cur))

print('\n===== 差异（备份 -> 当前）=====')
diff = list(difflib.unified_diff(bak.splitlines(), cur.splitlines(), 'BAK', 'CUR', lineterm='', n=0))
print('差异行数:', len(diff))
for line in diff:
    if line.startswith('+') or line.startswith('-'):
        print(line[:150])
