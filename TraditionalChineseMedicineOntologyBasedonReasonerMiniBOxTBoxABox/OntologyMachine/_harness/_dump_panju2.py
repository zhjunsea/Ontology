# -*- coding: utf-8 -*-
"""用 ElementTree 打印 Panju_* 判据的完整合取/析取表达式。"""
import os
import xml.etree.ElementTree as ET

ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
RDF = '{http://www.w3.org/1999/02/22-rdf-syntax-ns#}'
RDFS = '{http://www.w3.org/2000/01/rdf-schema#}'
OWL = '{http://www.w3.org/2002/07/owl#}'


def frag(iri):
    return iri.split('#')[-1]


def node(el):
    """把 owl:Restriction / owl:Class 转成表达式字符串"""
    if el.tag == OWL + 'Restriction':
        prop = el.find(OWL + 'onProperty')
        some = el.find(OWL + 'someValuesFrom')
        if some is not None:
            r = some.get(RDF + 'resource')
            if r:
                return '%s some %s' % (frag(prop.get(RDF + 'resource')), frag(r))
            return '%s some (%s)' % (frag(prop.get(RDF + 'resource')), node(some))
        return 'Restriction?'
    inter = el.find(OWL + 'intersectionOf')
    if inter is not None:
        return '(' + ' AND '.join(node(c) for c in list(inter)) + ')'
    uni = el.find(OWL + 'unionOf')
    if uni is not None:
        return '(' + ' OR '.join(node(c) for c in list(uni)) + ')'
    r = el.get(RDF + 'about')
    if r:
        return frag(r)
    kids = list(el)
    if len(kids) == 1:
        return node(kids[0])
    return '?'


for fn in sorted(os.listdir(ONT)):
    if not fn.endswith('.owl'):
        continue
    p = os.path.join(ONT, fn)
    try:
        root = ET.parse(p).getroot()
    except Exception:
        continue
    for el in root.iter(OWL + 'Class'):
        about = el.get(RDF + 'about')
        if not about:
            continue
        f = frag(about)
        if not f.startswith('Panju_'):
            continue
        subs = []
        eq = None
        for ch in el:
            if ch.tag == RDFS + 'subClassOf':
                r = ch.get(RDF + 'resource')
                if r:
                    subs.append(frag(r))
            elif ch.tag == OWL + 'equivalentClass':
                eq = node(ch)
        lab = ''
        for ch in el:
            if ch.tag == RDFS + 'label':
                lab = (ch.text or '').strip()
        print('%-10s ⊑ %-28s %s' % (f, ','.join(subs), lab))
        if eq:
            print('           ≡ %s' % eq)
