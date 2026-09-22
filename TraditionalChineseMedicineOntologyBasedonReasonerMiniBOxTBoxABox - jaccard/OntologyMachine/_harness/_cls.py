# -*- coding: utf-8 -*-
"""打印指定类的 subClassOf / equivalentClass 目标（八纲/病性/六经/父类）。"""
import os, sys, io
import xml.etree.ElementTree as ET

ONT = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
NS = {
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'owl': 'http://www.w3.org/2002/07/owl#',
    'xml': 'http://www.w3.org/XML/1998/namespace',
}
RDF = NS['rdf']
RDFS = NS['rdfs']
OWL = NS['owl']

BAGANG = {'Biao', 'Li', 'Banbiaobanli', 'Han', 'Re', 'Xu', 'Shi', 'Yin', 'Yang'}
LIUJING = {'Taiyangbing', 'Yangmingbing', 'Shaoyangbing', 'Taiyinbing', 'Shaoyinbing', 'Jueyinbing'}

targets = sys.argv[1:]

# 收集所有 owl:Class 定义
cls = {}   # frag -> {'sub':[...], 'eq':[...], 'file':..., 'label':...}
for fn in sorted(os.listdir(ONT)):
    if not fn.endswith('.owl'):
        continue
    p = os.path.join(ONT, fn)
    try:
        tree = ET.parse(p)
    except Exception as e:
        print('!! parse fail', fn, e)
        continue
    root = tree.getroot()
    for el in root.iter('{%s}Class' % OWL):
        about = el.get('{%s}about' % RDF)
        if not about or '#' not in about:
            continue
        frag = about.split('#')[-1]
        subs, eqs = [], []
        for ch in el:
            if ch.tag == '{%s}subClassOf' % RDFS:
                r = ch.get('{%s}resource' % RDF)
                if r:
                    subs.append(r.split('#')[-1])
                else:
                    eqs.append('(anon-sub)')
            elif ch.tag == '{%s}equivalentClass' % OWL:
                r = ch.get('{%s}resource' % RDF)
                if r:
                    eqs.append(r.split('#')[-1])
                else:
                    # 匿名：收集其中所有 rdf:resource
                    mem = [x.get('{%s}resource' % RDF) for x in ch.iter() if x.get('{%s}resource' % RDF)]
                    eqs.append('anon:' + ','.join(sorted(set(m.split('#')[-1] for m in mem))))
        lab = ''
        for ch in el:
            if ch.tag == '{%s}label' % RDFS:
                lab = (ch.text or '').strip()
                break
        cur = cls.setdefault(frag, {'sub': [], 'eq': [], 'file': fn, 'label': ''})
        cur['sub'] += subs
        cur['eq'] += eqs
        if lab:
            cur['label'] = lab

for t in targets:
    d = cls.get(t)
    if not d:
        print('%-22s NOT FOUND' % t)
        continue
    bg = [s for s in d['sub'] if s in BAGANG]
    lj = [s for s in d['sub'] if s in LIUJING]
    other = [s for s in d['sub'] if s not in BAGANG and s not in LIUJING]
    print('%-22s [%s] %s' % (t, d['label'], d['file']))
    print('     八纲=%s 六经=%s 其它父=%s' % (bg, lj, other))
    if d['eq']:
        print('     ≡ %s' % d['eq'])
