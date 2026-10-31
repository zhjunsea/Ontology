import glob, os
from rdflib import Graph, RDF, RDFS, OWL, URIRef

onto_dir = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi\ontology"
subs = ["core","calendar","chart","relations","tengod","deity","pattern",
        "yongshen","fortune","reading","diagnosis","rules"]

def label(g, s):
    for p in (RDFS.label,):
        for o in g.objects(s, p):
            return str(o)
    return ""

out = []
for name in subs:
    f = os.path.join(onto_dir, f"sizhubazi-{name}.owl")
    g = Graph()
    g.parse(f)
    out.append(f"\n===== {name} =====")
    # classes
    for s in sorted(set(g.subjects(RDF.type, OWL.Class))):
        if isinstance(s, URIRef):
            out.append(f"C\t{name}\t{s}\t{label(g,s)}")
    # object properties
    for s in sorted(set(g.subjects(RDF.type, OWL.ObjectProperty))):
        if isinstance(s, URIRef):
            out.append(f"OP\t{name}\t{s}\t{label(g,s)}")
    # datatype properties
    for s in sorted(set(g.subjects(RDF.type, OWL.DatatypeProperty))):
        if isinstance(s, URIRef):
            out.append(f"DP\t{name}\t{s}\t{label(g,s)}")
    # annotation properties
    for s in sorted(set(g.subjects(RDF.type, OWL.AnnotationProperty))):
        if isinstance(s, URIRef):
            out.append(f"AP\t{name}\t{s}\t{label(g,s)}")
    # named individuals
    for s in sorted(set(g.subjects(RDF.type, OWL.NamedIndividual))):
        if isinstance(s, URIRef):
            out.append(f"I\t{name}\t{s}\t{label(g,s)}")

with open(os.path.join(os.path.dirname(__file__), "labels.tsv"), "w", encoding="utf-8") as fh:
    fh.write("\n".join(out))
print("done", len(out))
