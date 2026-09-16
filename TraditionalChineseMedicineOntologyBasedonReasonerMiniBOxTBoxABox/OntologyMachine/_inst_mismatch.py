# -*- coding: utf-8 -*-
import re, io
s = io.open('../ontology/tcm-zhengzhuang-abox.owl', encoding='utf-8').read()
# 提取每个实例的 id 与 type
pairs = re.findall(r'rdf:about="#([^"]+)_instance"><rdf:type rdf:resource="#([^"]+)"', s)
print('实例总数:', len(pairs))
bad = [(i, t) for i, t in pairs if i != t]
print('实例ID与类名不一致:', len(bad))
for i, t in bad:
    print('  ID=%s  type=%s' % (i, t))
