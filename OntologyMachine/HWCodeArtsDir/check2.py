# -*- coding: utf-8 -*-
from rdflib import Graph, RDF, RDFS, OWL, URIRef, Namespace
from rdflib.namespace import XSD
SWRL = Namespace("http://www.w3.org/2003/11/swrl#")
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
g = Graph(); g.parse(p, format="xml")
print("triples:", len(g))
print("Imp:", len(set(g.subjects(RDF.type, SWRL.Imp))))
print("Variable:", len(set(g.subjects(RDF.type, SWRL.Variable))))
print("Restriction:", len(set(g.subjects(RDF.type, OWL.Restriction))))
print("Class:", len(set(g.subjects(RDF.type, OWL.Class))))
# validate each Imp body/head lists
def walk_list(l):
    n=0
    while l and l != RDF.nil:
        n+=1
        l = g.value(l, RDF.rest)
    return n
for imp in g.subjects(RDF.type, SWRL.Imp):
    b = g.value(imp, SWRL.body); h = g.value(imp, SWRL.head)
    print("Imp", imp.split('/')[-1], "body atoms:", walk_list(b), "head atoms:", walk_list(h))
# dangling: class refs in subClassOf / onClass / domain / range that are not declared classes
declared = set(g.subjects(RDF.type, OWL.Class))
missing=set()
for s,o in g.subject_objects(RDFS.subClassOf):
    if isinstance(o,URIRef) and o in declared is False and str(o).startswith("http://example.org/sizhubazi"):
        missing.add(str(o))
for s,o in g.subject_objects(OWL.onClass):
    if str(o).startswith("http://example.org/sizhubazi") and o not in declared:
        missing.add(str(o))
print("dangling class refs:", sorted(missing))
# declared object/datatype props
props = set(g.subjects(RDF.type, OWL.ObjectProperty)) | set(g.subjects(RDF.type, OWL.DatatypeProperty))
missingp=set()
for s,pred,o in g:
    if isinstance(o,URIRef) and str(o).startswith("http://example.org/sizhubazi") and str(pred)==str(RDFS.range):
        if o not in declared and o not in props and 'XMLSchema' not in str(o):
            missingp.add(str(o))
print("dangling range refs (class expected):", sorted(x for x in missingp if not x.startswith('http://example.org/sizhubazi/rules/') or True)[:20])
