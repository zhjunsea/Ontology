# -*- coding: utf-8 -*-
"""列出四诊各类（含中文名），供人工依医理甄选互斥对。"""
import re, os, sys, io, collections
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"

def read(p):
    with open(p, encoding='utf-8') as f: return f.read()

parents, labels, comments = collections.defaultdict(set), {}, {}
for cf in ["tcm-core.owl","tcm-zhengzhuang.owl","tcm-maixiang.owl","tcm-shexiang.owl","tcm-fuzheng.owl"]:
    txt = read(os.path.join(ONT, cf))
    for m in re.finditer(r'<owl:Class rdf:about="#([A-Za-z0-9_]+)">(.*?)</owl:Class>', txt, re.S):
        frag, body = m.group(1), m.group(2)
        for pm in re.finditer(r'<rdfs:subClassOf rdf:resource="#([A-Za-z0-9_]+)"', body):
            parents[frag].add(pm.group(1))
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>', body)
        if lm: labels[frag] = lm.group(1)
        cm = re.search(r'<rdfs:comment xml:lang="zh">([^<]+)</rdfs:comment>', body)
        if cm: comments[frag] = cm.group(1)

def closure(frag):
    seen, stack = set(), [frag]
    while stack:
        c = stack.pop()
        for p in parents.get(c, ()):
            if p not in seen: seen.add(p); stack.append(p)
    return seen

CH = {"Zhengzhuang":"症状","Maixiang":"脉象","Shexiang":"舌象","Fuzheng":"腹证"}
groups = collections.defaultdict(list)
for frag in sorted(labels):
    cl = closure(frag) | {frag}
    for top, name in CH.items():
        if top in cl:
            groups[name].append(frag); break

for name in ["症状","脉象","舌象","腹证"]:
    lst = groups[name]
    print(f"\n===== {name} ({len(lst)}) =====")
    for f in lst:
        print(f"  {f}\t{labels.get(f,'')}")
