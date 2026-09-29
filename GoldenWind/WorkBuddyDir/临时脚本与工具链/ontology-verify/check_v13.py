# -*- coding: utf-8 -*-
from rdflib import Graph, RDF, RDFS, OWL, Namespace
import sys

P = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"
g = Graph()
g.parse(P, format="xml")
print("Triples:", len(g))

EX = Namespace("http://goldwind.com/ontology/tower-mid-section#")
OWLNS = OWL

classes = set(g.subjects(RDF.type, OWL.Class)) | set(g.subjects(RDF.type, RDFS.Class))
objprops = set(g.subjects(RDF.type, OWL.ObjectProperty))
dataprops = set(g.subjects(RDF.type, OWL.DatatypeProperty))
inds = set(g.subjects(RDF.type, OWL.NamedIndividual))
print("Classes:", len(classes))
print("ObjectProps:", len(objprops))
print("DataProps:", len(dataprops))
print("Individuals:", len(inds))

# Restriction 合法性
rests = list(g.subjects(RDF.type, OWL.Restriction))
print("Restrictions:", len(rests))
bad = 0
for r in rests:
    onp = list(g.objects(r, OWL.onProperty))
    hasAll = list(g.objects(r, OWL.allValuesFrom))
    hasSome = list(g.objects(r, OWL.someValuesFrom))
    hasVal = list(g.objects(r, OWL.hasValue))
    qc = list(g.objects(r, OWL.qualifiedCardinality)) + list(g.objects(r, OWL.minQualifiedCardinality)) + list(g.objects(r, OWL.maxQualifiedCardinality))
    onData = list(g.objects(r, OWL.onDataRange))
    onCls = list(g.objects(r, OWL.onClass))
    ok = True
    if not onp: ok = False
    if onData: ok = False                      # 非法：onDataRange 只能配 qualifiedCardinality
    if qc and not onCls: ok = False            # qualified 必须配 onClass
    if onCls and not qc: ok = False
    if not (hasAll or hasSome or hasVal or qc): ok = False
    if not ok:
        bad += 1
        print("  BAD RESTRICTION:", r, "onp=", bool(onp), "all=", bool(hasAll), "some=", bool(hasSome), "val=", bool(hasVal), "qc=", bool(qc), "onData=", bool(onData), "onCls=", bool(onCls))
print("Invalid restrictions:", bad)

# 逆属性配对
inv = {}
for p, q in g.subject_objects(OWL.inverseOf):
    inv.setdefault(p, set()).add(q)
mismatch = 0
for p, qs in inv.items():
    for q in qs:
        if p not in inv.get(q, set()):
            mismatch += 1
            print("  INVERSE NOT PAIRED:", p, "->", q)
print("InverseOf pairs:", len(inv), "mismatch:", mismatch)

# hasKey
print("hasKey:", len(list(g.subject_objects(OWL.hasKey))))
# disjoint groups
print("AllDisjointClasses:", len(list(g.subjects(RDF.type, OWL.AllDisjointClasses))))

# 残留检查
leftovers = ["sameSideLadderSupport", "sameSideCableBracket", "supportToWeldDistance",
             "bracketAttachedToTowerTube", "platformAttachedToTowerTube", "studAttachedToTowerTube",
             "trayAttachedToTowerTube", "supportAttachedToTowerTube", "clampAttachedToTowerTube",
             "anchorAttachedToTowerTube", "搭建顶段", "modelBelongsToWindTurbineHead",
             "regionBelongsToTowerMidSection", "onDataRange"]
print("--- leftovers ---")
for k in leftovers:
    c = sum(1 for _ in g.triples((None, None, None)))  # noop
    txt = open(P, encoding='utf-8').read()
    print("  %-32s %d" % (k, txt.count(k)))
