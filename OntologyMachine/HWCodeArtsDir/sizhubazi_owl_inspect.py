# -*- coding: utf-8 -*-
"""按类枚举 sizhubazi-all.owl 个体，供第 8 步合规断言精确取值。"""
import sys
from collections import defaultdict
from rdflib import Graph, RDF, OWL, URIRef, Namespace
from rdflib.namespace import RDFS

path = sys.argv[1] if len(sys.argv) > 1 else r"OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
g = Graph()
g.parse(path, format="xml")

print("triples =", len(g))
counts = defaultdict(int)
for t in (OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty, OWL.NamedIndividual,
          OWL.Restriction, OWL.Ontology):
    pass
print("owl:Class =", len(set(g.subjects(RDF.type, OWL.Class))))
print("owl:ObjectProperty =", len(set(g.subjects(RDF.type, OWL.ObjectProperty))))
print("owl:DatatypeProperty =", len(set(g.subjects(RDF.type, OWL.DatatypeProperty))))
print("owl:NamedIndividual =", len(set(g.subjects(RDF.type, OWL.NamedIndividual))))

SWRL = Namespace("http://www.w3.org/2003/11/swrl#")
print("swrl:Imp =", len(set(g.subjects(RDF.type, SWRL.Imp))))
print("swrl:Variable =", len(set(g.subjects(RDF.type, SWRL.Variable))))

restrictions = set(g.subjects(RDF.type, OWL.Restriction))
print("owl:Restriction =", len(restrictions))
card = 0
for r in restrictions:
    if (r, OWL.cardinality, None) in g:
        card += 1
print("  with owl:cardinality =", card)

# 枚举个体：凡有 rdf:type 且不是 class/property/restriction/ontology
SPECIAL = {OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty, OWL.Restriction,
           OWL.Ontology, OWL.AnnotationProperty, RDFS.Class}
by_class = defaultdict(set)
all_ind = set()
for s, o in g.subject_objects(RDF.type):
    if o in SPECIAL:
        continue
    if isinstance(o, URIRef):
        by_class[o].add(s)
        all_ind.add(s)

print("\n== 个体（按类） ==")
for cls in sorted(by_class, key=lambda x: str(x)):
    frag = str(cls).split("/")[-1]
    members = sorted(str(m).split("/")[-1] for m in by_class[cls])
    print(f"[{frag}] ({len(members)}): {', '.join(members)}")
print("\nTOTAL individuals =", len(all_ind))
