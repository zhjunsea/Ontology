# -*- coding: utf-8 -*-
"""公理普查：统计每个 owl 文件里各类 OWL 构造的出现次数，定位分类慢的结构来源。
只读，不改本体。"""
import os, re, sys, glob, collections

ROOT = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
FILES = [f for f in glob.glob(os.path.join(ROOT, "*.owl")) + glob.glob(os.path.join(ROOT, "fangzheng", "*.owl"))
         if ".bak" not in f]

KEYS = ["owl:equivalentClass", "owl:intersectionOf", "owl:unionOf", "owl:complementOf",
        "owl:someValuesFrom", "owl:allValuesFrom", "owl:hasValue", "owl:Restriction",
        "rdfs:subClassOf", "rdfs:domain", "rdfs:range", "owl:disjointWith",
        "owl:AllDisjointClasses", "owl:NamedIndividual", "owl:TransitiveProperty",
        "owl:SymmetricProperty", "owl:FunctionalProperty", "owl:InverseFunctionalProperty",
        "owl:inverseOf", "owl:propertyChainAxiom", "owl:oneOf", "owl:hasSelf",
        "owl:minCardinality", "owl:maxCardinality", "owl:qualifiedCardinality",
        "owl:DatatypeProperty", "owl:ObjectProperty", "owl:AnnotationProperty"]

total = collections.Counter()
print("%-28s %s" % ("file", " ".join(k.split(":")[1][:9] for k in KEYS)))
for f in FILES:
    txt = open(f, encoding="utf-8", errors="replace").read()
    c = collections.Counter()
    for k in KEYS:
        c[k] = txt.count("<" + k)
    total.update(c)
    print("%-28s %s" % (os.path.basename(f), " ".join("%9d" % c[k] for k in KEYS)))
print("-" * 200)
print("%-28s %s" % ("TOTAL", " ".join("%9d" % total[k] for k in KEYS)))

# 关键：subClassOf 的对象是不是匿名 Restriction（方证的「必要条件」写法）
print()
print("=== rdfs:subClassOf 指向匿名 Restriction 的数量（<rdfs:subClassOf><owl:Restriction>）===")
for f in FILES:
    txt = open(f, encoding="utf-8", errors="replace").read()
    n = len(re.findall(r"<rdfs:subClassOf>\s*<owl:Restriction", txt))
    if n:
        print("%6d  %s" % (n, os.path.basename(f)))
