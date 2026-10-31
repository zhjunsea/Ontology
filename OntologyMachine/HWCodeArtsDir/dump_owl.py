# -*- coding: utf-8 -*-
import sys
from rdflib import Graph, RDF, RDFS, OWL, Namespace

g = Graph()
g.parse(sys.argv[1])
print('TRIPLES', len(g))

def label(s):
    lbl = g.value(s, RDFS.label)
    return str(lbl) if lbl is not None else ''

def show(kind, cls):
    items = sorted(set(str(s) for s in g.subjects(RDF.type, cls)))
    print('\n===== %s (%d) =====' % (kind, len(items)))
    for s in items:
        print('%s | %s' % (s.split('#')[-1], label(s)))

show('CLASS', OWL.Class)
show('OBJECTPROPERTY', OWL.ObjectProperty)
show('DATATYPEPROPERTY', OWL.DatatypeProperty)

SWRL = Namespace('http://www.w3.org/2003/11/swrl#')
imps = sorted(set(str(s) for s in g.subjects(RDF.type, SWRL.Imp)))
print('\n===== SWRL IMP (%d) =====' % len(imps))
for s in imps:
    print(s)

ind = sorted(set(str(s) for s in g.subjects(RDF.type, OWL.NamedIndividual)))
print('\n===== NAMEDINDIVIDUAL (%d) =====' % len(ind))
for s in ind:
    print('%s | %s' % (s.split('#')[-1], label(s)))

desc = sorted(set(str(s) for s in g.subjects(RDF.type, RDF.Description)))
print('\n===== RDF:Description typed (%d) =====' % len(desc))
for s in desc:
    print('%s | %s' % (s.split('#')[-1], label(s)))
