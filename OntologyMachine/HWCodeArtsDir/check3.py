# -*- coding: utf-8 -*-
from rdflib import Graph, RDF, RDFS, OWL, URIRef
SWRL = "http://www.w3.org/2003/11/swrl#"
p = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology\sizhubazi-all.owl"
g = Graph(); g.parse(p, format="xml")
NS = "http://example.org/sizhubazi/"
subjects = set(str(s) for s in g.subjects())
dangling = set()
for s,pred,o in g:
    if isinstance(o, URIRef):
        os = str(o)
        if os.startswith(NS) and os not in subjects:
            dangling.add((pred.split('#')[-1], os))
print("dangling object URIs (used but never subject):")
for d in sorted(dangling):
    print("  ", d)
print("count:", len(dangling))
