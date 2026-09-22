# -*- coding: utf-8 -*-
"""扫描全本体：所有 ⊑ Han(寒) / ⊑ Xu(虚) 的类，以及 Panju_* 判据的八纲归属"""
import re, sys, io, glob, os
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ONT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
BAGANG = {'Biao','Li','Banbiaobanli','Han','Re','Xu','Shi','Yin','Yang'}

print("=== 所有 ⊑ Han(寒) 的类 ===")
for f in sorted(glob.glob(os.path.join(ONT,'**','*.owl'), recursive=True)):
    t = open(f, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', t, re.S):
        n, b = m.group(1), m.group(2)
        subs = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', b)
        if 'Han' in subs:
            print(f"  {n} ⊑ {subs}   [{os.path.basename(f)}]")

print("\n=== 所有 ⊑ Xu(虚) 的类（仅症状/判据，排除脉舌） ===")
for f in sorted(glob.glob(os.path.join(ONT,'**','*.owl'), recursive=True)):
    t = open(f, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', t, re.S):
        n, b = m.group(1), m.group(2)
        subs = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', b)
        if 'Xu' in subs and n.startswith('Panju'):
            print(f"  {n} ⊑ {subs}   [{os.path.basename(f)}]")

print("\n=== 所有 Panju_* 判据的 ⊑ 八纲 ===")
for f in sorted(glob.glob(os.path.join(ONT,'**','*.owl'), recursive=True)):
    t = open(f, encoding='utf-8', errors='replace').read()
    for m in re.finditer(r'<owl:Class rdf:about="#(Panju_[^"]+)">(.*?)</owl:Class>', t, re.S):
        n, b = m.group(1), m.group(2)
        subs = [x for x in re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', b) if x in BAGANG]
        if subs:
            print(f"  {n} ⊑ {subs}   [{os.path.basename(f)}]")
