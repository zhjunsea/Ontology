# -*- coding: utf-8 -*-
import re, json

with open('../ontology/tcm-fangji-abox.owl', encoding='utf-8') as f:
    text = f.read()

items = []
for m in re.finditer(r'<owl:NamedIndividual\s+rdf:about="#([^"]+)"(.*?)</owl:NamedIndividual>',
                     text, re.S):
    iri, body = m.group(1), m.group(2)
    lm = re.search(r'<rdfs:label[^>]*>([^<]+)</rdfs:label>', body)
    items.append((iri, lm.group(1).strip() if lm else None))

print('方剂个体总数:', len(items))
for iri, lab in items:
    print(lab, '|', iri)
