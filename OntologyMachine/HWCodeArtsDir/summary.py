# -*- coding: utf-8 -*-
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from collections import Counter
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
g = Graph(); g.parse(p, format="xml")
NS = "http://example.org/sizhubazi/"
def mod(u): 
    s=str(u)[len(NS):]
    return s.split('/')[0] if '/' in s else '(root)'
print("== owl:Class by module ==")
c = Counter(mod(x) for x in set(g.subjects(RDF.type, OWL.Class)))
for k,v in sorted(c.items()): print(f"  {k}: {v}")
print("== ObjectProperty by module ==")
c = Counter(mod(x) for x in set(g.subjects(RDF.type, OWL.ObjectProperty)))
for k,v in sorted(c.items()): print(f"  {k}: {v}")
print("== DatatypeProperty by module ==")
c = Counter(mod(x) for x in set(g.subjects(RDF.type, OWL.DatatypeProperty)))
for k,v in sorted(c.items()): print(f"  {k}: {v}")
print("== ABox individuals (typed, excluding class/prop/restriction) ==")
cnt=0
for s in set(g.subjects(RDF.type, None)):
    t = g.value(s, RDF.type)
    pass
print("total distinct subjects typed as a domain class:")
n=0
for s,t in g.subject_objects(RDF.type):
    if str(t).startswith(NS) and 'Class' not in str(t):
        n+=1
print("  ", n)
