import os
from rdflib import Graph, RDF, RDFS, OWL, URIRef
from rdflib.namespace import split_uri

onto_dir = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
subs = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]

g = Graph()
for name in subs:
    g.parse(os.path.join(onto_dir, f"sizhubazi-{name}.owl"))

def loc(u):
    try:
        _, n = split_uri(u)
        return n
    except Exception:
        return str(u)

# collect classes with labels
labels = {}
for s in set(g.subjects(RDF.type, OWL.Class)):
    if not isinstance(s, URIRef): continue
    for o in g.objects(s, RDFS.label):
        labels[s] = str(o)

lines = []
lines.append("### CLASS HIERARCHY (has-label classes) ###")
for s in sorted(labels):
    sups = [loc(o) for o in g.objects(s, RDFS.subClassOf) if isinstance(o, URIRef)]
    allsup = g.objects(s, RDFS.subClassOf)
    restricted = any(not isinstance(o, URIRef) for o in allsup)
    lines.append(f"{loc(s)}\t{labels[s]}\tsubClassOf={sups}\trestricted={restricted}")

lines.append("\n### OBJECT PROPERTIES w/ label ###")
for s in sorted(set(g.subjects(RDF.type, OWL.ObjectProperty))):
    if not isinstance(s, URIRef): continue
    lb = list(g.objects(s, RDFS.label))
    if lb:
        lines.append(f"{loc(s)}\t{lb[0]}")

lines.append("\n### DATATYPE PROPERTIES w/ label ###")
for s in sorted(set(g.subjects(RDF.type, OWL.DatatypeProperty))):
    if not isinstance(s, URIRef): continue
    lb = list(g.objects(s, RDFS.label))
    if lb:
        lines.append(f"{loc(s)}\t{lb[0]}")

lines.append("\n### IRIs declared as Class WITHOUT any label anywhere ###")
for s in sorted(set(g.subjects(RDF.type, OWL.Class))):
    if isinstance(s, URIRef) and s not in labels:
        lines.append(loc(s))

# subClassOf restricted (anonymous) - parents that are URIRef
lines.append("\n### anonymous Restriction parents? count ###")

with open(os.path.join(os.path.dirname(__file__), "structure.txt"), "w", encoding="utf-8") as fh:
    fh.write("\n".join(lines))
print("classes_with_label", len(labels))
print("objprops_with_label", sum(1 for s in set(g.subjects(RDF.type, OWL.ObjectProperty)) if isinstance(s,URIRef) and list(g.objects(s,RDFS.label))))
print("dtprops_with_label", sum(1 for s in set(g.subjects(RDF.type, OWL.DatatypeProperty)) if isinstance(s,URIRef) and list(g.objects(s,RDFS.label))))
