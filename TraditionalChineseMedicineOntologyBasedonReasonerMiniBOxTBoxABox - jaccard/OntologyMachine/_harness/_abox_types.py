# -*- coding: utf-8 -*-
"""深入解析 3 个 ABox 模块：每个个体的 rdf:type 目标，验证 'X_instance : X' 命名约定。"""
import os, collections
import xml.etree.ElementTree as ET

ONT = os.environ.get("ONT_DIR",
    r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology")
RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

FILES = ["tcm-shexiang-abox.owl", "tcm-zhengzhuang-abox.owl", "tcm-maixiang-abox.owl"]

def local(tag):
    return tag.split('}')[-1] if '}' in tag else tag

grand = collections.Counter()
mismatch = []
multi = []
total_ind = 0
for fn in FILES:
    p = os.path.join(ONT, fn)
    tree = ET.parse(p)
    root = tree.getroot()
    types_per_ind = {}
    for el in root:
        if local(el.tag) != "NamedIndividual":
            continue
        about = el.get("{%s}about" % RDF)
        frag = about.split('#')[-1] if about else None
        ts = []
        for c in el:
            lt = local(c.tag)
            if lt == "type":
                r = c.get("{%s}resource" % RDF)
                ts.append(r.split('#')[-1] if r else None)
        types_per_ind[frag] = ts
        total_ind += 1
        for t in ts:
            grand[t] += 1
        # 命名约定检查：X_instance -> X
        if frag and frag.endswith("_instance"):
            base = frag[:-len("_instance")]
            if base not in ts:
                mismatch.append((fn, frag, ts))
        else:
            mismatch.append((fn, frag, ts))
        if len(ts) > 1:
            multi.append((fn, frag, ts))
    print(f"{fn}: 个体={len(types_per_ind)}")
    # 类型分布 top
    c = collections.Counter()
    for f, ts in types_per_ind.items():
        for t in ts:
            c[t] += 1
    print("   type 目标 top10:", c.most_common(10))

print()
print("总个体数:", total_ind)
print("type 断言总数:", sum(grand.values()))
print("不符合 X_instance->X 约定的个体数:", len(mismatch))
for m in mismatch[:15]:
    print("   ", m)
print("有多个 type 的个体数:", len(multi))
for m in multi[:15]:
    print("   ", m)
