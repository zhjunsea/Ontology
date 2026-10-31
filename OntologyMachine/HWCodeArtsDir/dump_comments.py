# -*- coding: utf-8 -*-
import sys
from rdflib import Graph, RDFS, RDF, OWL

g = Graph()
g.parse(sys.argv[1])

print('TRIPLES', len(g))
comments = set()
for s, p, o in g.triples((None, RDFS.comment, None)):
    comments.add(str(o))
print('\n===== rdfs:comment (%d unique) =====' % len(comments))
for c in sorted(comments):
    print('-', c)

inds = set()
for s in g.subjects(RDF.type, None):
    types = set(str(o) for o in g.objects(s, RDF.type))
    if not (types & {str(OWL.Class), str(OWL.ObjectProperty), str(OWL.DatatypeProperty), str(OWL.Ontology), 'http://www.w3.org/2003/11/swrl#Imp'}):
        if types:
            inds.add(str(s))
print('\n===== individuals (%d) =====' % len(inds))
for s in sorted(inds):
    lbl = g.value(s, RDFS.label)
    print('%s | %s' % (s.split('#')[-1], str(lbl) if lbl else ''))
