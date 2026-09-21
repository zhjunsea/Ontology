# -*- coding: utf-8 -*-
import re, io, sys
F = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-maixiang.owl'
s = io.open(F, encoding='utf-8').read()
want = sys.argv[1:] if len(sys.argv) > 1 else None
# find each top-level class block by locating <owl:Class rdf:about="#X"> and matching to its close
for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">', s):
    frag = m.group(1)
    if want and frag not in want:
        continue
    # take a window until next top-level <owl:Class rdf:about="# at col 4
    start = m.end()
    nxt = re.search(r'\n    <owl:Class rdf:about="#', s[start:])
    body = s[start:start + (nxt.start() if nxt else 4000)]
    subs = re.findall(r'rdfs:subClassOf[^>]*rdf:resource="#([^"]+)"', body)
    eqs = re.findall(r'owl:equivalentClass[^>]*rdf:resource="#([^"]+)"', body)
    eqmembers = re.findall(r'<owl:Class rdf:about="#([^"]+)"/>', body)
    print(frag, '| sub=', sorted(set(subs)), '| eq=', sorted(set(eqs)), '| eqmem=', sorted(set(eqmembers)))
