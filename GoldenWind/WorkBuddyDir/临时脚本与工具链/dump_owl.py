# -*- coding: utf-8 -*-
"""Dump TowerMidSection.owl structure: classes, object props, data props, restrictions."""
import sys
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.collection import Collection

OWL_FILE = r"D:\work\Ontology\GoldenWind\ontology\TowerMidSection.owl"

g = Graph()
g.parse(OWL_FILE, format="xml")
print("triples:", len(g))

NS = None
for s in g.subjects(RDF.type, OWL.Ontology):
    print("ontology:", s)
    for p, o in g.predicate_objects(s):
        if p in (OWL.versionInfo, RDFS.comment, OWL.imports):
            print("   ", p.split("#")[-1], "=", str(o)[:120])

# namespace
for s in g.subjects(RDF.type, OWL.Ontology):
    NS = str(s)
print("NS =", NS)


def frag(u):
    s = str(u)
    return s.split("#")[-1] if "#" in s else s.split("/")[-1]


print("\n=== 类 (owl:Class) ===")
classes = sorted({c for c in g.subjects(RDF.type, OWL.Class) if isinstance(c, URIRef)})
for c in classes:
    parents = [frag(p) for p in g.objects(c, RDFS.subClassOf) if isinstance(p, URIRef)]
    print(f"  {frag(c):<28} ⊑ {parents}")

print("\n=== 对象属性 ===")
for p in sorted({x for x in g.subjects(RDF.type, OWL.ObjectProperty) if isinstance(x, URIRef)}):
    dom = [frag(d) for d in g.objects(p, RDFS.domain) if isinstance(d, URIRef)]
    rng = [frag(r) for r in g.objects(p, RDFS.range) if isinstance(r, URIRef)]
    inv = [frag(i) for i in g.objects(p, OWL.inverseOf) if isinstance(i, URIRef)]
    print(f"  {frag(p):<30} dom={dom} rng={rng} inv={inv}")

print("\n=== 数据属性 ===")
for p in sorted({x for x in g.subjects(RDF.type, OWL.DatatypeProperty) if isinstance(x, URIRef)}):
    dom = [frag(d) for d in g.objects(p, RDFS.domain) if isinstance(d, URIRef)]
    rng = [frag(r) for r in g.objects(p, RDFS.range) if isinstance(r, URIRef)]
    print(f"  {frag(p):<30} dom={dom} rng={rng}")

print("\n=== Restriction (按宿主类) ===")
for cls in classes:
    for sup in g.objects(cls, RDFS.subClassOf):
        if (sup, RDF.type, OWL.Restriction) in g:
            onp = g.value(sup, OWL.onProperty)
            kinds = []
            for k in ("onClass", "onDataRange", "someValuesFrom", "allValuesFrom", "hasValue",
                      "minQualifiedCardinality", "maxQualifiedCardinality", "qualifiedCardinality",
                      "minCardinality", "maxCardinality", "cardinality"):
                v = g.value(sup, OWL[k])
                if v is not None:
                    kinds.append(f"{k}={frag(v) if isinstance(v, URIRef) else v}")
            print(f"  {frag(cls):<24} ⊑ ∃{frag(onp) if onp else '?'} [{', '.join(kinds)}]")

print("\n=== hasKey ===")
for c in classes:
    for k in g.objects(c, OWL.hasKey):
        print(f"  {frag(c)}: {[frag(x) for x in Collection(g, k)]}")

print("\n=== AllDisjointClasses ===")
for d in g.subjects(RDF.type, OWL.AllDisjointClasses):
    for m in g.objects(d, OWL.members):
        print("  ", [frag(x) for x in Collection(g, m)])

print("\n=== 个体数 ===")
inds = list(g.subjects(RDF.type, OWL.NamedIndividual))
print("  NamedIndividual:", len(inds))
print("  (任意 rdf:type 非 owl 类的个体):",
      len({s for s in g.subjects(RDF.type, None)
           if isinstance(s, URIRef) and not str(s).startswith(str(OWL))}))
