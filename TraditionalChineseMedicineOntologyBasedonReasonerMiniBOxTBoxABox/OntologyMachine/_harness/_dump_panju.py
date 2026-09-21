# -*- coding: utf-8 -*-
import re, io, sys
F = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-core.owl'
s = io.open(F, encoding='utf-8').read()
# Panju_* classes
for m in re.finditer(r'<owl:Class rdf:about="#(Panju_[A-Z0-9]+)">', s):
    frag = m.group(1)
    start = m.end()
    nxt = re.search(r'\n    <owl:Class rdf:about="#', s[start:])
    body = s[start:start + (nxt.start() if nxt else 3000)]
    subs = re.findall(r'rdfs:subClassOf[^>]*rdf:resource="#([^"]+)"', body)
    members = re.findall(r'<owl:Class rdf:about="#([^"]+)"/>', body)
    unions = re.findall(r'<owl:unionOf[^>]*>(.*?)</owl:unionOf>', body, re.S)
    um = []
    for u in unions:
        um += re.findall(r'rdf:resource="#([^"]+)"', u)
    label = re.search(r'<rdfs:label[^>]*>([^<]*)</rdfs:label>', body)
    print(frag, '| sub=', subs, '| and=', sorted(set(members)), '| or=', sorted(set(um)), '|', label.group(1) if label else '')
