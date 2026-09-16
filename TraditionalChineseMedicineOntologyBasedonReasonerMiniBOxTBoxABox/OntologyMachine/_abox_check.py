# -*- coding: utf-8 -*-
import io, re
abox = io.open('../ontology/tcm-fangji-abox.owl', encoding='utf-8').read()
missing = [l.split('\t') for l in io.open('_missing81.txt', encoding='utf-8').read().strip().split('\n')]
present = 0
absent = []
for m in missing:
    iri = m[2]
    if ('#' + iri + '"') in abox or ('#' + iri + "'") in abox:
        present += 1
    else:
        absent.append(iri)
print('abox 中已存在个体:', present)
print('abox 中缺失个体:', len(absent))
for a in absent:
    print('  ABSENT', a)
# 统计 abox 总个体数
ids = re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)"', abox)
print('abox NamedIndividual 总数:', len(ids))
