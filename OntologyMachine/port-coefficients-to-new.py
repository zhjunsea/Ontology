import rdflib
from rdflib import Graph, URIRef, Literal
from rdflib.namespace import RDF, RDFS, OWL, XSD
NEW = r'D:/work/Ontology/OntologyMachine/OntologyFrameworkSteelStructureDesignNew/ontology/steelstructuredesignnew-all.owl'
OLD = r'D:/work/Ontology/OntologyMachine/OntologyFrameworkSteelStructureDesign/ontology/steelstructuredesign-abox.owl'
BASE = 'http://example.org/steelstructuredesignnew#'
def lname(u):
    s = str(u)
    for sep in ['#', '/']:
        if sep in s:
            s = s.rsplit(sep, 1)[-1]
    return s
KEEP = {'StabilityCoefficientEntry','OverallStabilityFactorEntry','StrengthDesignValueEntry','DesignFormula','PlasticityFactorEntry','TubePlasticityFactorEntry','SlendernessLimitEntry','SlenderLimitEntry'}
g_new = Graph(); g_new.parse(NEW, format='xml')
g_old = Graph(); g_old.parse(OLD, format='xml')
CE = URIRef(BASE + 'CoefficientEntry'); CODE = URIRef(BASE + 'coeffCode')
subj = set()
for s, t in g_old.subject_objects(RDF.type):
    if lname(t) in KEEP: subj.add(s)
props = {}; n = 0
for s in subj:
    ns = URIRef(BASE + lname(s)); g_new.add((ns, RDF.type, CE)); g_new.add((ns, CODE, Literal(lname(s))))
    for p, o in list(g_old.predicate_objects(s)):
        if p == RDF.type or not isinstance(o, Literal): continue
        ln = lname(p); np = URIRef(BASE + ln); g_new.add((ns, np, o))
        if ln not in props: props[ln] = o.datatype if o.datatype else XSD.string
    n += 1
for ln, rng in props.items():
    g_new.add((URIRef(BASE + ln), RDF.type, OWL.DatatypeProperty)); g_new.add((URIRef(BASE + ln), RDFS.range, URIRef(str(rng)))); g_new.add((URIRef(BASE + ln), RDFS.domain, CE))
g_new.serialize(destination=NEW, format='xml')
print('ported CoefficientEntry instances=', n, 'props=', len(props), 'totalTriples=', len(g_new))