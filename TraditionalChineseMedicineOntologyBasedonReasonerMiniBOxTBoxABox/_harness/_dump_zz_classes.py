# -*- coding: utf-8 -*-
import re, json, io, os
D = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
src = io.open(os.path.join(D,"tcm-zhengzhuang.owl"), encoding="utf-8").read()
# split by owl:Class blocks
blocks = re.findall(r'<owl:Class rdf:about="#([^"]+)">(.*?)</owl:Class>', src, re.S)
rows=[]
for name, body in blocks:
    lab = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', body)
    com = re.search(r'<rdfs:comment xml:lang="zh">(.*?)</rdfs:comment>', body)
    subs = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"/>', body)
    rows.append({"name":name,"label":lab.group(1) if lab else "","comment":com.group(1) if com else "","supers":subs})
print("total classes:", len(rows))
# sections
secs = re.findall(r'<!-- =+ (.*?) =+ -->', src)
print("sections:", len(secs))
for s in secs: print("  -", s)
json.dump(rows, io.open(r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/_harness/_zz_classes.json","w",encoding="utf-8"), ensure_ascii=False, indent=1)
# label dup check
from collections import Counter
c=Counter(r["label"] for r in rows)
dups=[k for k,v in c.items() if v>1]
print("dup labels:", len(dups), dups[:30])
