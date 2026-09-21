# -*- coding: utf-8 -*-
import xml.etree.ElementTree as ET
p = r'D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology\fangzheng\shaoyang_yangming.owl'
r = ET.parse(p).getroot()
RDF = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'
print("RDF len", len(RDF), repr(RDF))
n = 0
for c in r:
    if not c.tag.endswith('}Class'):
        continue
    for sc in c:
        if sc.tag.endswith('}subClassOf'):
            n += 1
            if n <= 8:
                print("subClassOf attrib:", sc.attrib)
print("total subClassOf:", n)
# 直接找 Baihutangzheng
for c in r:
    if c.get(RDF + 'about') == '#Baihutangzheng':
        print("FOUND Baihutangzheng, children:")
        for ch in c:
            print("   ", ch.tag.split('}')[-1], ch.attrib)
