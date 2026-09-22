# -*- coding: utf-8 -*-
"""统计 3 个 ABox 模块的公理构成，判断哪些是诊断链路真正需要的。"""
import os, sys, collections
import xml.etree.ElementTree as ET

ONT = os.environ.get("ONT_DIR",
    r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology")

RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
OWL = "http://www.w3.org/2002/07/owl#"

FILES = ["tcm-shexiang-abox.owl", "tcm-zhengzhuang-abox.owl", "tcm-maixiang-abox.owl"]

def local(tag):
    return tag.split('}')[-1] if '}' in tag else tag

for fn in FILES:
    p = os.path.join(ONT, fn)
    if not os.path.exists(p):
        print(f"!! missing {fn}")
        continue
    tree = ET.parse(p)
    root = tree.getroot()
    kinds = collections.Counter()
    inds = set()
    classes_asserted = collections.Counter()
    props = collections.Counter()
    for el in root:
        t = local(el.tag)
        kinds[t] += 1
        if t == "NamedIndividual":
            inds.add(el.get("{%s}about" % RDF))
        elif t == "ClassAssertion":
            # child Class or rdf:resource
            cls = None
            for c in el:
                if local(c.tag) == "Class":
                    cls = c.get("{%s}about" % RDF)
            if cls is None:
                cls = el.get("{%s}resource" % RDF)
            classes_asserted[cls] += 1
        elif t in ("ObjectPropertyAssertion", "DataPropertyAssertion"):
            for c in el:
                if local(c.tag) in ("ObjectProperty", "DataProperty"):
                    props[c.get("{%s}about" % RDF)] += 1
    print(f"===== {fn} =====")
    print("  顶层元素:", dict(kinds))
    print("  NamedIndividual 声明数:", len(inds))
    print("  ClassAssertion 目标类 top10:", classes_asserted.most_common(10))
    print("  属性断言 top10:", props.most_common(10))
    print()
