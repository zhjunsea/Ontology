# -*- coding: utf-8 -*-
import sys
from rdflib import Graph, RDF, OWL, URIRef
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
g = Graph()
g.parse(p, format="xml")
print("triples:", len(g))
classes = set(g.subjects(RDF.type, OWL.Class))
print("owl:Class count:", len(classes))
restr = set(g.subjects(RDF.type, OWL.Restriction))
print("owl:Restriction count:", len(restr))
print("ObjectProperty:", len(set(g.subjects(RDF.type, OWL.ObjectProperty))))
print("DatatypeProperty:", len(set(g.subjects(RDF.type, OWL.DatatypeProperty))))
# dangling class refs check: classes referenced as rdfs:subClassOf / owl:onClass / rdfs:domain/range
