# -*- coding: utf-8 -*-
"""校验裁剪后的塔架中段本体 v14.0"""
import io, sys, re
from rdflib import Graph, Namespace, RDF, RDFS, OWL, URIRef
from rdflib.collection import Collection

P = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"
NS = "http://goldwind.com/ontology/tower-mid-section#"
EX = Namespace(NS)

g = Graph()
g.parse(P, format="xml")
print("三元组总数 :", len(g))

def local(u):
    s = str(u)
    return s.split("#")[-1] if "#" in s else s

classes = sorted({local(c) for c in g.subjects(RDF.type, OWL.Class) if isinstance(c, URIRef)})
objprops = sorted({local(p) for p in g.subjects(RDF.type, OWL.ObjectProperty)})
dataprops = sorted({local(p) for p in g.subjects(RDF.type, OWL.DatatypeProperty)})
inds = sorted({local(i) for i in g.subjects(RDF.type, OWL.NamedIndividual)})
print("具名类     :", len(classes))
print("对象属性   :", len(objprops))
print("数据属性   :", len(dataprops))
print("个体       :", len(inds))

# 1) 悬空引用：所有 rdf:resource 指向本体的 IRI 必须已定义
defined = set()
for s_, p_, o_ in g:
    if isinstance(s_, URIRef) and str(s_).startswith(NS):
        defined.add(str(s_))
dangling = set()
for s_, p_, o_ in g:
    if isinstance(o_, URIRef) and str(o_).startswith(NS) and str(o_) not in defined:
        dangling.add(str(o_))
print("悬空引用   :", sorted(local(d) for d in dangling) or "无")

# 2) Restriction 合法性
invalid = 0
for r in g.subjects(RDF.type, OWL.Restriction):
    onp = list(g.objects(r, OWL.onProperty))
    has_card = any(g.objects(r, x) for x in
                   (OWL.cardinality, OWL.minCardinality, OWL.maxCardinality,
                    OWL.qualifiedCardinality, OWL.minQualifiedCardinality, OWL.maxQualifiedCardinality))
    has_range = any(g.objects(r, x) for x in (OWL.allValuesFrom, OWL.someValuesFrom, OWL.hasValue))
    if not onp:
        invalid += 1; continue
    # onDataRange 只能配 qualifiedCardinality 系列
    if list(g.objects(r, OWL.onDataRange)):
        if not any(g.objects(r, x) for x in
                   (OWL.qualifiedCardinality, OWL.minQualifiedCardinality, OWL.maxQualifiedCardinality)):
            invalid += 1
    if not (has_card or has_range):
        invalid += 1
print("Restriction:", len(list(g.subjects(RDF.type, OWL.Restriction))), " invalid =", invalid)

# 3) inverseOf 配对
inv = list(g.subject_objects(OWL.inverseOf))
bad = [(local(a), local(b)) for a, b in inv if (b, a) not in inv]
print("inverseOf  :", len(inv), " 未配对 =", bad or "无")

# 4) 不相交类
print("AllDisjointClasses:", len(list(g.subjects(RDF.type, OWL.AllDisjointClasses))))
for d in g.subjects(RDF.type, OWL.AllDisjointClasses):
    m = list(g.objects(d, OWL.members))
    if m:
        col = list(Collection(g, m[0]))
        print("   -", [local(x) for x in col])

# 5) 类清单
print("\n类清单:", ", ".join(classes))
print("\n对象属性:", ", ".join(objprops))
