# -*- coding: utf-8 -*-
import os
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import SKOS

ONTO = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
SUBS = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]

tg = Graph(); tg.parse(os.path.join(ONTO, "sizhubazi-terminology.ttl"))
og = Graph()
for n in SUBS:
    og.parse(os.path.join(ONTO, f"sizhubazi-{n}.owl"))

onto_iris = set()
for p in (OWL.Class, OWL.ObjectProperty, OWL.DatatypeProperty):
    for s in og.subjects(RDF.type, p):
        if isinstance(s, URIRef):
            onto_iris.add(s)

concepts = set(tg.subjects(RDF.type, SKOS.Concept))
broader_targets = set(o for s in concepts for o in tg.objects(s, SKOS.broader))
exact = {}
missing = []
for s in concepts:
    for o in tg.objects(s, SKOS.exactMatch):
        exact[s] = o
        if isinstance(o, URIRef) and o not in onto_iris:
            missing.append((str(s), str(o)))

no_pref = [str(s) for s in concepts if not list(tg.objects(s, SKOS.prefLabel))]
no_exact = [str(s) for s in concepts if not list(tg.objects(s, SKOS.exactMatch))]
bad_broader = [str(o) for o in broader_targets if o not in concepts]
alt_count = sum(1 for s in concepts for _ in tg.objects(s, SKOS.altLabel))
with_alt = sum(1 for s in concepts if list(tg.objects(s, SKOS.altLabel)))

print("concepts:", len(concepts))
print("with exactMatch:", len(exact))
print("exactMatch targets not in ontology:", missing)
print("concepts without prefLabel:", no_pref)
print("concepts without exactMatch:", no_exact)
print("broader targets not a concept:", bad_broader)
print("altLabel total:", alt_count, "concepts with altLabel:", with_alt)

txt = open(os.path.join(ONTO, "sizhubazi-terminology.ttl"), encoding="utf-8").read()
print("lines with '# 源:':", txt.count("# 源:"))
