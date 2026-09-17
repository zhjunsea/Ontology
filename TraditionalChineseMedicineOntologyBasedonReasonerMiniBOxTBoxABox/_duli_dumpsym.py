# -*- coding: utf-8 -*-
import re, io, os
BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
txt = open(os.path.join(BASE,'tcm-zhengzhuang-abox.owl'), encoding='utf-8').read()
rows = []
for m in re.finditer(r'<owl:NamedIndividual rdf:about="#([^"]+)_instance">(.*?)</owl:NamedIndividual>', txt, re.S):
    n, body = m.group(1), m.group(2)
    lm = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
    rows.append((n, lm.group(1) if lm else ''))
out = io.StringIO()
out.write(f"共 {len(rows)} 个症状实例\n\n")
for n, l in rows:
    out.write(f"{n}\t{l}\n")
open(os.path.join(BASE,'_duli_all_sym.txt'),'w',encoding='utf-8').write(out.getvalue())
print("done", len(rows))
