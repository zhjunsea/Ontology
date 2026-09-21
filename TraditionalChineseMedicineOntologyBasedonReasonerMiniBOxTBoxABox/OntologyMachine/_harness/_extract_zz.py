# -*- coding: utf-8 -*-
import re, json, io, sys
D = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
tbox = io.open(D + "/tcm-zhengzhuang.owl", encoding="utf-8").read()
abox = io.open(D + "/tcm-zhengzhuang-abox.owl", encoding="utf-8").read()

# 类：<owl:Class rdf:about="#X">...</owl:Class>  或自闭合
cls = {}
for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"\s*>(.*?)</owl:Class>', tbox, re.S):
    frag, body = m.group(1), m.group(2)
    lab = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
    com = re.search(r'<rdfs:comment xml:lang="zh">([^<]*)</rdfs:comment>', body)
    sup = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"', body)
    cls[frag] = {"label": lab.group(1) if lab else "", "comment": com.group(1) if com else "", "sup": sup}

# 个体
ind = {}
for m in re.finditer(r'<owl:NamedIndividual rdf:about="#([^"]+)"\s*>(.*?)</owl:NamedIndividual>', abox, re.S):
    frag, body = m.group(1), m.group(2)
    lab = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
    typ = re.findall(r'<rdf:type rdf:resource="#([^"]+)"', body)
    ind[frag] = {"label": lab.group(1) if lab else "", "type": typ}

print("TBox classes:", len(cls))
print("ABox individuals:", len(ind))
# 只保留 type 指向症状类的（排除脉象/舌象/腹证）
nonzz = [k for k,v in ind.items() if v["type"] and v["type"][0] not in cls]
print("individuals whose type not in zhengzhuang TBox:", len(nonzz), nonzz[:20])
json.dump({"cls":cls,"ind":ind}, io.open(D+"/../OntologyMachine/_harness/_zz_dump.json","w",encoding="utf-8"), ensure_ascii=False, indent=1)
