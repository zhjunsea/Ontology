# -*- coding: utf-8 -*-
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
from rdflib import Graph, RDF, RDFS, OWL, URIRef, Namespace
from rdflib.namespace import SKOS

TBOX = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/sizhubazi-tbox.owl"
SKOSF = r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/sizhubazi-terminology.ttl"

import glob
g = Graph()
files = glob.glob(r"D:/work/Ontology/OntologyMachine/OntologyFrameworkSiZhuBaZi/ontology/sizhubazi-*.owl")
for fp in sorted(files):
    try:
        g.parse(fp, format="xml")
    except Exception as e:
        print("PARSE FAIL", fp, e)
print("=== TBox axioms:", len(g), "===")
classes = {}
for s in g.subjects(RDF.type, OWL.Class):
    if isinstance(s, URIRef):
        lbl = g.value(s, RDFS.label)
        classes[str(s)] = str(lbl) if lbl else ""
print("### CLASSES (%d) ###" % len(classes))
for k in sorted(classes):
    print("%s | %s" % (k, classes[k]))

# data properties
dps = [str(s) for s in g.subjects(RDF.type, OWL.DatatypeProperty)]
print("### DATATYPE PROPS (%d) ###" % len(dps))
for k in sorted(dps):
    print(k)
ops = [str(s) for s in g.subjects(RDF.type, OWL.ObjectProperty)]
print("### OBJECT PROPS (%d) ###" % len(ops))
for k in sorted(ops):
    print(k)

gs = Graph()
gs.parse(SKOSF, format="turtle")
print("=== SKOS triples:", len(gs), "===")
concepts = {}
for s in gs.subjects(RDF.type, SKOS.Concept):
    pl = gs.value(s, SKOS.prefLabel)
    concepts[str(s)] = str(pl) if pl else ""
print("### SKOS CONCEPTS (%d) ###" % len(concepts))
for k in sorted(concepts):
    print("%s | %s" % (k, concepts[k]))
