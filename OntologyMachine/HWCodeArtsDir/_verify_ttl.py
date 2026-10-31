from rdflib import Graph
from rdflib.namespace import SKOS
g=Graph(); g.parse(r'D:/work/Ontology/OntologyMachine/OntologyFrameworkZiWeiDouShu/ontology/ziweidoushu-terminology.ttl',format='turtle')
print('triples',len(g))
print('concepts', len(set(g.subjects(None, SKOS.prefLabel))))
print('exactMatch', len(set(g.subjects(None,SKOS.exactMatch))))
print('altLabel', len(set(g.subjects(None,SKOS.altLabel))))
print('broader', len(set(g.subjects(None,SKOS.broader))))
