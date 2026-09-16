# -*- coding: utf-8 -*-
import re

p = '../ontology/tcm-fangji-abox.owl'
text = open(p, encoding='utf-8').read()
m = re.search(r'<owl:NamedIndividual\s+rdf:about="#Guizhitang">(.*?)</owl:NamedIndividual>', text, re.S)
print('=== Guizhitang 个体 ===')
print(m.group(0)[:2500] if m else 'NOT FOUND')
print()
print('总长度:', len(text))
